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
 * Individual time starts when submitted; total action time is bounded separately.
 * Late values are never published after timeout/cancellation.
 */
class CollectorCoordinator(
    private val collectors: Map<String, Collector>,
    private val maxConcurrent: Int = 2,
    private val checkBudgetMs: Long = 500,
    private val totalBudgetMs: Long = 1_500,
    private val executorFactory: (Int) -> ExecutorService = { size -> Executors.newFixedThreadPool(size) },
) {
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
        val submittedAt = linkedMapOf<String, Long>()
        try {
            for (id in Observation.IDS) {
                if (cancellation.isCancelled()) break
                val future = executor.submit(Callable { collectors.getValue(id).collect(cancellation) })
                futures[id] = future
                submittedAt[id] = System.nanoTime()
                cancellation.onCancel { future.cancel(true) }
            }
            val values = Observation.IDS.map { id ->
                val future = futures[id]
                if (future == null || cancellation.isCancelled()) {
                    unresolved(id, Observation.Execution.NOT_COLLECTED, startedAt)
                } else {
                    resolve(id, future, submittedAt.getValue(id), deadline, cancellation)
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
        submittedAt: Long,
        deadline: Long,
        cancellation: ObservationCancellation,
    ): Observation {
        val now = System.nanoTime()
        val perCheckDeadline = submittedAt + TimeUnit.MILLISECONDS.toNanos(checkBudgetMs)
        val waitNanos = minOf(perCheckDeadline, deadline) - now
        if (waitNanos <= 0 || cancellation.isCancelled()) {
            future.cancel(true)
            return unresolved(id, if (cancellation.isCancelled()) Observation.Execution.NOT_COLLECTED else Observation.Execution.TIMEOUT, submittedAt)
        }
        return try {
            val observation = future.get(waitNanos, TimeUnit.NANOSECONDS)
            require(observation.id == id)
            observation
        } catch (_: java.util.concurrent.TimeoutException) {
            future.cancel(true)
            unresolved(id, Observation.Execution.TIMEOUT, submittedAt)
        } catch (_: CancellationException) {
            unresolved(id, Observation.Execution.NOT_COLLECTED, submittedAt)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            cancellation.cancel()
            future.cancel(true)
            unresolved(id, Observation.Execution.NOT_COLLECTED, submittedAt)
        } catch (_: ExecutionException) {
            unresolved(id, Observation.Execution.ERROR, submittedAt)
        } catch (_: IllegalArgumentException) {
            unresolved(id, Observation.Execution.ERROR, submittedAt)
        }
    }

    private fun unresolved(id: String, execution: Observation.Execution, startedAt: Long): Observation = Observation(
        id = id,
        execution = execution,
        finding = Observation.Finding.UNKNOWN,
        durationMs = TimeUnit.NANOSECONDS.toMillis((System.nanoTime() - startedAt).coerceAtLeast(0)),
    )
}
