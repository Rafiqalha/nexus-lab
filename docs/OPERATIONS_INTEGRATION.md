# Staging service operations integration

The SDK importer now requires the manifest to name the supplied clean SDK commit.
It rejects a mismatched SHA before writing bindings or vendor assets. SDK runtime crate bytes remain at verified commit9184561; current package tooling is
pinned at 4b8a8ad5cd08da8799636b4391173d0d7eb8c4b3 and emits explicit SPDX alternatives.
Vendor directory identity includes both binary and manifest hashes, so a new source/metadata
package with identical library bytes cannot collide with an older immutable import.

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
