package org.nexus.lab

import android.content.Context
import android.os.Build
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.security.MessageDigest

object Native {
    @JvmStatic external fun open(): Long
    @JvmStatic external fun create(session: Long): Long
    @JvmStatic external fun submit(capture: Long, pixels: ByteBuffer, width: Int, height: Int, rotation: Int, timestamp: Long): String
    @JvmStatic external fun progress(capture: Long): String
    @JvmStatic external fun finish(capture: Long): Long
    @JvmStatic external fun read(capture: Long, result: Long): String
    @JvmStatic external fun cancel(capture: Long): Int
    @JvmStatic external fun release(session: Long, capture: Long, result: Long): Int
    @JvmStatic external fun capabilities(session: Long): String
    @JvmStatic external fun stageBegin(): String
    @JvmStatic external fun stageComplete(session: Long, challenge: Long, bundle: String): String
    @JvmStatic external fun stageAbort(session: Long, challenge: Long): Int
    @JvmStatic external fun createBound(session: Long, document: String): Long
    @JvmStatic external fun receiptExport(capture: Long, result: Long): ByteArray
    @JvmStatic external fun receiptWire(input: ByteArray, der: ByteArray): String
    private var identity: String? = null
    @Synchronized fun load(context: Context): String {
        identity?.let { return it }
        val record = JSONObject(context.assets.open("sdk-identity.json").bufferedReader().use { it.readText() })
        check(record.getString("profile") == if (BuildConfig.NEXUS_STAGING) "staging-enrollment" else "default-preview")
        check(Build.SUPPORTED_ABIS.contains("arm64-v8a")) { "ARM64 device required" }
        val directory = File(context.applicationInfo.nativeLibraryDir)
        val sdk = File(directory, "libnexus_sdk.so")
        val bridge = File(directory, "libnexus_lab.so")
        fun sha(f: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            f.inputStream().use { stream -> val bytes = ByteArray(65536); while (true) { val n = stream.read(bytes); if (n < 0) break; digest.update(bytes, 0, n) } }
            return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
        }
        val loaded = sha(sdk)
        check(loaded == record.getString("sdk_sha256")) { "SDK library hash mismatch" }
        check(sha(bridge) == record.getString("bridge_sha256")) { "JNI library hash mismatch" }
        System.load(sdk.absolutePath)
        System.load(bridge.absolutePath)
        record.put("loaded_sdk_sha256", loaded).put("model", Build.MODEL).put("android", Build.VERSION.RELEASE).put("device_abis", Build.SUPPORTED_ABIS.joinToString())
        return record.toString(2).also { identity = it }
    }
}
