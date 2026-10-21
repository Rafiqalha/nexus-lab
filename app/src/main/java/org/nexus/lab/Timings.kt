package org.nexus.lab

import kotlin.math.ceil

/** Bounded last-512 sample window, exact nearest-rank percentiles; metadata only. */
class Timings {
    private val values = LongArray(512)
    private var count = 0
    private var next = 0
    fun add(ns: Long) { values[next] = ns; next = (next + 1) % values.size; count = minOf(count + 1, values.size) }
    fun summary(): String {
        if (count == 0) return "NOT_MEASURED"
        val sorted = values.copyOf(count).sorted()
        fun percentile(q: Double) = sorted[(ceil(count * q).toInt() - 1).coerceAtLeast(0)] / 1_000_000.0
        return "n=$count p50=%.3f p95=%.3f ms".format(percentile(.5), percentile(.95))
    }
}
