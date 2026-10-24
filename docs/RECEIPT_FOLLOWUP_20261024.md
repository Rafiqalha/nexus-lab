# Receipt follow-up — 2026-10-24

The previously missing physical rerun is now complete against service **0f01b218e593df243cb3b00aa66fdc4ad9d4bc7f**, including its final revision-floor guard.
Service source digest `7c520995ca07670d5c50fda59d15684b7a9243afd6093bb5f07672388a5c3049`
and binary SHA256 `e8e4ac5070dba468c3b8f4f34b989b00c66c73e0c4d84bf99c8fecfaff49b570`
matched the final service validation report before the device run. Service still has no remote.
Earlier reports remain historical and are not rewritten.

**Device PASS: 7/7, 43.533 seconds**, Redmi Note 12 / Xiaomi 23021RAA2Y, Android 15/API 35,
ARM64, genuine Android attestation key level trusted_environment (TEE). Installed release APK and
both native libraries matched the recorded SDK 1c94583 package / ABI 2.11 identity. No APK rebuild
was needed for the host-script-only CI correction. Roots/status were freshly fetched from official
Android endpoints; no trust substitution, new production root or fixture key was used.

Enrollment → signed challenge bound before capture → Complete core receipt → same enrolled TEE
key signature → server acceptance persisted. Acceptance ID
`91467778bd5ef3ee1e9a951d83c7a8966465cdbc2030d7fba0b2a25319b38163`, payload SHA256
`1a623fde0b46dc24af906921e5a249423a23bcf9f58f19b9a9c96de214b9e61a`.
Read-only journal inspection confirmed one durable acceptance and exact registered key binding.
The suite also checked identical retry, missing-key denial, monotonic expiry invalidation, Incomplete,
buffer bounds/color/orientation, lifecycle cancellation and cleanup. Physical tests used current
revision 1; advancing-revision rejection is the separate service debug/release regression, not an
unperformed device journal mutation.

Exact invocation:

```sh
ANDROID_SERIAL=b728feaa NEXUS_SHELL_LAUNCH=1 python3 tools/run_instrumentation.py
```

This existing metadata-only runner launches the same public MAIN/LAUNCHER activity when the test
announces its start, to accommodate MIUI restrictions. Direct instrumentation attempts stalled and
were stopped; their incomplete logs are retained, never classified PASS. The earlier receipt report's
bare `am instrument` command was shorthand for the inner command; its recorded launcher intents
show that the same shell-launch runner was required there too. No keyguard, OEM permission or global
setting was changed. No screenshots, camera frames or private keys were retained in evidence.

CI run [40053508609](https://github.com/Rafiqalha/nexus-lab/actions/runs/40053508609) on d90d168 failed
because System.load received a relative JNI path; ARM64 step was skipped. The path correction in
**0b1a3c28f52cb8dc2395fce66313b7942c463bc9** passed local reproduction with CARGO_TARGET_DIR unset.
Hosted run [40056975193](https://github.com/Rafiqalha/nexus-lab/actions/runs/40056975193) then completed
SUCCESS: packaged host SDK/JNI/JVM checks, ARM64 release build, unit tests and lint. SDK run
[40051466147](https://github.com/Rafiqalha/nexus-sdk/actions/runs/40051466147) on 1c94583 remains six-job SUCCESS.
A report-only follow-up commit is checked separately after push; conclusions above apply to exact SHAs.

Production remains **BLOCKED**, product_eligible=false. Server receipt acceptance authenticates key,
message and challenge/current state; capture claims remain client_reported, camera provenance is
not_verified and manipulation is not_evaluated. No liveness, KYC, fraud or transaction authorization
is implied. Next priority is a separate review of the production enrollment/trust/provider requirements.
