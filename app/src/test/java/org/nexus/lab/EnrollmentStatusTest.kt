package org.nexus.lab

import org.junit.Assert.*
import org.junit.Test

class EnrollmentStatusTest {
    @Test fun policyChangesRequireFreshAuthorizationWithExistingKey() {
        for (code in listOf("policy_changed_authorization_required", "authorization_policy_or_principal_changed", "enrollment_scope_or_policy_changed")) {
            assertTrue(EnrollmentStatus.refusal(code).contains("existing key"))
        }
    }
    @Test fun policyAttributesAndAttestationAgeHaveDifferentActions() {
        assertTrue(EnrollmentStatus.refusal("reattestation_required_policy_attributes").contains("attributes"))
        assertTrue(EnrollmentStatus.refusal("reattestation_required_enrollment_age").contains("too old"))
    }
    @Test fun revocationAndUnavailableServiceNeverOfferAutomaticReplacement() {
        assertTrue(EnrollmentStatus.refusal("device_key_revoked").contains("No replacement"))
        assertTrue(EnrollmentStatus.refusal("revocation_unavailable_or_stale").contains("retained"))
        assertTrue(EnrollmentStatus.refusal("unknown_service_error").contains("retained"))
    }
    @Test fun expiredGrantNeedsFreshSessionWithoutReset() {
        assertTrue(EnrollmentStatus.refusal("grant_expired").contains("existing enrollment"))
    }
}
