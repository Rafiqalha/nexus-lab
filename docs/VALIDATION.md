# External Android lab validation

SDK: `fce252b5a74614b645f4887c8c503d6f2ddf995c`, main, ABI 2.9 / 58 exports.
The SDK working tree is unchanged. Existing [SDK CI 39519059332](https://github.com/Rafiqalha/nexus-sdk/actions/runs/39519059332)
has six successful jobs for that SHA. Lab hosted CI is **NOT_RUN**: no lab remote exists.
The lab is a separate repository at `/home/rafiq/Projects/nexus-android-lab`, branch
`codex/android-integration-lab`. No SDK commit/push or production release was necessary.

The **release APK ran on a physical Xiaomi 23021RAA2Y (Redmi Note 12), Android 15/API35,
arm64-v8a**. CameraX → bounded RGBA adapter → Rust JNI → packaged public C ABI → session →
capture v2 → progress → finalization → current-result validation → cleanup all ran.
It used the default-preview SDK and release Rust JNI, with a local debug signing certificate
for lab installation. The release application itself is non-debuggable.

| Check | Actual result |
|---|---|
| SDK ARM64 source-bound package / 58 exports | PASS |
| Rust format, host and ARM64 Clippy | PASS |
| Host Rust layout/live ABI/ownership | PASS, 1 test |
| Host JVM actual JNI smoke | PASS; includes 80 cancel/release cycles |
| Release Kotlin adapter/unit tests | PASS, 3 tests |
| Release/debug/test APK builds | PASS |
| Debug/release lint | PASS with warnings; no checks disabled |
| Signature, 16 KiB alignment, APK native hashes | PASS |
| Installed SDK/JNI native hashes | PASS, match build bytes |
| Release device bridge + real CameraX | PASS, 5 tests |
| Portrait 90°/270°, background/cancel/restart/close in flight | PASS |
| Camera permission denial on final release APK | PASS; permission restored afterward |
| Production | EXPECTED_DENIAL; preview is not product authorization |

Tests are counted by configuration, not added as unique tests. Lint warnings include pinned
dependency update suggestions, ARM64-only lab, intentional absolute-path loading and UI/metadata.
Source whitespace checks exclude verbatim upstream legal texts whose original bytes are preserved.

Final release capture observations, each **n=9** analyzer samples, milliseconds:

| Rotation | Adapter p50 / p95 | SDK/JNI p50 / p95 | Entire analyzer p50 / p95 |
|---|---|---|---|
| 90° | 0.636 / 3.012 | 15.647 / 16.070 | 18.495 / 26.137 |
| 270° | 0.684 / 0.845 | 16.143 / 18.292 | 19.194 / 21.820 |

Actual source and crop were 640×480, RGBA bytes, row stride 2560, pixel stride 4,
position 0, limit/capacity 1,228,800. Analysis bytes were not mirrored or physically
rotated by CameraX; the SDK applied metadata rotation once (oriented ROI 480×640).
Both captures submitted 9/9 delivered frames, appSkipped=0, errors=0, with five
sampling-accepted and distinct fingerprints, Complete decision and current_result_valid=true.
Both retained secured=0, required_checks_complete=0 and product_eligible=0.

CameraX internal dropped frames remain **UNKNOWN**; its KEEP_ONLY_LATEST API does not expose
an exact count. These small uncontrolled samples include runtime scheduling/warmup effects;
nearest-rank p95 at n=9 is the maximum. They are not sustained throughput, biometric accuracy,
or performance claims for other devices. SDK/JNI includes JSON construction; entire analyzer
includes wipe and ImageProxy close, but excludes finalization, UI and preview rendering.

Earlier installation was blocked by the phone until the user approved USB installs. Some
runner attempts timed out: an earlier device was locked; even after unlock MIUI explicitly
logged `Permission Denied Activity` for the instrumentation launch. The final suite used
`NEXUS_SHELL_LAUNCH=1` to launch the same public Activity intent through ADB when its camera
test started. No OEM permission, keyguard or global setting was changed. The entire final
suite exited 0 with `OK (5 tests)`. Earlier incomplete attempts remain historical, not PASS.

Integration guards enforce direct-buffer bounds/position/limit, bounded copying and wiping,
source-context invalidation and worker-ordered release. No concrete SDK core defect was found.
No frame or screenshot was saved; evidence contains counters, timings, identity and digests.
CameraX/OS buffer erasure and JVM allocation reclamation are outside the lab's guarantees.
No evidence collector, fixture authority, AI/model, issuer/witness or production enrollment was enabled.

[Machine-readable evidence](VALIDATION.json) records source identity, actual hashes, commands,
exit codes, log hashes, complete SDK result snapshots, toolchain and test scope. Logs/APKs
remain local ignored artifacts. The lab remote is unavailable, so no push or lab CI is claimed.
The next priority is to place this separate lab in an owner-provided remote and run its
reproducible build/device campaign there; production needs external enrollment/evidence/evaluation.
