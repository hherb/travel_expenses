package com.travelexpenses

import com.travelexpenses.db.TravelExpensesDb
import com.travelexpenses.repository.SqlDelightEventLogRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * iOS-specific coroutine and threading tests.
 *
 * These run only on iOS targets (iosX64, iosArm64, iosSimulatorArm64)
 * to validate Kotlin/Native-specific concerns:
 *
 * - Memory model correctness (object sharing across threads)
 * - Concurrent writes from real background dispatchers
 * - Performance of SQLite operations
 * - No freezing-related crashes (legacy concern, but worth validating)
 * - Dispatchers.Main integration via test dispatcher
 */
class IosCoroutineTest {

    private val driver = createInMemoryDriver()
    private val db = TravelExpensesDb(driver)
    private val mainTestDispatcher = StandardTestDispatcher()

    /** Uses [Dispatchers.Default] as queryContext to exercise real Kotlin/Native threading. */
    private val repo = SqlDelightEventLogRepository(db, queryContext = Dispatchers.Default)

    @BeforeTest
    fun setup() {
        TestHelpers.resetSequence()
        Dispatchers.setMain(mainTestDispatcher)
    }

    @AfterTest
    fun teardown() {
        Dispatchers.resetMain()
        driver.close()
    }

    /**
     * Writes on [Dispatchers.Default] (real background thread), then reads on the
     * overridden [Dispatchers.Main]. Validates cross-dispatcher data visibility
     * under the new Kotlin/Native memory model.
     */
    @Test
    fun writeFromDefaultDispatcher_thenReadFromMain() = runTest {
        withContext(Dispatchers.Default) {
            for (i in 1..10) {
                repo.append(
                    TestHelpers.makeExpenseCreatedEvent(
                        expense = TestHelpers.makeExpense(id = "exp-$i"),
                        sequenceNumber = i.toLong(),
                    )
                )
            }
        }

        val count = withContext(Dispatchers.Main) {
            repo.count()
        }

        assertEquals(10L, count)
    }

    /**
     * Validates that SQLDelight's query-as-Flow works when collected on the Main dispatcher.
     */
    @Test
    fun flowCollection_worksOnMainDispatcher() = runTest {
        repo.append(TestHelpers.makeExpenseCreatedEvent(sequenceNumber = 1))

        val count = withContext(Dispatchers.Main) {
            repo.observeEventCount().first()
        }

        assertEquals(1L, count)
    }

    /**
     * Launches 20 concurrent coroutines on [Dispatchers.Default] (real threads).
     * This is THE critical Kotlin/Native memory model test -- under the old (pre-1.7.20)
     * model, this would crash with [InvalidMutabilityException].
     */
    @Test
    fun concurrentWritesFromNativeDispatchers_noFreezeErrors() = runTest {
        val eventCount = 20
        val jobs = (1..eventCount).map { i ->
            async(Dispatchers.Default) {
                repo.append(
                    TestHelpers.makeExpenseCreatedEvent(
                        expense = TestHelpers.makeExpense(id = "exp-$i"),
                        sequenceNumber = i.toLong(),
                    )
                )
            }
        }

        jobs.awaitAll()
        assertEquals(eventCount.toLong(), repo.count())
    }

    /**
     * Write from Default, read from Main, write again, read again.
     * Validates that a single repository instance is safely shareable
     * across dispatchers without data races.
     */
    @Test
    fun repositorySharedAcrossDispatchers_noMemoryModelIssues() = runTest {
        withContext(Dispatchers.Default) {
            repo.append(TestHelpers.makeExpenseCreatedEvent(sequenceNumber = 1))
        }

        val events = withContext(Dispatchers.Main) {
            repo.getAllEvents()
        }
        assertEquals(1, events.size)

        withContext(Dispatchers.Default) {
            repo.append(TestHelpers.makeExpenseCreatedEvent(sequenceNumber = 2))
        }

        val updatedCount = withContext(Dispatchers.Main) {
            repo.count()
        }
        assertEquals(2L, updatedCount)
    }

    /**
     * Measures read latency for 100 events. The threshold is generous (100ms) to
     * account for CI variance, but in practice we expect well under 16ms (one frame
     * at 60fps) for this dataset size on real hardware.
     */
    @Test
    fun sqliteQueryPerformance_underThreshold() = runTest {
        val eventCount = 100
        for (i in 1..eventCount) {
            repo.append(
                TestHelpers.makeExpenseCreatedEvent(
                    expense = TestHelpers.makeExpense(id = "exp-$i"),
                    sequenceNumber = i.toLong(),
                )
            )
        }

        val startTime = Clock.System.now()
        val events = repo.getAllEvents()
        val elapsed = Clock.System.now() - startTime

        assertEquals(eventCount, events.size)
        val maxAcceptableMs = 100L
        assertTrue(
            elapsed.inWholeMilliseconds < maxAcceptableMs,
            "Query took ${elapsed.inWholeMilliseconds}ms, expected < ${maxAcceptableMs}ms"
        )
    }
}
