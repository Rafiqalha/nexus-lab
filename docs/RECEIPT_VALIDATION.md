# Device-bound receipt staging — 2026-10-24

Baseline lab 700128e; SDK 1c9458391fe9b9e6fb265f2cb9bc99fe9345d950, ABI 2.11 / 60 exports.
SDK hosted run [40051466147](https://github.com/Rafiqalha/nexus-sdk/actions/runs/40051466147): all six jobs SUCCESS on that exact SHA.
Service is committed locally as 0f01b218e593df243cb3b00aa66fdc4ad9d4bc7f; it has no remote.
Historical reports remain unchanged. Exact source/log/artifact hashes are in RECEIPT_VALIDATION.json.

The lab requests the signed server challenge before capture. Rust core binds and validates it, exports
canonical signing input only for a live Complete result, then Android signs those exact bytes with the
same enrollment-owned Keystore alias. Rust converts canonical DER to raw ES256. The lab revalidates
before submission; Kotlin cannot supply replacement metrics to the exporter. Retry keeps the same live
payload; missing/changed key requires explicit new enrollment. Cancellation and expiry stop new use.

**Physical PASS: 7/7** on Redmi Note 12 / Xiaomi 23021RAA2Y, Android 15/API 35, ARM64. Genuine enrollment
verified key level trusted_environment (TEE), not StrongBox. CameraX secured capture reached Complete,
server accepted receipt, exact retry returned the same acceptance, deleting the owned key stopped
submission, and signed expiry invalidated further use. Preview/rotation/lifecycle/cleanup regressions
also passed. No camera images or raw frames were retained.

Durable acceptance `8c8f864fa02ae46c06418a1adac4af4cd50f79d2894994fd8d2c445cceec925c`,
payload SHA256 `52bd9c11f56d9e0695dac34d60d3ac88c8234924b23c933c195d98572d9bed8c`.
Exactly one acceptance row for the challenge; registered key digest matches signed challenge identity.
APK SHA256 `8543b090d7df006649d945d1f170ef54d016ca8ba1ca328491bd3ee7b4675a29`.
APK and installed SDK/JNI library hashes matched package identity; signing and 16KiB alignment PASS.

Physical service was policy-final before the last revision-floor guard. That final fail-closed guard
passed service debug/release and ephemeral-root public Rust/loaded C ABI acceptance tests. The phone
later disconnected, so no latest-guard physical rerun is claimed. This distinction does not change the
recorded device result or APK identity.

Local default packaged JNI/JVM smoke, Rust Clippy/tests against final staged package, Android release
build/JVM tests/lint, APK signature/alignment PASS. Synthetic-chain public Rust/actual shared-library
server campaigns are separate service evidence; they do not prove hardware enrollment.

The lab workflow now builds/tests/lints without hardware against pinned SDK 1c94583. A dedicated
read-only SDK deploy key is configured as NEXUS_SDK_READ_KEY; it is unrelated to issuer seeds. At this
report's creation lab hosted CI is NOT_RUN; the exact pushed SHA is checked separately. CI default
preview does not claim operational staging or physical-camera coverage.

Production remains BLOCKED, product_eligible=false. Server authenticates key/message, exact challenge,
online revocation and reported shape. Metrics remain client_reported; no verified camera provenance,
manipulation verdict, liveness, KYC or transaction authorization. No public deployment was performed.
