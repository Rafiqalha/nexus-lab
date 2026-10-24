package org.nexus.lab
import org.junit.Assert.*
import org.junit.Test
class EnrollmentPurposeTest {
    @Test fun proofPurposesCannotBecomeReceiptOrEnrollmentSigningOracle() {
        val enrolled="nexus.enrollment-pop.staging.v1"
        val authorized="nexus.session-authorization-pop.staging.v1"
        Enrollment.requirePoPPurpose((authorized+"\n{}").toByteArray(),authorized)
        assertTrue(runCatching { Enrollment.requirePoPPurpose((enrolled+"\n{}").toByteArray(),authorized) }.isFailure)
        assertTrue(runCatching { Enrollment.requirePoPPurpose((authorized+"\n{}").toByteArray(),enrolled) }.isFailure)
        assertTrue(runCatching { Enrollment.requirePoPPurpose("eyJhbGciOiJFUzI1NiJ9.eyJzY2hlbWEiOjF9".toByteArray(),authorized) }.isFailure)
        assertTrue(runCatching { Enrollment.requirePoPPurpose((authorized+"\n"+"x".repeat(8193)).toByteArray(),authorized) }.isFailure)
    }
}
