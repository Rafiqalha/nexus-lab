# Staging service operations integration

The SDK importer now requires the manifest to name the supplied clean SDK commit.
It rejects a mismatched SHA before writing bindings or vendor assets. SDK runtime source
is pinned at 91845610d2c699ab0ccfc4a9835aa5499e0b2036; later SDK acceptance-report-only
commits can have the same source digest without changing this immutable package identity.

The lab retains its enrollment/key on service overload, issuance quota, interrupted body
or storage unavailability. Retry overload after a short delay using the existing enrollment;
ask the operator to inspect readiness/capacity for storage failures. These responses never
create a replacement key or promote preview to trusted. The new status cases are covered by
EnrollmentStatusTest. Real camera/Keystore/restart acceptance remains BLOCKED without a device.

Local default and staging builds are debug-signed laboratory APKs, not production signing.
The staging profile retains the previously pinned isolated staging public root; this campaign
does not generate or authorize production key material. Host JNI/JVM and Android unit/lint
checks do not prove sensor authenticity or execution integrity. Intensive fuzz work was
explicitly deferred by the user on 2026-10-25; do not treat it as passed.
