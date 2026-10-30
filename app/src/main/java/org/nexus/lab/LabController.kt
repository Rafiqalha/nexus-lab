package org.nexus.lab

import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.camera.core.ImageProxy
import org.json.JSONObject
import org.nexus.lab.security.CollectorCoordinator
import org.nexus.lab.security.ObservationCancellation
import org.nexus.lab.security.PlatformCollectors
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicBoolean

/** One worker owns all handles/buffers. Only cancellation crosses the worker boundary.
 * Epoch guards UI publication and start/stop races. Cleanup is queued behind in-flight JNI.
 */
class LabController(
    private val context: android.content.Context,
    private val worker: ExecutorService,
    private val publish: (String) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private val epoch = AtomicLong()
    private val cancellable = AtomicLong()
    private val pollQueued = AtomicBoolean()
    private var session = 0L
    private var capture = 0L
    private var result = 0L
    private var enrollmentStatus = "PREVIEW: no enrollment or platform evidence"
    @Volatile private var observationCancellation: ObservationCancellation? = null
    private var observationStatus = "Local observations: NOT_COLLECTED (preview mode)"
    private var enrolled: Enrollment.DeviceSession? = null
    private var submissionAttempt = 0
    private var submissionStatus = "Server acceptance: not submitted"
    private var running = false
    private var token = 0L
    private var key: List<Int>? = null
    private var pixels: ByteBuffer? = null
    @Volatile internal var observedRotation: Int? = null
        private set
    @Volatile internal var sdkInFlight = false
        private set
    private var lastFrame = "No submitted camera frame"
    private var lastTimestamp = 0L
    private var lastUi = 0L
    private var delivered = 0L
    private var submitted = 0L
    private var skipped = 0L
    private var errors = 0L
    private var adapter = Timings()
    private var sdk = Timings()
    private var analyzer = Timings()
    private fun emit(t: Long, message: String) { main.post { if (epoch.get() == t) publish(message) } }
    fun start(secured: Boolean = false, enrollNew: Boolean = false) {
        val t = epoch.incrementAndGet()
        val previous = cancellable.getAndSet(0)
        if (previous != 0L) Native.cancel(previous)
        observationCancellation?.cancel()
        worker.execute {
            cleanup()
            if (epoch.get() != t) return@execute
            token = t
            try {
                if (secured) {
                    val opened = Enrollment.open(enrollNew); enrolled = opened; session = opened.handle; enrollmentStatus = opened.status
                    val cancellation = ObservationCancellation()
                    observationCancellation = cancellation
                    val report = CollectorCoordinator(PlatformCollectors.supported(context)).collect(cancellation)
                    if (epoch.get() != t || cancellation.isCancelled()) { cleanup(); return@execute }
                    observationStatus = "Local observations (client_reported; not authorization): ${report.summary()}"
                } else {
                    session = Native.open()
                    enrollmentStatus = "PREVIEW: no enrollment or platform evidence"
                    observationStatus = "Local observations: NOT_COLLECTED (preview mode)"
                }
                submissionAttempt = 0
                submissionStatus = "Server acceptance: not submitted"
                capture = enrolled?.createCapture() ?: Native.create(session)
                cancellable.set(capture)
                if (epoch.get() != t) { Native.cancel(capture); cleanup(); return@execute }
                key = null; lastTimestamp = 0; delivered = 0; submitted = 0; skipped = 0; errors = 0
                adapter = Timings(); sdk = Timings(); analyzer = Timings(); lastFrame = "No submitted camera frame"; running = true
                emit(t, "Capturing\n$enrollmentStatus\n${Native.capabilities(session)}")
            } catch (e: Exception) { cleanup(); emit(t, "Start failed: ${e.message}") }
        }
    }
    fun resetEnrollment() {
        stop("Resetting local enrollment")
        val t = epoch.get()
        worker.execute { try { Enrollment.reset(); emit(t,"Local enrollment deleted; server revocation was not requested. Explicit enrollment required") }
            catch (e: Exception) { emit(t,"Enrollment reset failed: ${e.message}") } }
    }
    fun stop(reason: String) {
        val t = epoch.incrementAndGet()
        val active = cancellable.getAndSet(0)
        if (active != 0L) Native.cancel(active)
        observationCancellation?.cancel()
        emit(t, "$reason\nPrevious result is no longer current. Tap Start for a new capture.")
        worker.execute { cleanup() }
    }
    fun close() {
        stop("Activity destroyed")
        worker.execute { pixels?.let { FrameAdapter.wipe(it) }; pixels = null }
    }
    private fun cleanup() {
        running = false
        cancellable.compareAndSet(capture, 0)
        if (session != 0L || capture != 0L || result != 0L) Native.release(session, capture, result)
        enrolled?.close(); enrolled = null
        observationCancellation = null
        session = 0; capture = 0; result = 0
        pixels?.let { FrameAdapter.wipe(it) }; key = null
    }
    private fun report(snapshot: JSONObject): String = "$enrollmentStatus\n$observationStatus\n$submissionStatus\n" +
        "Enrollment metadata is historical; current_result_valid controls current result use.\n" + snapshot.toString(2) +
        "\nAdapter pack: ${adapter.summary()}\nSDK/JNI submit: ${sdk.summary()}\nAnalyzer incl wipe/close: ${analyzer.summary()}\n" +
        "$lastFrame\ndelivered=$delivered submitted=$submitted appSkipped=$skipped errors=$errors\nCameraX dropped frames: UNKNOWN (not exposed by KEEP_ONLY_LATEST)"
    private fun acceptSnapshot(snapshot: JSONObject) {
        if (snapshot.getInt("sdk_status") != 0) throw IllegalStateException("SDK error ${snapshot.getInt("sdk_status")}")
        val state = snapshot.getInt("state")
        if ((state == 2 || state == 6) && result == 0L) {
            result = Native.finish(capture)
            running = false
            if (enrolled != null) submitOwned()
            emit(token, report(JSONObject(Native.read(capture, result))))
        } else if (state == 4 || state == 5) {
            running = false
            emit(token, report(snapshot))
        } else if (SystemClock.elapsedRealtime() - lastUi >= 100) {
            lastUi = SystemClock.elapsedRealtime()
            emit(token, report(snapshot))
        }
    }
    fun poll() {
        if (!pollQueued.compareAndSet(false, true)) return
        worker.execute {
            try {
                if (epoch.get() == token && capture != 0L) {
                    if (result != 0L) emit(token, report(JSONObject(Native.read(capture, result))))
                    else if (running) acceptSnapshot(JSONObject(Native.progress(capture)))
                }
            } catch (e: Exception) { running = false; emit(token, "Progress failed: ${e.message}") }
            finally { pollQueued.set(false) }
        }
    }
    private fun submitOwned() {
        submissionAttempt++
        try {
            val receipt = enrolled?.submit(capture, result) ?: error("Secured enrollment required")
            check(!receipt.optBoolean("product_eligible", true)) { "Unexpected product authorization" }
            check(receipt.getBoolean("authenticated_receipt_accepted")) { "Receipt not accepted" }
            submissionStatus = "Server acceptance (staging) attempt=$submissionAttempt: authenticated_receipt_accepted=true; " +
                "acceptance_id=${receipt.getString("acceptance_id")}; payload_digest=${receipt.getString("payload_digest")}; " +
                "capture_claims=client_reported; independent validation: registered signature, challenge, online revocation, reported shape. " +
                "product_eligible=false; no verified camera provenance or manipulation verdict."
        } catch (e: Exception) { submissionStatus = "Receipt submission failed attempt=$submissionAttempt: ${e.message}; no trusted fallback" }
    }
    fun submitReceipt() { worker.execute {
        if (epoch.get() == token && result != 0L && enrolled != null) {
            submitOwned(); emit(token, report(JSONObject(Native.read(capture, result))))
        }
    } }
    internal fun loseEnrollmentKey() { worker.execute { enrolled?.deleteKey() } }
    fun finish() {
        worker.execute {
            if (epoch.get() != token || capture == 0L) return@execute
            try {
                if (result == 0L) result = Native.finish(capture)
                running = false
                emit(token, report(JSONObject(Native.read(capture, result))))
            } catch (e: Exception) { running = false; emit(token, "Finalize refused: ${e.message}") }
        }
    }
    fun validate() {
        worker.execute {
            if (epoch.get() == token && result != 0L) emit(token, report(JSONObject(Native.read(capture, result))))
        }
    }
    fun analyze(image: ImageProxy) {
        val start = SystemClock.elapsedRealtimeNanos()
        var measured = false
        var snapshot: JSONObject? = null
        try {
            observedRotation = image.imageInfo.rotationDegrees
            if (!running || epoch.get() != token) return
            delivered++
            val crop = image.cropRect
            val rotation = image.imageInfo.rotationDegrees
            val context = listOf(image.width, image.height, crop.left, crop.top, crop.right, crop.bottom, rotation)
            if (key != null && key != context) { stop("Orientation/crop changed; capture invalidated"); return }
            require(image.format == PixelFormat.RGBA_8888 && image.planes.size == 1) { "Expected CameraX single RGBA plane" }
            require(rotation in listOf(0, 90, 180, 270))
            val timestamp = image.imageInfo.timestamp
            if (timestamp <= lastTimestamp || timestamp <= 0) { skipped++; return }
            val bytes = crop.width().toLong() * crop.height() * 4
            require(bytes in 36..FrameAdapter.MAX_BYTES.toLong())
            if ((pixels?.capacity() ?: 0) < bytes.toInt()) {
                pixels?.let { FrameAdapter.wipe(it) }
                pixels = ByteBuffer.allocateDirect(bytes.toInt())
            }
            val plane = image.planes[0]
            val a = SystemClock.elapsedRealtimeNanos()
            val packed = FrameAdapter.packRgba(plane.buffer, image.width, image.height, plane.rowStride, plane.pixelStride,
                FrameAdapter.Crop(crop.left, crop.top, crop.right, crop.bottom), pixels!!)
            val b = SystemClock.elapsedRealtimeNanos()
            sdkInFlight = true
            val raw = try { Native.submit(capture, packed, crop.width(), crop.height(), rotation, timestamp) }
                finally { sdkInFlight = false }
            val c = SystemClock.elapsedRealtimeNanos()
            adapter.add(b - a); sdk.add(c - b); measured = true
            lastTimestamp = timestamp; key = context; submitted++
            lastFrame = "Camera source=${image.width}x${image.height} crop=$crop submitted=${crop.width()}x${crop.height()} rotation=$rotation mirrored=false RGBA full-range RGB rowStride=${plane.rowStride} pixelStride=${plane.pixelStride} bufferPosition=${plane.buffer.position()} bufferLimit=${plane.buffer.limit()} bufferCapacity=${plane.buffer.capacity()}"
            snapshot = JSONObject(raw).put("source_width", image.width).put("source_height", image.height)
                .put("crop_source", "$crop").put("rotation", rotation).put("mirrored", false)
                .put("submitted_width", crop.width()).put("submitted_height", crop.height())
        } catch (e: Exception) {
            errors++; running = false
            if (epoch.get() == token) emit(token, "Analyzer failed: ${e.message}; tap Start to retry")
        } finally {
            pixels?.let { FrameAdapter.wipe(it) }
            image.close()
            if (measured) analyzer.add(SystemClock.elapsedRealtimeNanos() - start)
        }
        if (epoch.get() == token) snapshot?.let {
            try { acceptSnapshot(it) } catch (e: Exception) { running = false; emit(token, "SDK stopped: ${e.message}") }
        }
    }
}
