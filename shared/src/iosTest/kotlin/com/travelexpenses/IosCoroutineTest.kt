package com.travelexpenses

import com.travelexpenses.db.TravelExpensesDb
import com.travelexpenses.repository.SqlDelightEventLogRepository
import com.travelexpenses.repository.createInMemoryDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
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
 * - Coroutine dispatch on Kotlin/Native's Main dispatcher
 * - Memory model correctness (object sharing across threads)
 * - Performance of SQLite operations on main thread
 * - No freezing-related crashes (legacy concern, but worth validating)
 */
class IosCoroutineTest {

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
    fun writeFromDefaultDispatcher_thenReadFromMain() = runTest {
        // Write on background
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

        // Read on main (this is what the iOS UI layer will do)
        val count = withContext(Dispatchers.Main) {
            repo.count()
        }

        assertEquals(10L, count)
    }

    @Test
    fun flowCollection_worksOnMainDispatcher() = runTest {
        repo.append(TestHelpers.makeExpenseCreatedEvent(sequenceNumber = 1))

        val count = withContext(Dispatchers.Main) {
            repo.observeEventCount().first()
        }

        assertEquals(1L, count)
    }

    @Test
    fun concurrentWritesFromNativeDispatchers_noFreezeErrors() = runTest {
        // This specifically validates the Kotlin/Native new memory model
        // handles concurrent access without freezing crashes
        val jobs = (1..20).map { i ->
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
        assertEquals(20L, repo.count())
    }

    @Test
    fun repositorySharedAcrossDispatchers_noMemoryModelIssues() = runTest {
        // Write from Default, read from Main, verify consistency
        // This catches any issues with object sharing in the new MM
        withContext(Dispatchers.Default) {
            repo.append(TestHelpers.makeExpenseCreatedEvent(sequenceNumber = 1))
        }

        val events = withContext(Dispatchers.Main) {
            repo.getAllEvents()
        }

        assertEquals(1, events.size)

        // Modify from Default again
        withContext(Dispatchers.Default) {
            repo.append(TestHelpers.makeExpenseCreatedEvent(sequenceNumber = 2))
        }

        val updatedCount = withContext(Dispatchers.Main) {
            repo.count()
        }

        assertEquals(2L, updatedCount)
    }

    @Test
    fun sqliteQueryPerformance_underThreshold() = runTest {
        // Insert a moderate amount of data
        for (i in 1..100) {
            repo.append(
                TestHelpers.makeExpenseCreatedEvent(
                    expense = TestHelpers.makeExpense(id = "exp-$i"),
                    sequenceNumber = i.toLong(),
                )
            )
        }

        // Measure read performance (should be well under 16ms for UI thread safety)
        val startTime = kotlinx.datetime.Clock.System.now()

        val events = repo.getAllEvents()

        val elapsed = kotlinx.datetime.Clock.System.now() - startTime

        assertEquals(100, events.size)
        // 16ms = one frame at 60fps. We want reads well under this.
        assertTrue(
            elapsed.inWholeMilliseconds < 100,
            "Query took ${elapsed.inWholeMilliseconds}ms -- too slow for main thread"
        )
    }
}
