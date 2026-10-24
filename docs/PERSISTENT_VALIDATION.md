# Persistent enrollment and fresh session authorization — 2026-10-24

SDK **225b331c062debc04dc5a46b454bdbcee256d295**, service **45d2f141078af47422b0041d503a7b5ba239eba6**.
SDK ABI stays 2.11 / **60 exports**, same C layouts/header. Historical reports remain unchanged.
Source/log/artifact hashes and commands are in PERSISTENT_VALIDATION.json.

The Lab keeps one device enrollment alias across SDK sessions and Android process restarts. AtomicFile
stores bounded public metadata in noBackupFilesDir; server/core still verify fresh context. Ownership
is recorded pending before generation. Failure never replaces a key; explicit local reset removes
exactly the recorded Nexus UUID alias. Session close/cancel releases handles and receipt cache without
deleting enrollment. Enroll, use existing and reset are separate UI actions; preview remains separate.
PoP signing checks exact purpose and bootstrap/session context before signing original server bytes;
it cannot sign an enrollment/receipt payload through the authorization path. Core remains authoritative
for receipt bytes and signed provisioning/time/platform/capture binding. Historical attestation time,
key/security level and app version are displayed separately from fresh grant expiry and result validity.

The service fixes strong_box/strongbox using one typed serialization. New session authorization uses
the registered key and domain nexus.session-authorization-pop.staging.v1, consuming challenge and
saving one grant transactionally. Multiple live grants share stable enrollment revision; revocation or
new validated attestation advances security revision. Exact grant/provisioning digest selection prevents
new sessions from replacing older live ones. Age defaults 24h; certificate, <=300s revocation snapshot,
stored version/signer/app/security policy and original challenge/lease freshness remain mandatory.

Local PASS: packaged SDK import, Rust/JNI/JVM with 80 cancel/release cycles, Android staging release
build and unit tests (including PoP domain separation), lint, APK signature and 16KiB alignment. APK
and embedded native hashes match the new package identity. No new dependencies or native bindings
were introduced. Service debug/release: **16 passed, 2 explicitly ignored**, with those **2 passing**
separately through ephemeral-root public Rust / actual loaded C ABI reauthorization and capture receipt.
Configurations are not added together as unique tests. HTTP process A -> B -> C verifies same-key
fresh authorization, two durable receipts, idempotent retry and committed revocation denial.

The actual isolated staging journal was backed up outside Git, then migrated by a real service process
on an ephemeral loopback port and stopped. user_version **0 -> 1**, **7 grants backfilled**, and all five
historical tables (including clock, consumption, revocation and acceptance) retained exact hashes.
See evidence/persistent-live-migration.json for counts and the private backup path/hash. This is process
persistence evidence, not power-loss or independent hardware anti-rollback proof.

Hosted SDK [40081519310](https://github.com/Rafiqalha/nexus-sdk/actions/runs/40081519310): six jobs SUCCESS
on 225b331. Hosted service [40081637626](https://github.com/Rafiqalha/nexus-enrollment/actions/runs/40081637626):
SUCCESS on 45d2f14. Lab new-SHA CI is checked after push; no result is inherited.

**Hardware BLOCKED / NOT_RUN for this milestone:** the Redmi is absent from USB and ADB, even after
restarting its daemon; a readiness request is pending. Earlier 7/7 physical TEE receipt tests do not
prove new persistent lifecycle behavior. StrongBox coverage is synthetic only, not a physical PASS.
The existing runner now supports full 8-test suite and separate seed/reuse phases for genuine Android
process stop plus service restart (commands in README). They require a connected unlocked device.
No images, camera frames, private keys or journal contents are committed. Build artifacts stay ignored.

Production remains BLOCKED, product_eligible=false. Fresh PoP proves use of the enrolled key, not fresh
boot/app inspection, truthful metrics, SDK execution integrity, camera provenance, liveness or transaction
eligibility. Offline revocation is bounded by signed lease; no new continuity/witness protocol is added.
