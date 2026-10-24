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
    private const val PREFIX = "nexus.staging.persistent.v1."
    private lateinit var record: android.util.AtomicFile
    @Synchronized fun initialize(context: android.content.Context) {
        if (!::record.isInitialized) record = android.util.AtomicFile(java.io.File(context.noBackupFilesDir, "nexus-enrollment-v1.json"))
    }
    private fun owned(alias: String): Boolean = alias.startsWith(PREFIX) && runCatching { java.util.UUID.fromString(alias.removePrefix(PREFIX)).toString() == alias.removePrefix(PREFIX) }.getOrDefault(false)
    private fun metadata(): JSONObject? {
        if (!record.baseFile.exists() && !java.io.File(record.baseFile.path + ".bak").exists()) return null
        return record.openRead().use { input ->
            val buffer=ByteArray(8193); var size=0
            while(size<buffer.size) { val n=input.read(buffer,size,buffer.size-size); if(n<0) break; size+=n }
            check(size<=8192) { "Enrollment metadata exceeds bound; reset explicitly" }
            JSONObject(String(buffer, 0, size, Charsets.UTF_8)).also { check(it.getInt("schema") == 1 && owned(it.getString("alias"))) { "Invalid enrollment metadata; no key selected" } }
        }
    }
    private fun persist(value: JSONObject) {
        val out = record.startWrite()
        try { out.write(value.toString().toByteArray(Charsets.UTF_8)); record.finishWrite(out) }
        catch (e: Throwable) { record.failWrite(out); throw e }
    }
    @Synchronized fun describe(): String = try {
        metadata()?.let { "Enrollment ${it.getString("state")} (local key present=${KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.containsAlias(it.getString("alias"))}): key=${it.optJSONObject("enrollment")?.optString("key_sha256", "pending") ?: "pending"}; " +
            "historically validated level=${it.optJSONObject("enrollment")?.optString("security_level", "unknown") ?: "unknown"}; " +
            "attestation verified_at_ms=${it.optLong("verified_at_ms")}; attested app version=${it.optLong("application_version")}. Fresh authorization required." }
            ?: "Enrollment unavailable; explicitly enroll first."
    } catch (e: Exception) { "Enrollment metadata unavailable: ${e.message}; reset explicitly." }
    @Synchronized fun reset() {
        val m = metadata() ?: return
        val alias = m.getString("alias"); check(owned(alias))
        // Bounded orphan cleanup: exactly the alias recorded before key generation, never enumerate/delete others.
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias)
        record.delete()
    }

    class DeviceSession(val handle: Long, private val alias: String, private val enrollment: JSONObject, private val grant: String, val status: String) {
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
        internal fun checkRegisteredKey() { registeredKey(keyStore()) }
        fun createCapture(): Long {
            registeredKey(keyStore())
            val challenge = post("/v1/capture-challenge", JSONObject().put("enrollment_id", enrollment.getString("enrollment_id")).put("grant_id", grant))
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
            pendingInput?.fill(0); pendingInput = null; pendingWire = null
            // The enrollment key belongs to the device record, never to the SDK session.
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
        } catch (e: java.io.IOException) { throw IllegalStateException("Service unavailable; secured authorization failed", e) } finally { c.disconnect() }
    }
    internal fun requirePoPPurpose(bytes: ByteArray, kind: String) {
        check(kind == "nexus.enrollment-pop.staging.v1" || kind == "nexus.session-authorization-pop.staging.v1")
        val prefix = (kind + "\n").toByteArray(Charsets.UTF_8)
        check(bytes.size in (prefix.size + 2)..8192 && prefix.indices.all { bytes[it] == prefix[it] }) { "Wrong proof purpose; signing refused" }
    }
    private fun pop(challenge: JSONObject, begin: JSONObject, kind: String, enrollment: String? = null): ByteArray {
        val bytes = Base64.decode(challenge.getString("pop_context"), Base64.NO_WRAP)
        requirePoPPurpose(bytes, kind)
        val body = JSONObject(String(bytes, kind.length + 1, bytes.size - kind.length - 1, Charsets.UTF_8))
        val ctx = if (enrollment != null) body.getJSONObject("context") else body
        check(ctx.getString("kind") == kind && ctx.getString("id") == challenge.getString("id") && ctx.getString("scope") == "nexus-core") { "Wrong proof context" }
        for (name in listOf("bootstrap", "session")) {
            val expected = begin.getJSONObject(name); val actual = ctx.getJSONObject(name)
            for (key in expected.keys()) check(actual.get(key).toString() == expected.get(key).toString()) { "Proof $name binding mismatch" }
        }
        if (enrollment != null) check(body.getString("enrollment_id") == enrollment) { "Wrong enrollment proof" }
        return bytes // Sign exact server canonical bytes; Kotlin never reconstructs authoritative bytes.
    }
    private fun sign(ks: KeyStore, alias: String, bytes: ByteArray): ByteArray {
        val signer = Signature.getInstance("SHA256withECDSA")
        signer.initSign(ks.getKey(alias, null) as? java.security.PrivateKey ?: error("Enrollment key missing; reset explicitly"))
        signer.update(bytes); return signer.sign()
    }
    @Synchronized fun open(enrollNew: Boolean = false): DeviceSession {
        check(BuildConfig.NEXUS_STAGING)
        val previous = metadata()
        if (enrollNew) check(previous == null) { "Enrollment already exists or is pending; reset explicitly first" }
        else check(previous?.optString("state") == "active") { "Enrollment unavailable or pending; explicit enrollment/reset required" }
        val begin = JSONObject(Native.stageBegin())
        val session = begin.getLong("session_handle"); val bootstrap = begin.getLong("bootstrap_handle")
        try {
            val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val sdk = JSONObject().put("scope", "nexus-core").put("bootstrap", begin.getJSONObject("bootstrap")).put("session", begin.getJSONObject("session"))
            val alias: String
            val bundle: JSONObject
            if (enrollNew) {
                alias = PREFIX + java.util.UUID.randomUUID().toString()
                // Durable pending ownership precedes generation; process death never silently replaces a key.
                persist(JSONObject().put("schema",1).put("state","pending").put("alias",alias))
                val challenge = post("/v1/challenge", sdk)
                val proofBytes = pop(challenge, begin, "nexus.enrollment-pop.staging.v1")
                val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
                generator.initialize(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                    .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1")).setDigests(KeyProperties.DIGEST_SHA256)
                    .setAttestationChallenge(Base64.decode(challenge.getString("attestation_challenge"), Base64.NO_WRAP)).build())
                generator.generateKeyPair()
                val chain = ks.getCertificateChain(alias) ?: error("Attestation chain unavailable")
                check(chain.size in 2..8)
                bundle = post("/v1/enroll", JSONObject().put("challenge_id",challenge.getString("id"))
                    .put("chain",JSONArray(chain.map { Base64.encodeToString(it.encoded,Base64.NO_WRAP) }))
                    .put("proof",Base64.encodeToString(sign(ks,alias,proofBytes),Base64.NO_WRAP)))
            } else {
                alias = previous!!.getString("alias")
                val enrollment = previous.getJSONObject("enrollment")
                DeviceSession(session,alias,enrollment,"","key check only").checkRegisteredKey()
                val challenge = post("/v1/session-authorization/challenge",JSONObject().put("enrollment_id",enrollment.getString("enrollment_id")).put("sdk",sdk))
                val pop = challenge.getString("pop_context")
                val proofBytes = Enrollment.pop(challenge,begin,"nexus.session-authorization-pop.staging.v1",enrollment.getString("enrollment_id"))
                bundle = post("/v1/session-authorization/complete",JSONObject().put("challenge_id",challenge.getString("id")).put("pop_context",pop)
                    .put("proof",Base64.encodeToString(sign(ks,alias,proofBytes),Base64.NO_WRAP)))
            }
            val accepted = JSONObject(Native.stageComplete(session,bootstrap,bundle.toString()))
            check(accepted.getInt("sdk_status") == 0) { "Core rejected provisioning" }
            val enrollment = accepted.getJSONObject("enrollment")
            if (!enrollNew) check(listOf("enrollment_id","key_sha256","attestation_sha256","application_id","signer_sha256","security_level").all { enrollment.getString(it) == previous!!.getJSONObject("enrollment").getString(it) }) { "Enrollment identity changed; reset explicitly" }
            val opened = DeviceSession(session,alias,enrollment,bundle.getString("grant_id"),"STAGING enrollment=${enrollment.getString("enrollment_id")}\n" +
                "Enrollment key=${enrollment.getString("key_sha256")}\nValidated key level=${enrollment.getString("security_level")} (historical attestation)\n" +
                "Fresh session authorization grant=${bundle.getString("grant_id")} expires_ms=${bundle.getLong("expires_ms")}\n" +
                "Attestation verified_at_ms=${bundle.getLong("attestation_verified_at_ms")} attested app version=${bundle.getLong("attested_application_version")}\n" +
                "Provisioning=${accepted.getInt("provisioning_status")} continuity=${accepted.getInt("continuity_status")}\n" +
                "No fresh boot/app inspection, camera provenance, liveness or production eligibility.")
            opened.checkRegisteredKey()
            if (enrollNew) persist(JSONObject().put("schema",1).put("state","active").put("alias",alias).put("enrollment",enrollment)
                .put("verified_at_ms",bundle.getLong("attestation_verified_at_ms")).put("application_version",bundle.getLong("attested_application_version")))
            return opened
        } catch (e: Throwable) { Native.stageAbort(session,bootstrap); throw e }
    }
}
