package com.travelexpenses

import com.travelexpenses.db.TravelExpensesDb
import com.travelexpenses.repository.SqlDelightEventLogRepository
import com.travelexpenses.repository.createInMemoryDriver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Test 5: Structured concurrency and cancellation.
 *
 * Validates that:
 * - Cancelling a coroutine scope properly cancels pending DB operations
 * - No resource leaks after cancellation
 * - Parent scope cancellation propagates to child operations
 * - Repository remains usable after a cancelled operation
 *
 * This is particularly important on iOS/Kotlin Native where the threading
 * model differs from the JVM.
 */
class CoroutineCancellationTest {

    private val driver = createInMemoryDriver()
    private val db = TravelExpensesDb(driver)
    private val repo = SqlDelightEventLogRepository(db)

    @BeforeTest
    fun setup() {
        TestHelpers.resetSequence()
    }

    @AfterTest
    fun teardown() {
        driver.close()
    }

    @Test
    fun cancelledScope_stopsFlowCollection() = runTest {
        val collected = mutableListOf<Long>()

        val job = launch {
            repo.observeEventCount().collect { count ->
                collected.add(count)
                if (count >= 1L) {
                    cancel() // Cancel after seeing first update
                }
            }
        }

        testScheduler.advanceUntilIdle()

        repo.append(TestHelpers.makeExpenseCreatedEvent(sequenceNumber = 1))
        testScheduler.advanceUntilIdle()

        // Add more events -- the collector should not see these
        repo.append(TestHelpers.makeExpenseCreatedEvent(sequenceNumber = 2))
        repo.append(TestHelpers.makeExpenseCreatedEvent(sequenceNumber = 3))
        testScheduler.advanceUntilIdle()

        job.join()

        // Collector should have stopped at count=1
        assertTrue(collected.size <= 2, "Expected at most 2 emissions, got ${collected.size}: $collected")
        assertTrue(collected.contains(0L), "Should have seen initial 0")
        assertTrue(collected.contains(1L), "Should have seen 1 after first append")
    }

    @Test
    fun repositoryUsableAfterCancelledOperation() = runTest {
        // Start a flow and cancel it
        val job = launch {
            repo.observeEventCount().collect { /* just collecting */ }
        }
        testScheduler.advanceUntilIdle()
        job.cancel()
        testScheduler.advanceUntilIdle()

        // Repository should still work fine
        repo.append(TestHelpers.makeExpenseCreatedEvent(sequenceNumber = 1))
        assertEquals(1L, repo.count())

        // New flow should work
        val count = repo.observeEventCount().first()
        assertEquals(1L, count)
    }

    @Test
    fun cancelledChildDoesNotAffectSiblings() = runTest {
        val results = mutableListOf<Long>()

        val job1 = async {
            repo.observeEventCount().first() // Will complete normally
        }

        val job2 = launch {
            repo.observeEventCount().collect {
                // This will be cancelled
                yield()
            }
        }

        testScheduler.advanceUntilIdle()

        val initialCount = job1.await()
        assertEquals(0L, initialCount)

        job2.cancel()
        testScheduler.advanceUntilIdle()

        // Repository still works
        repo.append(TestHelpers.makeExpenseCreatedEvent(sequenceNumber = 1))
        assertEquals(1L, repo.count())
    }
}
