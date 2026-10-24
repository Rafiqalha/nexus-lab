# Nexus External Android Integration Lab

A separate CameraX application and thin Rust JNI consumer of **packaged C ABI 2.11**.
It has no `nexus-core` dependency and is outside the SDK Cargo workspace. The default
preview package is accepted; fixture/diagnostic profiles are rejected at load/open. An explicitly
imported staging package enables Android Keystore enrollment through a separate loopback Rust
service. There is no production authority.

One screen: back-camera preview, Start, Cancel, Finish, Check and SDK progress/results.
Complete means the SDK's configured quality/capture contract, never identity validity,
liveness, camera origin, anti-replay or transaction approval. Required-check completeness,
product eligibility and result ownership validity are displayed separately.

## Build

Requirements: JDK 17, Python 3, Rust 1.99.0 + aarch64-linux-android, Android platform 35,
build-tools 35.0.0 and NDK 27.2.12479018; Gradle 8.13 wrapper. Set `ANDROID_HOME` and
optionally `ANDROID_NDK_HOME`, with the NDK clang binaries on PATH. The wrapper and
direct Maven/Cargo dependency versions are pinned; Cargo.lock is committed. Do not put this lab in
nexus-sdk or add it to the SDK Cargo workspace.

```sh
rustup target add aarch64-linux-android
./tools/prepare_sdk.sh ../nexus-sdk dist/android-lab-NEW-UNUSED-NAME
./tools/build_android.sh
```

`prepare_sdk` uses the SDK's source-bound xtask packaging/audit, then imports its exact
library, header, notices and dependency licenses. `SDK_IDENTITY.json` records the source
SHA/digest, target/profile, header/manifest/library hashes. Generated Rust declarations
come solely from that header and fail closed on unknown C syntax. Cargo links
`libnexus_sdk.so`, not SDK Rust crates. SDK package paths and native artifacts are ignored.
Build assets bind the bridge/library hashes; Android hashes the extracted files and
loads those exact absolute paths before opening a session. These hashes identify build
bytes, not signed provenance or execution/hardware attestation.

Release APK: `app/build/outputs/apk/release/app-release.apk`. Debug APK is also built; device instrumentation targets the signed lab release APK itself. **Lab release uses the local Android debug signing key for installation**;
it is not a production release/distribution certificate. Both package a release Rust
bridge and the same release SDK. No signing key is committed. Notices/licenses from
the SDK package and JNI dependencies are included in APK assets, with their original terms.

```sh
export ANDROID_SERIAL=YOUR_AUTHORIZED_DEVICE
./tools/device_tests.sh
adb -s "$ANDROID_SERIAL" install -r app/build/outputs/apk/release/app-release.apk
adb -s "$ANDROID_SERIAL" shell am start -n org.nexus.lab/.MainActivity
```

Grant Camera permission when testing capture. The app requests it normally and shows
refusal without processing when denied. Xiaomi may require Install via USB plus an
on-device installation confirmation. The test script grants CAMERA to this lab only. Keep the phone unlocked; the foreground
lab keeps its own window awake and does not dismiss or bypass the keyguard.
On MIUI, if instrumentation cannot open the Activity because background launch is denied,
run `NEXUS_SHELL_LAUNCH=1 ./tools/device_tests.sh`. This uses the same public launcher
intent through ADB when the camera test starts; it changes no device permission or keyguard setting.
Use a textured, non-sensitive scene for capture; no face is required or recorded.


## Authenticated staging enrollment

Build the separate sibling `nexus-enrollment` service and initialize its private keys/snapshots outside
all repositories. Pin its public issuer root into an isolated SDK package:

```sh
export NEXUS_STAGING_ROOT_PUBLIC_HEX=EXACT_SERVICE_PUBLIC_ROOT
# In nexus-sdk, with Android NDK linker configured:
cargo xtask package --target aarch64-linux-android --output dist/NEW_STAGING_PACKAGE --profile staging
# In this lab:
python3 tools/import_sdk.py ../nexus-sdk/dist/NEW_STAGING_PACKAGE EXACT_SDK_COMMIT --staging
./tools/build_android.sh
adb reverse tcp:8787 tcp:8787
```

The service policy must allow this APK's exact application ID and signing certificate digest.
`Enroll new key + secured STAGING capture` creates a fresh key under the lab's own Keystore alias
with the service challenge, submits its certificate chain and proof of possession, and configures a
secured SDK session only after both service verification and core signature/binding verification.
The bridge displays enrollment metadata from the exact provisioning bytes accepted by core.
Enrollment failure stops secured capture. Preview remains a separate explicit action.

