# Persistent device trust and policy evolution — isolated staging

One persistent Nexus Keystore alias remains selected across session close, activity/process restart,
network failure and policy refusal. An explicit local reset deletes exactly its recorded alias and
metadata; it does not revoke server enrollment. Missing/changed keys refuse reuse without generating
replacements. Pending initial enrollment remains explicit after an interrupted operation.

After server PoP and core provisioning acceptance, the lab updates its bounded AtomicFile public
metadata with the current signed enrollment attributes and signed policy identity. Identity continuity
requires the same enrollment ID and public-key fingerprint. Attestation certificate hash and validated
attributes are versioned by the service and may change after accepted genuine re-attestation. Metadata
never becomes an authority: every session still proves the existing key and verifies fresh core nonces.

The service encodes the operator policy digest in signed policy_id. Unlike lease-specific policy bytes,
this identity stays stable across ordinary grants and changes with operator configuration. The lab can
show a successful compatible policy transition and fresh authorization using the same key. Policy
refusal distinguishes fresh authorization, incompatible attributes, old attestation, revoked enrollment,
expired grant and unavailable service/trust data. Errors retain key/metadata. Fresh PoP does not inspect
boot/app state again or refresh historical attestation time. The lab does not assume Android supports
new attestation for an existing key; resetting/enrolling a replacement remains an explicit operation.

Capture Complete, current-result validity and server acceptance remain independently displayed.
An online server update/revocation may deny a still-live offline SDK lease; the SDK has no immediate
remote invalidation channel. Metrics remain client_reported and product_eligible=false.

Use the existing persistent instrumentation campaign phases seed/reuse for device key reuse, with an
explicit app process stop and service restart preserving SQLite between phases. Apply compatible or
incompatible policy with the service's trusted policy-update CLI, then repeat reuse. Verify stable key
fingerprint and different grant/session/nonces, denial of prior receipt and revoked/incompatible keys.
Do not use a fixed sleep as ordering evidence. Synthetic service tests cover same-key re-attestation.

The local ADB inventory was empty for this milestone. Device acceptance is NOT_RUN/BLOCKED; no new
physical key/camera/TEE/StrongBox claim is made. Host/JVM/build evidence and source/binary identities
are separate. CI uploads only JSON summaries and hashes, never keys, device chains, database or frames.
