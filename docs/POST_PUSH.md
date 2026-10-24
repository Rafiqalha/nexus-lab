# Post-push provenance

The original device validation report was written before this lab had a remote and remains unchanged.
The repository now uses the verified origin `https://github.com/Rafiqalha/nexus-lab.git` on main.
Its pre-enrollment remote baseline is `494221c55dc3e6736bc075826c02115ffd28382f`.

The authenticated enrollment milestone consumes SDK main
`ad41f5fde927de5eed2c9d7a104bc67fc22b8fb6` (ABI 2.10, 58 exports).
`SDK_IDENTITY.json` binds the exact packaged ARM64 source digest and library/header/manifest hashes.
New device and local checks are recorded separately in ENROLLMENT_VALIDATION.md/json.
The enrollment service is a separate sibling repository; it has no remote until the owner assigns one.

Enrollment implementation commit `1f9cbd222c5c0306a5d04c68df13182b1ed1396c` was pushed
to origin/main without force. GitHub Actions queried for that exact SHA returned zero runs;
this lab has no workflow configured. Local seven-test physical-device PASS is not hosted CI.
SDK run 39580027152 on ad41f5f completed all six jobs successfully.

## Receipt milestone

SDK 1c9458391fe9b9e6fb265f2cb9bc99fe9345d950 (ABI 2.11, 60 exports) has six successful jobs in run 40051466147.
New physical and local receipt evidence is separate in RECEIPT_VALIDATION.md/json. The lab now includes
a hardware-free workflow and a dedicated read-only SDK checkout secret. Hosted status for the next
lab commit is checked after push; the earlier no-workflow statement above remains historical.

## CI path correction

Lab run 40053508609 on d90d168 failed at JVM System.load because the default Cargo
target path was relative; Rust checks had passed and Android step was skipped.
tools/host_jni.sh now resolves package and target paths to absolute paths. Local
reproduction with CARGO_TARGET_DIR unset passes the same full host script, including
actual JNI/JVM and 80 cancel/release cycles (evidence/receipt-host-jni-ci-env.log).
The next workflow run must be assessed on its own SHA; no hosted PASS is inherited.

## Final-service device follow-up

Run 40056975193 on path-fix 0b1a3c2 completed SUCCESS. Physical 7/7 follow-up now covers
the final 0f01b21 service binary; exact artifacts, source identity, launcher requirement and
new durable acceptance are in RECEIPT_FOLLOWUP_20261024.md/json. Earlier reports remain historical.

## Persistent enrollment milestone

SDK 225b331 has six hosted SUCCESS jobs (40081519310); service 45d2f14 has hosted SUCCESS
(40081637626). New code, migration and local APK validation are in PERSISTENT_VALIDATION.md/json.
New hardware reuse/restart testing is NOT_RUN while USB/ADB is empty; prior physical reports are historical.
