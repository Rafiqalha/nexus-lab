package org.nexus.lab

/** Actionable staging outcomes. These messages never authorize key replacement or deletion. */
internal object EnrollmentStatus {
    fun refusal(code: String): String = when (code) {
        "policy_changed", "policy_changed_authorization_required", "authorization_policy_or_principal_changed",
        "enrollment_scope_or_policy", "enrollment_scope_or_policy_changed", "enrollment_revision_changed",
        "current_enrollment_statement_required" ->
            "Policy or enrollment context changed; obtain fresh session authorization using the existing key."
        "enrollment_policy_changed", "reattestation_required_policy_attributes",
        "reattestation_required_missing_application_version" ->
            "Stored enrollment attributes do not satisfy policy; new attestation/enrollment is required. No key was replaced."
        "reattestation_required_enrollment_age" ->
            "Attestation is too old; new attestation/enrollment is required. Fresh key proof does not refresh attestation."
        "device_key_revoked", "enrollment_revoked", "attestation_key_revoked" ->
            "Enrollment/key revoked; further use is denied. No replacement key was created."
        "grant_expired", "grant_expired_at_acceptance", "provisioning_expired", "provisioning_changed_or_expired",
        "authorization_challenge_expired", "capture_challenge_expired", "receipt_expired_at_acceptance" ->
            "Authorization expired; request a fresh session using the existing enrollment."
        "enrollment_not_found" ->
            "Enrollment is unavailable on the service; verify the service database before an explicit local reset."
        "revocation_unavailable_or_stale" ->
            "Service trust data unavailable; retry after the operator refreshes it. Local enrollment is retained."
        else -> "Staging request refused ($code); local enrollment is retained."
    }
}