This profile alone requests INTERNET and permits cleartext **localhost only** for ADB reverse;
the default manifest remains camera-only. No issuer seed, private key export, device-wide identifier,
frame recording or public service deployment is part of the app. Lab release is locally debug-key
signed for staging installation. New-key enrollment is explicit; revocation of one key does not
claim to ban an entire physical device.

The UI separates staging enrollment, verified TEE/StrongBox key level, provisioning/continuity,
capture decision, required checks, current-result validity and product eligibility. It keeps checking
a finalized result while held, so the 30-second issuer lease becoming stale is visible. Signed
app/key context does not prove camera origin or liveness; product eligibility stays zero. Offline
revocation is bounded by the lease, not instantaneous. There is no durable issuer/witness continuity.

New validation is in [ENROLLMENT_VALIDATION.md](docs/ENROLLMENT_VALIDATION.md). The original
[VALIDATION.md](docs/VALIDATION.md) remains historical baseline evidence. Completion tests now assert
SDK success, Complete, minimum/distinct sampling, observation span, current validity and security
mode; a zero-frame valid Incomplete has its own test.

## Frame, clock and lifecycle contract

- Pinned CameraX 1.4.2 uses output RGBA_8888. The API contract is increasing bytes
  **R,G,B,A**; the older analysis guide's A,R,G,B example conflicts with the API/source.
  This lab follows the API and tests colored/asymmetric patterns. It does not reinterpret
  Bitmap integer order or pass an unknown-range Y plane as full-range luma.
- CameraX converts to opaque, straight eight-bit RGB. SDK RGBA full-range RGB is
  converted by the core's existing integer BT.601 approximation. This is RGB-derived
  quality luma, not a calibrated raw sensor luminance or proof of sensor range.
- Adapter duplicates the plane buffer, respects position/limit/row stride, requires
  pixel stride 4 and checks the final address. Source-axis crop is explicitly copied to
  a single bounded, worker-owned direct RGBA buffer (<=16 MiB). SDK receives packed
  cropped dimensions and whole ROI. Source crop/size/rotation changes stop the capture;
  a new Start uses a new session/context.
- Output rotation in CameraX is disabled. The core applies imageInfo rotation once.
  Back camera only: analysis bytes are not mirrored, metadata mirrored=false. PreviewView
  applies its own display transform; preview orientation never transforms analysis twice.
- KEEP_ONLY_LATEST; one analyzer executor off the main thread. ImageProxy closes in
  finally. Synchronous JNI enforces position=0 and limit=exact payload on the prepared direct copy,
  while its owner remains
  alive; the core makes its own bounded normalized copy. Lab buffer is wiped afterward
  and on cleanup. One zeroed buffer is reused across captures by the controller and
  released after its worker drains at activity destruction (direct-memory reclamation
  remains JVM-managed). There is no persistence/logging of images; CameraX/OS buffer wiping
  is outside this lab's control.
- Camera timestamp is checked for positive/increasing ordering only. Sampling cadence,
  span, maximum gap and capture deadline use the SDK's internal monotonic clock. Lab
  capture v2: min 5, distinct 3, interval 100ms, span 400ms, window 32, max 256 submissions,
  duration 15s, max gap 1500ms. Fingerprint diversity means different bytes only.
- A UI epoch blocks late result publication after cancel/stop/rotation. Cancellation may
  signal in-flight SDK work immediately; releases are queued behind the worker. Last
  result is held for Check until Stop/Start/close, then all handles are released. Finish
  can yield valid Incomplete; validity never promotes a negative decision to Complete.
  onStop unbinds the camera, cancels and clears current use. Foreground requires Start.

## Measurements and verification

Last 512 analyzer samples, exact nearest-rank p50/p95, separately: adapter copy, JNI/SDK
submit (including JSON serialization), entire analyzer including wipe/close. Finalization,
UI dispatch and preview rendering are outside analyzer timing. Actual source/crop dimensions
and rotation accompany values; timing windows are per capture. CameraX does not expose an
exact KEEP_ONLY_LATEST drop count: show **UNKNOWN**, plus delivered/submitted/app-skipped/
error counters, rather than inventing drops from timestamps. Timings are not additive
exclusive costs and are not extrapolated from Linux to Android.

Unit tests exercise crop/padding/buffer positions, known channels, bounds and wiping.
Device tests load the actual ARM64 library, verify exact luma/digests across all rotations,
real SDK-clock sampling/validation, stale/invalid buffers, repeated cleanup, then real
CameraX at portrait/reverse portrait, lifecycle cancel/restart and close during SDK work.
Tests emit only identity/counter/quality/timing metadata. Host layout/JVM JNI smoke is a
separate Linux check (`tools/host_jni.sh`), never Android/camera evidence.

