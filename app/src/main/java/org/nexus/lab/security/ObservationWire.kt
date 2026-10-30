package org.nexus.lab.security

import java.security.MessageDigest

/**
 * Exact local serialization for the v1 client-reported observation object.
 * This is not a transport client, proof signer, provider verifier or authority
 * decision. Callers must bind and submit these bytes only through an approved
 * authenticated action transport.
 */
object ObservationWire {
    private const val MAX_CHECK_DURATION_MS = 2_000L
    private const val MAX_CHECK_AGE_MS = 5_000L

    fun canonicalReport(report: ObservationReport): ByteArray {
        validate(report)
        val checks = report.checks.joinToString(separator = ",") { check ->
            "{" + listOf(
                "\"age_ms\":${check.ageMs}",
                "\"duration_ms\":${check.durationMs}",
                "\"execution\":\"${check.execution.wire}\"",
                "\"finding\":\"${check.finding.wire}\"",
                "\"id\":\"${check.id}\"",
            ).joinToString(separator = ",") + "}"
        }
        return ("{\"checks\":[$checks],\"collector_version\":\"${Observation.VERSION}\",\"schema\":1}")
            .toByteArray(Charsets.UTF_8)
    }

    fun reportSha256(report: ObservationReport): String = sha256(canonicalReport(report))

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString(separator = "") { "%02x".format(it.toInt() and 0xff) }

    private fun validate(report: ObservationReport) {
        require(report.checks.size in 1..Observation.IDS.size)
        var previous = ""
        for (check in report.checks) {
            require(check.id in Observation.IDS && check.id > previous)
            require(check.durationMs <= MAX_CHECK_DURATION_MS)
            require(check.ageMs <= MAX_CHECK_AGE_MS)
            require(check.execution == Observation.Execution.COMPLETED || check.finding == Observation.Finding.UNKNOWN)
            previous = check.id
        }
    }
}
