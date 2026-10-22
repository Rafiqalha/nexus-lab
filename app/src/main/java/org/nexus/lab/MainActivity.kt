package org.nexus.lab

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import android.util.Size
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private lateinit var controller: LabController
    private lateinit var status: TextView
    private lateinit var preview: PreviewView
    private var provider: ProcessCameraProvider? = null
    private var analysis: ImageAnalysis? = null
    private var resumed = false
    private var loaded = false
    private var bindingEpoch = 0L
    private val requestPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && resumed) bindCamera() else { controller.stop("Camera permission denied"); status.text = "Camera permission denied; grant it and tap Start." }
    }
    private val poll = object : Runnable { override fun run() { if (resumed) { controller.poll(); main.postDelayed(this, 250) } } }
    private val displays by lazy { getSystemService(DISPLAY_SERVICE) as DisplayManager }
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(id: Int) {}
        override fun onDisplayRemoved(id: Int) {}
        override fun onDisplayChanged(id: Int) { preview.display?.let { if (it.displayId == id) analysis?.targetRotation = it.rotation } }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 16, 24, 16) }
        root.addView(TextView(this).apply { text = "NEXUS · EXTERNAL ANDROID LAB\n" + (if (BuildConfig.NEXUS_STAGING) "ISOLATED STAGING · explicit preview available" else "PREVIEW") + "\nNo production / identity / liveness verdict"; textSize = 17f })
        preview = PreviewView(this).apply { scaleType = PreviewView.ScaleType.FIT_CENTER }
        root.addView(preview, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        val buttons = LinearLayout(this)
        buttons.addView(Button(this).apply { text = "Preview"; setOnClickListener { if (!loaded) return@setOnClickListener; if (hasCameraPermission()) { if (analysis == null) bindCamera(); controller.start() } else requestPermission.launch(Manifest.permission.CAMERA) } })
        buttons.addView(Button(this).apply { text = "Cancel"; setOnClickListener { controller.stop("Cancelled") } })
        buttons.addView(Button(this).apply { text = "Finish"; setOnClickListener { controller.finish() } })
        buttons.addView(Button(this).apply { text = "Check"; setOnClickListener { controller.validate() } })
        root.addView(buttons)
        if (BuildConfig.NEXUS_STAGING) root.addView(Button(this).apply {
            text = "Enroll new key + secured STAGING capture"
            setOnClickListener { if (loaded && hasCameraPermission()) { if (analysis == null) bindCamera(); controller.start(true) } }
        })
        status = TextView(this).apply { text = "Loading verified SDK package…"; textSize = 12f; setTextIsSelectable(true) }
        root.addView(ScrollView(this).apply { addView(status) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        controller = LabController(worker) { status.text = it }
        worker.execute {
            try { val id = Native.load(this); runOnUiThread { loaded = true; status.text = "SDK loaded; tap Start.\n$id" } }
            catch (e: Throwable) { runOnUiThread { status.text = "Library load failed: ${e.message}" } }
        }
    }
    internal fun labObservedRotation() = controller.observedRotation
    internal fun labStatus() = status.text.toString()
    internal fun labSdkInFlight() = controller.sdkInFlight
    internal fun labStart() = controller.start()
    internal fun labStartSecured() = controller.start(true)
    internal fun labFinish() = controller.finish()
    internal fun labCancel() = controller.stop("Cancelled")
    private fun hasCameraPermission() = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    private fun bindCamera() {
        val epoch = ++bindingEpoch
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            if (!resumed || epoch != bindingEpoch) return@addListener
            try {
                provider = future.get()
                provider!!.unbindAll()
                val selector = ResolutionSelector.Builder().setResolutionStrategy(ResolutionStrategy(Size(640, 480), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER)).build()
                val view = Preview.Builder().setResolutionSelector(selector).build()
                view.surfaceProvider = preview.surfaceProvider
                val frames = ImageAnalysis.Builder().setResolutionSelector(selector)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .setOutputImageRotationEnabled(false)
                    .setTargetRotation(preview.display?.rotation ?: android.view.Surface.ROTATION_0)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                frames.setAnalyzer(worker) { controller.analyze(it) }
                analysis = frames
                provider!!.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, view, frames)
            } catch (e: Exception) { controller.stop("Camera binding failed: ${e.message}") }
        }, ContextCompat.getMainExecutor(this))
    }
    override fun onStart() {
        super.onStart(); resumed = true
        displays.registerDisplayListener(displayListener, main)
        if (hasCameraPermission()) bindCamera() else requestPermission.launch(Manifest.permission.CAMERA)
        main.post(poll)
    }
    override fun onStop() {
        resumed = false; bindingEpoch++
        displays.unregisterDisplayListener(displayListener)
        main.removeCallbacks(poll)
        analysis?.clearAnalyzer(); analysis = null; provider?.unbindAll()
        controller.stop("Background/close: capture stopped")
        super.onStop()
    }
    override fun onDestroy() {
        // stop queues owner release after any in-flight analyzer. shutdown drains it.
        controller.close(); worker.shutdown()
        super.onDestroy()
    }
}
