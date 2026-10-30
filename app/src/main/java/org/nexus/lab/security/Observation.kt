package org.nexus.lab.security

import org.json.JSONArray
import org.json.JSONObject

/**
 * A bounded local observation. `finding` describes only the check's observed
 * scope; it is never a device-integrity or authorization verdict.
 */
data class Observation(
    val id: String,
    val execution: Execution,
    val finding: Finding,
    val durationMs: Long,
    val ageMs: Long = 0,
) {
    init {
        require(id in IDS)
        require(durationMs >= 0)
        require(ageMs >= 0)
        require((execution == Execution.COMPLETED) == (finding != Finding.UNKNOWN))
    }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("execution", execution.wire)
        .put("finding", finding.wire)
        .put("duration_ms", durationMs)
        .put("age_ms", ageMs)

    enum class Execution(val wire: String) {
        COMPLETED("completed"),
        UNSUPPORTED("unsupported"),
        PERMISSION_DENIED("permission_denied"),
        TIMEOUT("timeout"),
        ERROR("error"),
        NOT_COLLECTED("not_collected"),
    }

    enum class Finding(val wire: String) {
        NO_KNOWN_INDICATOR("no_known_indicator"),
        RISK_OBSERVED("risk_observed"),
        UNKNOWN("unknown"),
    }

    companion object {
        const val VERSION = "lab-android-v1"
        val IDS = listOf(
            "android.app_identity",
            "android.debugger",
            "android.environment",
            "native.process_indicators",
        )
    }
}

data class ObservationReport(val checks: List<Observation>) {
    init {
        require(checks.map { it.id } == Observation.IDS)
    }

    fun toJson(): JSONObject = JSONObject()
        .put("schema", 1)
        .put("collector_version", Observation.VERSION)
        .put("checks", JSONArray(checks.map { it.toJson() }))

    fun summary(): String = checks.joinToString(separator = "; ") {
        "${it.id}=${it.execution.wire}/${it.finding.wire}/${it.durationMs}ms"
    }
}
