package com.travelexpenses

import app.cash.sqldelight.db.SqlDriver
import com.travelexpenses.db.TravelExpensesDb
import com.travelexpenses.event.ExpenseEvent
import com.travelexpenses.repository.SqlDelightEventLogRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Test 4: Flow-based reactive observation.
 *
 * Validates that SQLDelight's query-as-Flow mechanism works on all platforms:
 * - Flow emits initial value on collection
 * - Flow emits updates when underlying data changes
 * - Multiple collectors receive updates independently
 *
 * This is critical for the UI layer -- SwiftUI/Compose need to observe
 * database changes reactively.
 */
class FlowObservationTest {

    private lateinit var driver: SqlDriver
    private lateinit var db: TravelExpensesDb
    private lateinit var repo: SqlDelightEventLogRepository
    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setup() {
        TestHelpers.resetSequence()
        driver = createInMemoryDriver()
        db = TravelExpensesDb(driver)
        repo = SqlDelightEventLogRepository(db, queryContext = testDispatcher)
    }

    @AfterTest
    fun teardown() {
        driver.close()
    }

    @Test
    fun observeEventCount_emitsInitialZero() = runTest {
        val count = repo.observeEventCount().first()
        assertEquals(0L, count)
    }

    @Test
    fun observeEventCount_emitsUpdatesAfterAppend() = runTest {
        val collectedCounts = mutableListOf<Long>()

        val job = launch {
            repo.observeEventCount()
                .take(3)
                .toList(collectedCounts)
        }

        advanceUntilIdle() // Let the first emission (0) happen

        repo.append(TestHelpers.makeExpenseCreatedEvent(sequenceNumber = 1))
        advanceUntilIdle()

        repo.append(TestHelpers.makeExpenseCreatedEvent(sequenceNumber = 2))
        advanceUntilIdle()

        job.join()

        assertEquals(3, collectedCounts.size)
        assertEquals(0L, collectedCounts[0])
        assertEquals(1L, collectedCounts[1])
        assertEquals(2L, collectedCounts[2])
    }

    @Test
    fun observeAllEvents_emitsEventList() = runTest {
        val collectedSnapshots = mutableListOf<List<ExpenseEvent>>()

        val job = launch {
            repo.observeAllEvents()
                .take(3)
                .toList(collectedSnapshots)
        }

        advanceUntilIdle()

        repo.append(
            TestHelpers.makeExpenseCreatedEvent(
                expense = TestHelpers.makeExpense(id = "exp-1"),
                sequenceNumber = 1,
            )
        )
        advanceUntilIdle()

        repo.append(
            TestHelpers.makeTripCreatedEvent(
                trip = TestHelpers.makeTrip(id = "trip-1"),
                sequenceNumber = 2,
            )
        )
        advanceUntilIdle()

        job.join()

        assertEquals(3, collectedSnapshots.size)

        // Snapshot 0: empty
        assertEquals(0, collectedSnapshots[0].size)

        // Snapshot 1: one expense
        assertEquals(1, collectedSnapshots[1].size)
        assertIs<ExpenseEvent.ExpenseCreated>(collectedSnapshots[1][0])

        // Snapshot 2: one expense + one trip
        assertEquals(2, collectedSnapshots[2].size)
    }

    @Test
    fun multipleCollectors_receiveIndependentUpdates() = runTest {
        val counts1 = mutableListOf<Long>()
        val counts2 = mutableListOf<Long>()

        val job1 = launch {
            repo.observeEventCount().take(2).toList(counts1)
        }
        val job2 = launch {
            repo.observeEventCount().take(2).toList(counts2)
        }

        advanceUntilIdle()

        repo.append(TestHelpers.makeExpenseCreatedEvent(sequenceNumber = 1))
        advanceUntilIdle()

        job1.join()
        job2.join()

        // Both collectors should see the same sequence
        assertEquals(listOf(0L, 1L), counts1)
        assertEquals(listOf(0L, 1L), counts2)
    }
}
