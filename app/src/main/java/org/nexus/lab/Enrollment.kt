package org.nexus.lab

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Signature
import java.security.spec.ECGenParameterSpec

/** Staging transport only, via explicit adb reverse to a loopback Rust service.
 * Attestation security level and app identity are verified on the service, never inferred here.
 */
object Enrollment {
    class DeviceSession(val handle: Long, private val alias: String, private val enrollment: JSONObject, val status: String) {
        private var pendingInput: ByteArray? = null
        private var pendingWire: String? = null
        private fun keyStore() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        private fun registeredKey(ks: KeyStore): java.security.PrivateKey {
            check(ks.containsAlias(alias)) { "Enrollment key missing; explicitly enroll again" }
            val certificate = ks.getCertificate(alias) ?: error("Enrollment key invalidated; explicitly enroll again")
            val hash = java.security.MessageDigest.getInstance("SHA-256").digest(certificate.publicKey.encoded)
                .joinToString("") { "%02x".format(it.toInt() and 255) }
            check(hash == enrollment.getString("key_sha256")) { "Enrollment key changed; explicitly enroll again" }
            return ks.getKey(alias, null) as? java.security.PrivateKey ?: error("Enrollment private key unavailable")
        }
        fun createCapture(): Long {
            registeredKey(keyStore())
            val challenge = post("/v1/capture-challenge", JSONObject().put("enrollment_id", enrollment.getString("enrollment_id")))
            // Core verifies this signed context before the capture lease or any frame exists.
            return Native.createBound(handle, challenge.getJSONObject("challenge").toString())
        }
        fun submit(capture: Long, result: Long): JSONObject {
            val key = registeredKey(keyStore()) // No replacement or cached-signature bypass after key loss.
            val input = Native.receiptExport(capture, result)
            if (pendingInput?.contentEquals(input) != true) {
                val signer = Signature.getInstance("SHA256withECDSA")
                signer.initSign(key); signer.update(input)
                pendingWire = Native.receiptWire(input, signer.sign())
                pendingInput = input.copyOf()
            }
            // Cancellation, snapshot change or expiry during the hardware signature must stop submission.
            check(Native.receiptExport(capture, result).contentEquals(input)) { "Receipt no longer current" }
            return post("/v1/capture-receipt", JSONObject().put("jws", pendingWire))
        }
        internal fun deleteKey() { keyStore().deleteEntry(alias) }
        fun close() {
            pendingInput = null; pendingWire = null
            // SDK handles are released independently; a Keystore cleanup error grants no new session.
            runCatching { deleteKey() }
        }
    }
    private fun post(path: String, value: JSONObject): JSONObject {
        check(BuildConfig.NEXUS_STAGING) { "Staging is disabled in this build" }
        val c = URL("http://127.0.0.1:8787$path").openConnection() as HttpURLConnection
        c.connectTimeout = 5000; c.readTimeout = 10000; c.requestMethod = "POST"; c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json")
        try {
            val body = value.toString().toByteArray(Charsets.UTF_8)
            c.setFixedLengthStreamingMode(body.size)
            c.outputStream.use { it.write(body) }
            val code = c.responseCode
            val stream = if (code == 200) c.inputStream else c.errorStream
            val raw = stream?.use { input ->
                val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
                while (out.size() <= 256 * 1024) { val n = input.read(buffer); if (n < 0) break; out.write(buffer, 0, n) }
                out.toByteArray()
            } ?: byteArrayOf()
            check(raw.size <= 256 * 1024) { "Response exceeds bound" }
            val response = JSONObject(String(raw, Charsets.UTF_8))
            check(code == 200) { "Enrollment refused: ${response.optString("error", "service unavailable")}" }
            return response
        } finally { c.disconnect() }
    }
    fun open(): DeviceSession {
        val alias = "nexus.staging.enrollment.v1." + java.util.UUID.randomUUID().toString()
        check(BuildConfig.NEXUS_STAGING)
        val begin = JSONObject(Native.stageBegin())
        val session = begin.getLong("session_handle"); val bootstrap = begin.getLong("bootstrap_handle")
        try {
            val challenge = post("/v1/challenge", JSONObject().put("scope", "nexus-core")
                .put("bootstrap", begin.getJSONObject("bootstrap")).put("session", begin.getJSONObject("session")))
            // Each session owns a new alias. Never replace another enrollment key silently.
            val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
            generator.initialize(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setAttestationChallenge(Base64.decode(challenge.getString("attestation_challenge"), Base64.NO_WRAP)).build())
            generator.generateKeyPair()
            val chain = ks.getCertificateChain(alias) ?: error("Attestation certificate chain unavailable")
            check(chain.size in 2..8) { "Attestation chain does not meet staging policy" }
            val signer = Signature.getInstance("SHA256withECDSA")
            signer.initSign(ks.getKey(alias, null) as java.security.PrivateKey)
            signer.update(Base64.decode(challenge.getString("pop_context"), Base64.NO_WRAP))
            val request = JSONObject().put("challenge_id", challenge.getString("id"))
                .put("chain", JSONArray(chain.map { Base64.encodeToString(it.encoded, Base64.NO_WRAP) }))
                .put("proof", Base64.encodeToString(signer.sign(), Base64.NO_WRAP))
            val bundle = post("/v1/enroll", request)
            val accepted = JSONObject(Native.stageComplete(session, bootstrap, bundle.toString()))
            check(accepted.getInt("sdk_status") == 0) { "Core rejected provisioning" }
            return DeviceSession(session, alias, accepted.getJSONObject("enrollment"), "STAGING enrollment=${accepted.getJSONObject("enrollment").getString("enrollment_id")}\n" +
                "Validated key level=${accepted.getJSONObject("enrollment").getString("security_level")}\n" +
                "Provisioning=${accepted.getInt("provisioning_status")} continuity=${accepted.getInt("continuity_status")}\n" +
                "Trust/platform: issuer-authenticated staging app/key context; other device signals UNKNOWN.\n" +
                "No camera provenance, liveness, or production eligibility.")
        } catch (e: Throwable) { Native.stageAbort(session, bootstrap);
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias); throw e }
    }
}
