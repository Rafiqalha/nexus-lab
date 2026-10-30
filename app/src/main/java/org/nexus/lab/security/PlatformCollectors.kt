package org.nexus.lab.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Debug
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Android-supported, own-process, read-only observation adapters. */
object PlatformCollectors {
    private const val MAX_PROC_BYTES = 256 * 1024
    private const val MAX_MAP_LINES = 256
    private const val MAX_STATUS_BYTES = 4 * 1024

    fun supported(context: Context): Map<String, Collector> = mapOf(
        "android.app_identity" to Collector { appIdentity(context) },
        "android.debugger" to Collector { debugger() },
        "android.environment" to Collector { environment() },
        "native.process_indicators" to Collector { processIndicators() },
    )

    private inline fun observed(id: String, block: () -> Boolean): Observation {
        val started = System.nanoTime()
        return try {
            Observation(
                id = id,
                execution = Observation.Execution.COMPLETED,
                finding = if (block()) Observation.Finding.RISK_OBSERVED else Observation.Finding.NO_KNOWN_INDICATOR,
                durationMs = elapsedMs(started),
            )
        } catch (_: SecurityException) {
            unknown(id, Observation.Execution.PERMISSION_DENIED, started)
        } catch (_: UnsupportedOperationException) {
            unknown(id, Observation.Execution.UNSUPPORTED, started)
        } catch (_: Exception) {
            unknown(id, Observation.Execution.ERROR, started)
        }
    }

    private fun appIdentity(context: Context): Observation = observed("android.app_identity") {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) throw UnsupportedOperationException("signing_info_requires_api_28")
        val info = packageInfo(context)
        val signing = info.signingInfo ?: throw IllegalStateException("signing_info_unavailable")
        val signers = if (signing.hasMultipleSigners()) signing.apkContentsSigners else signing.signingCertificateHistory
        check(signers.isNotEmpty()) { "signer_missing" }
        // Material is deliberately not emitted. Hash computation verifies the object is usable.
        signers.forEach { MessageDigest.getInstance("SHA-256").digest(it.toByteArray()) }
        false
    }

    private fun packageInfo(context: Context) = if (Build.VERSION.SDK_INT >= 33) {
        context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()),
        )
    } else {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
    }

    private fun debugger(): Observation = observed("android.debugger") {
        Debug.isDebuggerConnected() || tracerPid() > 0
    }

    private fun environment(): Observation = observed("android.environment") {
        val properties = listOf(Build.FINGERPRINT, Build.HARDWARE, Build.PRODUCT, Build.MODEL)
            .joinToString(" ")
            .lowercase(Locale.ROOT)
        val emulatorProperty = listOf("generic", "goldfish", "ranchu", "sdk_gphone", "emulator").any(properties::contains)
        val knownPipe = listOf("/dev/qemu_pipe", "/dev/socket/qemud", "/dev/vboxguest").any { File(it).exists() }
        emulatorProperty || knownPipe
    }

    private fun processIndicators(): Observation = observed("native.process_indicators") {
        var found = false
        boundedLines(File("/proc/self/maps"), MAX_PROC_BYTES, MAX_MAP_LINES) { line ->
            val lower = line.lowercase(Locale.ROOT)
            if (lower.contains("frida-agent") || lower.contains("libgadget")) found = true
        }
        found
    }

    private fun tracerPid(): Int {
        var result: Int? = null
        boundedLines(File("/proc/self/status"), MAX_STATUS_BYTES, 128) { line ->
            if (line.startsWith("TracerPid:")) result = line.substringAfter(':').trim().toIntOrNull()
        }
        return result ?: throw IllegalStateException("tracer_pid_missing")
    }

    private fun boundedLines(file: File, maxBytes: Int, maxLines: Int, consume: (String) -> Unit) {
        var bytes = 0
        var lines = 0
        FileInputStream(file).use { input ->
            BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8)).use { reader ->
                while (lines < maxLines) {
                    val line = reader.readLine() ?: break
                    bytes += line.toByteArray(StandardCharsets.UTF_8).size + 1
                    check(bytes <= maxBytes) { "proc_input_exceeds_bound" }
                    consume(line)
                    lines++
                }
            }
        }
    }

    private fun unknown(id: String, execution: Observation.Execution, started: Long): Observation = Observation(
        id = id,
        execution = execution,
        finding = Observation.Finding.UNKNOWN,
        durationMs = elapsedMs(started),
    )

    private fun elapsedMs(started: Long): Long = TimeUnit.NANOSECONDS.toMillis((System.nanoTime() - started).coerceAtLeast(0))
}
