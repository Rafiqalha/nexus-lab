package org.nexus.lab.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class CollectorCoordinatorTest {
    private fun observation(id: String, finding: Observation.Finding = Observation.Finding.NO_KNOWN_INDICATOR) =
        Observation(id, Observation.Execution.COMPLETED, finding, 1)

    private fun collectors(block: (String) -> Observation): Map<String, Collector> =
        Observation.IDS.associateWith { id -> Collector { block(id) } }

    @Test fun reportHasStableContractOrderedIdsAndReportedScope() {
        val report = CollectorCoordinator(collectors { observation(it) }).collect()
        assertEquals(Observation.IDS, report.checks.map { it.id })
        assertEquals("lab-android-v1", Observation.VERSION)
        assertEquals(4, report.checks.size)
        assertTrue(report.summary().contains("no_known_indicator"))
    }

    @Test fun failureNeverTurnsIntoCleanFinding() {
        val report = CollectorCoordinator(collectors { id ->
            if (id == "android.debugger") throw IllegalStateException("broken")
            observation(id)
        }).collect()
        val failure = report.checks.single { it.id == "android.debugger" }
        assertEquals(Observation.Execution.ERROR, failure.execution)
        assertEquals(Observation.Finding.UNKNOWN, failure.finding)
    }

    @Test fun stalledCollectorTimesOutAndLateValueIsRejected() {
        val report = CollectorCoordinator(
            collectors { id ->
                if (id == "android.app_identity") Thread.sleep(200)
                observation(id)
            },
            checkBudgetMs = 25,
            totalBudgetMs = 100,
        ).collect()
        val timeout = report.checks.single { it.id == "android.app_identity" }
        assertEquals(Observation.Execution.TIMEOUT, timeout.execution)
        assertEquals(Observation.Finding.UNKNOWN, timeout.finding)
    }

    @Test fun noMoreThanTwoCollectorsRunConcurrently() {
        val active = AtomicInteger()
        val peak = AtomicInteger()
        val firstTwo = CountDownLatch(2)
        val release = CountDownLatch(1)
        val report = CollectorCoordinator(
            collectors { id ->
                val now = active.incrementAndGet()
                peak.getAndUpdate { old -> maxOf(old, now) }
                firstTwo.countDown()
                firstTwo.await(100, TimeUnit.MILLISECONDS)
                release.countDown()
                try { observation(id) } finally { active.decrementAndGet() }
            },
            checkBudgetMs = 300,
            totalBudgetMs = 800,
        ).collect()
        assertTrue(peak.get() <= 2)
        assertEquals(4, report.checks.size)
    }

    @Test fun queuedCollectorsReceiveTheirOwnExecutionBudget() {
        val active = AtomicInteger()
        val peak = AtomicInteger()
        val report = CollectorCoordinator(
            collectors { id ->
                val now = active.incrementAndGet()
                peak.getAndUpdate { previous -> maxOf(previous, now) }
                try {
                    Thread.sleep(
                        if (id == "android.app_identity" || id == "android.debugger") 220 else 120,
                    )
                    observation(id)
                } finally {
                    active.decrementAndGet()
                }
            },
            maxConcurrent = 2,
            checkBudgetMs = 300,
            totalBudgetMs = 1_500,
        ).collect()
        assertTrue(peak.get() <= 2)
        assertTrue(report.summary(), report.checks.all { it.execution == Observation.Execution.COMPLETED })
    }

    @Test fun queuedCollectorCannotRunPastItsOwnExecutionBudget() {
        val report = CollectorCoordinator(
            collectors { id ->
                Thread.sleep(
                    when (id) {
                        "android.app_identity", "android.debugger" -> 100
                        "android.environment" -> 500
                        else -> 20
                    },
                )
                observation(id)
            },
            maxConcurrent = 2,
            checkBudgetMs = 150,
            totalBudgetMs = 1_500,
        ).collect()
        val queuedSlow = report.checks.single { it.id == "android.environment" }
        assertEquals(report.summary(), Observation.Execution.TIMEOUT, queuedSlow.execution)
        assertEquals(Observation.Finding.UNKNOWN, queuedSlow.finding)
    }

    @Test fun preCancelledCollectionPublishesOnlyNotCollectedUnknown() {
        val cancellation = ObservationCancellation().also { it.cancel() }
        val invoked = AtomicInteger()
        val report = CollectorCoordinator(collectors { id -> invoked.incrementAndGet(); observation(id) }).collect(cancellation)
        assertEquals(0, invoked.get())
        assertTrue(report.checks.all { it.execution == Observation.Execution.NOT_COLLECTED && it.finding == Observation.Finding.UNKNOWN })
    }

    @Test fun cancellationInterruptsInFlightCollectorAndRejectsItsResult() {
        val started = CountDownLatch(1)
        val cancellation = ObservationCancellation()
        val executor = Executors.newSingleThreadExecutor()
        val task = executor.submit<ObservationReport> {
            CollectorCoordinator(
                collectors { id ->
                    if (id == "android.app_identity") {
                        started.countDown()
                        Thread.sleep(5_000)
                    }
                    observation(id)
                },
                checkBudgetMs = 5_000,
                totalBudgetMs = 6_000,
            ).collect(cancellation)
        }
        assertTrue(started.await(1, TimeUnit.SECONDS))
        cancellation.cancel()
        val report = task.get(1, TimeUnit.SECONDS)
        executor.shutdownNow()
        assertEquals(Observation.Execution.NOT_COLLECTED, report.checks.first().execution)
        assertEquals(Observation.Finding.UNKNOWN, report.checks.first().finding)
    }
}
