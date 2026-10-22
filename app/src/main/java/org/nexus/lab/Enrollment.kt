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
    private const val ALIAS = "nexus.staging.enrollment.v1"
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
    fun open(): Pair<Long, String> {
        check(BuildConfig.NEXUS_STAGING)
        val begin = JSONObject(Native.stageBegin())
        val session = begin.getLong("session_handle"); val bootstrap = begin.getLong("bootstrap_handle")
        try {
            val challenge = post("/v1/challenge", JSONObject().put("scope", "nexus-core")
                .put("bootstrap", begin.getJSONObject("bootstrap")).put("session", begin.getJSONObject("session")))
            // A fresh attestation challenge requires a newly generated key. Only this lab alias
            // is replaced; no broad Keystore deletion, device ID or private key export.
            val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (ks.containsAlias(ALIAS)) ks.deleteEntry(ALIAS)
            val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
            generator.initialize(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setAttestationChallenge(Base64.decode(challenge.getString("attestation_challenge"), Base64.NO_WRAP)).build())
            generator.generateKeyPair()
            val chain = ks.getCertificateChain(ALIAS) ?: error("Attestation certificate chain unavailable")
            check(chain.size in 2..8) { "Attestation chain does not meet staging policy" }
            val signer = Signature.getInstance("SHA256withECDSA")
            signer.initSign(ks.getKey(ALIAS, null) as java.security.PrivateKey)
            signer.update(Base64.decode(challenge.getString("pop_context"), Base64.NO_WRAP))
            val request = JSONObject().put("challenge_id", challenge.getString("id"))
                .put("chain", JSONArray(chain.map { Base64.encodeToString(it.encoded, Base64.NO_WRAP) }))
                .put("proof", Base64.encodeToString(signer.sign(), Base64.NO_WRAP))
            val bundle = post("/v1/enroll", request)
            val accepted = JSONObject(Native.stageComplete(session, bootstrap, bundle.toString()))
            check(accepted.getInt("sdk_status") == 0) { "Core rejected provisioning" }
            return session to ("STAGING enrollment=${accepted.getJSONObject("enrollment").getString("enrollment_id")}\n" +
                "Validated key level=${accepted.getJSONObject("enrollment").getString("security_level")}\n" +
                "Provisioning=${accepted.getInt("provisioning_status")} continuity=${accepted.getInt("continuity_status")}\n" +
                "Trust/platform: issuer-authenticated staging app/key context; other device signals UNKNOWN.\n" +
                "No camera provenance, liveness, or production eligibility.")
        } catch (e: Throwable) { Native.stageAbort(session, bootstrap); throw e }
    }
}
