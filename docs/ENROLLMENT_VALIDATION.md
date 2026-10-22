# Authenticated device enrollment — isolated staging

PASS on Redmi Note 12 (23021RAA2Y), Android 15/API 35, ARM64. Official Android attestation chain
and fresh Google revocation status were verified by the separate Rust service. Key level accepted:
**trusted_environment (TEE)**, not StrongBox. App org.nexus.lab and exact debug signing certificate
were checked off-device. The SDK package pins an independent staging issuer public root.

The final release APK passed all **7 device tests**. Secured capture returned SDK success,
Complete, required_checks_complete=1 and current_result_valid=true, then the held result became
invalid after the issuer's 30-second lease. Sampling/distinct/span were asserted explicitly.
Product eligibility, camera provenance and manipulation verdicts remain zero. Preview portrait and
reverse portrait, background/cancel/restart/in-flight cleanup, bridge tests and valid zero-frame
Incomplete passed. Tests now wait on SDK Complete, not a CameraX delivered-frame count.

SDK main ad41f5fde927de5eed2c9d7a104bc67fc22b8fb6 supplies ABI 2.10 with the same 58 exports/layouts.
Its exact-SHA hosted run [39580027152](https://github.com/Rafiqalha/nexus-sdk/actions/runs/39580027152)
completed all six jobs successfully. SDK/JNI hashes inside the release APK match both packaged
identity and the files installed on the phone. Release signature and 16KiB zip alignment pass.
The release remains a locally debug-key-signed staging app, not a production distribution.

Local release/debug/test builds, lint, Kotlin JVM tests, default JNI/JVM actual-library smoke,
staging JNI Clippy/tests and default manifest processing passed. SDK default/fixture library probes,
workspace/fixture/diagnostic regressions and default/developer/staging packages were checked in
the SDK. Service regular tests (7; integration separately ignored) and both explicit synthetic-chain
Rust/loaded-C-ABI tests passed, including fresh challenge replay and trust snapshot invalidation.
Configurations are not summed as unique tests. Exact commands/log/source/artifact hashes are in
ENROLLMENT_VALIDATION.json; raw metadata-only logs remain local under evidence/.

Initial failures are retained with explanations: Android network XML lint; omitted bridge metadata;
lost ADB reverse after USB reconnect; stale ABI assertion; premature preview test finalization.
No checks or default-deny rules were disabled. Secured transport failure denied capture without
promoting preview to trusted. The final seven-test run supersedes those attempts for current source.

Service commit 8484dc6844408b715ccb85d3c41e4371829d4e8b is local in ../nexus-enrollment; no service
remote exists. The lab's verified origin is https://github.com/Rafiqalha/nexus-lab.git on main.
See POST_PUSH.md. The original VALIDATION.md/json is unchanged historical baseline evidence.

Production remains BLOCKED: production anchors/enrollment, trusted operational policy, durable
continuity/independent rollback anchor, external platform/sensor evidence and independent product
evaluation are not supplied by this milestone. Offline key revocation is bounded by lease expiry
or authenticated snapshot update, not immediate delivery. Whole-provider database rollback needs
an independent anchor. TEE enrollment does not establish camera origin, liveness or transaction
approval. Keys and SQLite stay outside every repository; no frames, secrets or certificates are
committed, and no public service or production release was deployed.