Actual results and remaining blockers are recorded in [docs/VALIDATION.md](docs/VALIDATION.md).
No open-source license grant for Nexus source is implied by this lab. Imported third-party
notices retain their terms. AI/models, secured fixture enrollment, platform attestation,
production issuer/witness, customer backends and fintech apps remain out of scope.

Official references:
- https://developer.android.com/reference/androidx/camera/core/ImageAnalysis#OUTPUT_IMAGE_FORMAT_RGBA_8888
- https://developer.android.com/reference/androidx/camera/core/ImageProxy.PlaneProxy
- https://developer.android.com/media/camera/camerax/analyze
- https://developer.android.com/training/camerax/orientation-rotation

## Device-bound receipt staging (ABI 2.11)

Secured capture now requests a server-signed challenge and passes it to core before any frame.
A persistent Keystore alias belongs to the device enrollment across sessions/processes; it is not silently replaced for an
old receipt. Complete finalization exports the exact core-owned JWS signing input, signs it with the
same enrolled key, converts Android DER to raw ES256 through Rust, revalidates and submits. The
`Submit receipt / Retry` action retries only that live payload; missing/changed key requires explicit
new enrollment. No metrics JSON is supplied by Kotlin to the core exporter.

The UI separates server receipt acceptance from client-reported capture claims and current SDK
result validity. Server online revocation can deny a still-live offline result. A held acceptance or
exported bytes are historical after expiry/cancellation. Signature does not prove camera origin,
core execution integrity, liveness or product authorization. Product eligibility remains false.

`.github/workflows/verify.yml` builds/test/lints the lab without a phone against a pinned packaged SDK.
A dedicated read-only SDK deploy key is stored in `NEXUS_SDK_READ_KEY`, distinct from issuer keys.
CI builds default preview; isolated staging root campaigns belong to SDK/service workflows. Physical
TEE capture-to-server evidence remains a separate campaign and is never inferred from CI builds.

Current receipt evidence: [RECEIPT_VALIDATION.md](docs/RECEIPT_VALIDATION.md).

## Persistent enrollment / fresh authorization

Staging has separate actions: Enroll device (first capture), Use existing enrollment (fresh authorized
capture), and Delete local Nexus enrollment. Cancel/close releases SDK state and cached receipt input,
not the enrollment key. Private keys stay in Keystore; bounded AtomicFile public metadata in
noBackupFilesDir selects a candidate alias and remains untrusted. Server PoP and core nonce binding
are verified anew. Missing/changed/revoked keys never create replacements or fall back to preview.

A pending alias is recorded before generation. Process death/incomplete enrollment needs explicit
reset; cleanup affects only that one recorded Nexus UUID alias, never other keys. Reset is local key
removal, not server revocation. Stored metadata describes historical app/key attestation; fresh PoP
does not re-inspect boot/application state. The service enforces certificate, attestation maximum age,
revocation and stored app/signer/version/security policy.

The same key can hold independent short SDK session grants. Capture passes explicit grant_id; neither
new authorization nor closing another session changes enrollment revision. UI shows historical key
level/verification time, fresh grant/expiry, capture/result validity and server acceptance separately.
Production remains blocked. See SDK ADR 035 and service docs/PERSISTENT_ENROLLMENT.md.

Physical campaign with existing metadata-only runner:

```sh
ANDROID_SERIAL=<device> NEXUS_SHELL_LAUNCH=1 python3 tools/run_instrumentation.py
```

For genuine process restart, run the persistent test in phase seed, end/force-stop its process, restart
the service preserving its journal, then run phase reuse:

```sh
NEXUS_TEST_CLASS=org.nexus.lab.CameraLabTest#persistentEnrollmentAcrossSessionsAndProcessRestart \
NEXUS_TEST_PHASE=seed ANDROID_SERIAL=<device> NEXUS_SHELL_LAUNCH=1 python3 tools/run_instrumentation.py
# Explicit process stop/service restart; no fixed sleep used as an ordering proof.
NEXUS_TEST_CLASS=org.nexus.lab.CameraLabTest#persistentEnrollmentAcrossSessionsAndProcessRestart \
NEXUS_TEST_PHASE=reuse ANDROID_SERIAL=<device> NEXUS_SHELL_LAUNCH=1 python3 tools/run_instrumentation.py
```
