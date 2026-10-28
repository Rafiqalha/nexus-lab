# Android MVP-1 implementation handoff

Status: PROPOSED IMPLEMENTATION SPECIFICATION; runtime NOT_RUN.
Action target: `lab.protected-record.v1`.
SDK specification commit: `3174ffa`.
Companion backend specification: `nexus-enrollment/docs/security-platform/MVP_HANDOFF.md`.

## 1. Responsibilities

- Warm up Google Play Integrity Standard provider asynchronously before action challenge.
- Execute bounded observation coordinator: max 4 checks, max 2 concurrent, 500ms/check, 1500ms max coordinator.
- Construct synthetic action payload: `message` (1..64 ASCII id), `sequence` (0..2147483647).
- Submit challenge request to `/v1/lab-security/challenges`.
- Acquire provider token with `requestHash = binding_sha256`.
- Sign complete-proof message with enrolled Keystore P-256 key; submit to `/v1/lab-security/complete`.
- If allowed grant returned: sign commit-proof message with same enrolled key; submit to `/v1/lab-security/commit`.
- Handle RETRY, backoff, and terminal failure without faking clean results or local bypass.

## 2. Wire requests (conforming to mvp-request.schema.json)

### A. Challenge request
```json
{
  "schema": 1,
  "kind": "nexus.lab.challenge-request.v1",
  "request_id": "<64-hex client CSPRNG>",
  "enrollment_id": "<64-hex active enrollment id>",
  "payload": {
    "message": "synthetic-01",
    "sequence": 1
  },
  "report": {
    "schema": 1,
    "collector_version": "lab-android-v1",
    "checks": [
      {
        "id": "android.debugger",
        "execution": "completed",
        "finding": "no_known_indicator",
        "duration_ms": 2,
        "age_ms": 10
      }
    ]
  }
}
```

### B. Complete request
```json
{
  "schema": 1,
  "kind": "nexus.lab.complete-request.v1",
  "challenge_id": "<64-hex from challenge response>",
  "provider_token": "<opaque provider token string>",
  "proof": "<128-hex P-256 r||s signature over complete-proof message>"
}
```
Complete-proof signing message:
`nexus-lab-complete-v1\0` || C({binding_sha256, provider_token_sha256})

### C. Commit request
```json
{
  "schema": 1,
  "kind": "nexus.lab.commit-request.v1",
  "challenge_id": "<64-hex>",
  "grant_id": "<64-hex from decision>",
  "commit_id": "<64-hex client CSPRNG>",
  "payload": {
    "message": "synthetic-01",
    "sequence": 1
  },
  "proof": "<128-hex P-256 r||s signature over commit-proof message>"
}
```
Commit-proof signing message:
`nexus-lab-commit-v1\0` || C({binding_sha256, grant_id, commit_id, payload_sha256})

## 3. Cryptography and key handling

- Use AndroidKeyStore alias corresponding to the active `enrollment_id`.
- Algorithm: `SHA256withECDSA` on P-256 curve.
- Normalize signature: convert ASN.1 DER to fixed 64-byte IEEE-P1363 `r || s` (each 32 bytes zero-padded big-endian).
- Ensure low-S normalization: if S > curve order / 2, replace S with order - S before hex encoding.
- Hex encode as exactly 128 lowercase hex characters.
- If Keystore entry is invalidated (e.g., biometric / lockscreen enrollment changed), catch `KeyPermanentlyInvalidatedException` and report `identity_inactive`. Do not generate a replacement key silently.

## 4. Provider warm-up and acquisition

- Call `StandardIntegrityManager.prepareIntegrityToken(PrepareIntegrityTokenRequest.builder().setCloudProjectNumber(cloudProjectNumber).build())` on cold startup or background.
- Preparation timeout: 60,000 ms.
- Re-preparation limit: max 3 per minute; exponential backoff on failure.
- When action is triggered: call `StandardIntegrityTokenProvider.request(StandardIntegrityTokenRequest.builder().setRequestHash(binding_sha256).build())`.
- If token provider returns `INTEGRITY_TOKEN_PROVIDER_INVALID`, re-warm once and re-request.
- If provider request fails, do not invent local bypass; report error to coordinator.

## 5. Local observation collectors

Coordinator coordinates up to 4 checks in parallel (max 2 concurrent coroutine workers):
1. `android.app_identity`: reads package name, version code, signer SHA256.
2. `android.debugger`: checks `Debug.isDebuggerConnected()`, reads `/proc/self/status` TracerPid (if accessible).
3. `android.environment`: checks Build props (`FINGERPRINT`, `HARDWARE`, `PRODUCT`), checks presence of QEMU/vbox pipe device nodes (read-only, non-blocking).
4. `native.process_indicators`: bounded scan of `/proc/self/maps` (max 256 KiB, max 256 lines parsed) looking for known injection keywords (`frida-agent`, `libgadget`).

Safety rules:
- Any check exceeding 500 ms is cancelled and returns `execution: "timeout"`, `finding: "unknown"`.
- If permission denied: returns `execution: "permission_denied"`, `finding: "unknown"`.
- If no keyword found: returns `finding: "no_known_indicator"`. Never claim universal absence of instrumentation.
- Total coordinator timeout: 1500 ms.
- Cancellation: if Activity is destroyed or user cancels, active collectors are cancelled immediately via CoroutineScope.

## 6. Test suite requirements (lab acceptance)

Implement automated tests covering:
- Conformance with canonical JSON and exact vector digests from SDK `binding-vectors.json`.
- DER to IEEE-P1363 low-S normalization roundtrip.
- Challenge rejection handling: 400, 401, 403, 409, 410, 429, 503.
- Retry budget: max 2 whole-flow retries; backoff delay respected.
- Re-signed / tampered APK detection.
- Physical device execution vs emulator execution distinction.
