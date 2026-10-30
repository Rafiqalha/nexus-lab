package org.nexus.lab.security

import java.util.concurrent.Callable
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** Cooperative cancellation shared with lifecycle owners. */
class ObservationCancellation {
    private val cancelled = AtomicBoolean(false)
    private val listeners = ConcurrentLinkedQueue<() -> Unit>()
    fun cancel() {
        if (cancelled.compareAndSet(false, true)) listeners.forEach { it.invoke() }
    }
    fun isCancelled(): Boolean = cancelled.get()
    fun onCancel(listener: () -> Unit) {
        listeners.add(listener)
        if (isCancelled()) listener()
    }
}

fun interface Collector {
    fun collect(cancellation: ObservationCancellation): Observation
}

/**
 * Executes exactly the supported collector set, preserving contract order.
 * Individual time starts when execution starts; total action time is bounded separately.
 * Late values are never published after timeout/cancellation.
 */
class CollectorCoordinator(
    private val collectors: Map<String, Collector>,
    private val maxConcurrent: Int = 2,
    private val checkBudgetMs: Long = 500,
    private val totalBudgetMs: Long = 1_500,
    private val executorFactory: (Int) -> ExecutorService = { size -> Executors.newFixedThreadPool(size) },
) {
    private companion object {
        const val START_POLL_MS = 10L
    }

    init {
        require(collectors.keys == Observation.IDS.toSet())
        require(maxConcurrent in 1..2)
        require(checkBudgetMs > 0)
        require(totalBudgetMs >= checkBudgetMs)
    }

    fun collect(cancellation: ObservationCancellation = ObservationCancellation()): ObservationReport {
        val executor = executorFactory(maxConcurrent)
        val startedAt = System.nanoTime()
        val deadline = startedAt + TimeUnit.MILLISECONDS.toNanos(totalBudgetMs)
        val futures = linkedMapOf<String, Future<Observation>>()
        val startedAtById = linkedMapOf<String, AtomicLong>()
        try {
            for (id in Observation.IDS) {
                if (cancellation.isCancelled()) break
                val startedAt = AtomicLong(0)
                val future = executor.submit(Callable {
                    startedAt.compareAndSet(0, System.nanoTime())
                    collectors.getValue(id).collect(cancellation)
                })
                futures[id] = future
                startedAtById[id] = startedAt
                cancellation.onCancel { future.cancel(true) }
            }
            val values = Observation.IDS.map { id ->
                val future = futures[id]
                if (future == null || cancellation.isCancelled()) {
                    unresolved(id, Observation.Execution.NOT_COLLECTED, startedAt)
                } else {
                    resolve(id, future, startedAtById.getValue(id), deadline, cancellation)
                }
            }
            return ObservationReport(values)
        } finally {
            cancellation.cancel()
            futures.values.forEach { it.cancel(true) }
            executor.shutdownNow()
            executor.awaitTermination(100, TimeUnit.MILLISECONDS)
        }
    }

    private fun resolve(
        id: String,
        future: Future<Observation>,
        startedAt: AtomicLong,
        deadline: Long,
        cancellation: ObservationCancellation,
    ): Observation {
        while (true) {
            val now = System.nanoTime()
            val actualStart = startedAt.get()
            val perCheckDeadline = if (actualStart == 0L) {
                deadline
            } else {
                minOf(actualStart + TimeUnit.MILLISECONDS.toNanos(checkBudgetMs), deadline)
            }
            val waitNanos = perCheckDeadline - now
            if (waitNanos <= 0 || cancellation.isCancelled()) {
                future.cancel(true)
                return unresolved(
                    id,
                    if (cancellation.isCancelled()) Observation.Execution.NOT_COLLECTED else Observation.Execution.TIMEOUT,
                    actualStart.takeIf { it != 0L } ?: now,
                )
            }
            try {
                val pollNanos = if (actualStart == 0L) {
                    minOf(waitNanos, TimeUnit.MILLISECONDS.toNanos(START_POLL_MS))
                } else {
                    waitNanos
                }
                val observation = future.get(pollNanos, TimeUnit.NANOSECONDS)
                require(observation.id == id)
                return observation
            } catch (_: java.util.concurrent.TimeoutException) {
                val observedStart = startedAt.get()
                if (actualStart == 0L && observedStart != 0L) continue
                if (observedStart == 0L && System.nanoTime() < deadline) continue
                future.cancel(true)
                return unresolved(id, Observation.Execution.TIMEOUT, observedStart.takeIf { it != 0L } ?: now)
            } catch (_: CancellationException) {
                return unresolved(id, Observation.Execution.NOT_COLLECTED, actualStart.takeIf { it != 0L } ?: now)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                cancellation.cancel()
                future.cancel(true)
                return unresolved(id, Observation.Execution.NOT_COLLECTED, actualStart.takeIf { it != 0L } ?: now)
            } catch (_: ExecutionException) {
                return unresolved(id, Observation.Execution.ERROR, actualStart.takeIf { it != 0L } ?: now)
            } catch (_: IllegalArgumentException) {
                return unresolved(id, Observation.Execution.ERROR, actualStart.takeIf { it != 0L } ?: now)
            }
        }
    }

    private fun unresolved(id: String, execution: Observation.Execution, startedAt: Long): Observation = Observation(
        id = id,
        execution = execution,
        finding = Observation.Finding.UNKNOWN,
        durationMs = TimeUnit.NANOSECONDS.toMillis((System.nanoTime() - startedAt).coerceAtLeast(0)),
    )
}
