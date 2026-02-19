package com.travelexpenses

import com.travelexpenses.db.TravelExpensesDb
import com.travelexpenses.event.EventReplayEngine
import com.travelexpenses.repository.SqlDelightEventLogRepository
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * End-to-end integration tests: append event via repository → verify materialized state updated.
 * These test the full wired path: EventLogRepository.append() → event_log insert + replay engine → materialized state.
 */
class IntegrationTest {

    private lateinit var db: TravelExpensesDb
    private lateinit var repo: SqlDelightEventLogRepository
    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        val driver = createInMemoryDriver()
        db = TravelExpensesDb(driver)
        val replayEngine = EventReplayEngine(db, testDispatcher)
        repo = SqlDelightEventLogRepository(db, testDispatcher, replayEngine)
        TestHelpers.resetSequence()
    }

    @Test
    fun appendExpenseCreated_materializedStateUpdated() = runTest(testDispatcher) {
        val expense = TestHelpers.makeExpense(
            id = "exp-1",
            tripId = "trip-1",
            amount = "55.00",
            currency = "GBP",
        )
        repo.append(TestHelpers.makeExpenseCreatedEvent(expense = expense))

        // Verify both event log and materialized state
        assertEquals(1L, repo.count(), "Event should be in event log")

        val row = db.materializedStateQueries.selectExpenseById("exp-1").executeAsOneOrNull()
        assertNotNull(row, "Expense should exist in materialized state")
        assertEquals("55.00", row.amount)
        assertEquals("GBP", row.currency)
    }

    @Test
    fun appendExpenseUpdated_materializedStateReflectsChange() = runTest(testDispatcher) {
        val expense = TestHelpers.makeExpense(id = "exp-1", amount = "10.00")
        repo.append(TestHelpers.makeExpenseCreatedEvent(expense = expense))
        repo.append(TestHelpers.makeExpenseUpdatedEvent(expenseId = "exp-1", amount = "20.00"))

        assertEquals(2L, repo.count(), "Both events should be in event log")

        val row = db.materializedStateQueries.selectExpenseById("exp-1").executeAsOneOrNull()
        assertNotNull(row)
        assertEquals("20.00", row.amount, "Materialized state should reflect update")
    }

    @Test
    fun appendExpenseDeleted_materializedStateMarksDeleted() = runTest(testDispatcher) {
        val expense = TestHelpers.makeExpense(id = "exp-1")
        repo.append(TestHelpers.makeExpenseCreatedEvent(expense = expense))
        repo.append(TestHelpers.makeExpenseDeletedEvent(expenseId = "exp-1"))

        assertEquals(2L, repo.count(), "Both events should be in event log")

        val row = db.materializedStateQueries.selectExpenseById("exp-1").executeAsOneOrNull()
        assertNull(row, "Deleted expense should not be returned")
    }

    @Test
    fun fullLifecycle_createUpdateDeleteTrip() = runTest(testDispatcher) {
        // 1. Create trip
        val trip = TestHelpers.makeTrip(id = "trip-1", name = "Europe", baseCurrency = "EUR")
        repo.append(TestHelpers.makeTripCreatedEvent(trip = trip))

        var tripRow = db.materializedStateQueries.selectTripById("trip-1").executeAsOneOrNull()
        assertNotNull(tripRow)
        assertEquals("Europe", tripRow.name)

        // 2. Create expense in trip
        val expense = TestHelpers.makeExpense(id = "exp-1", tripId = "trip-1", amount = "50.00")
        repo.append(TestHelpers.makeExpenseCreatedEvent(expense = expense))

        val expenses = db.materializedStateQueries.selectExpensesForTrip("trip-1").executeAsList()
        assertEquals(1, expenses.size)

        // 3. Update expense
        repo.append(TestHelpers.makeExpenseUpdatedEvent(expenseId = "exp-1", amount = "75.00"))

        val updatedExpense = db.materializedStateQueries.selectExpenseById("exp-1").executeAsOneOrNull()
        assertNotNull(updatedExpense)
        assertEquals("75.00", updatedExpense.amount)

        // 4. Delete expense
        repo.append(TestHelpers.makeExpenseDeletedEvent(expenseId = "exp-1"))

        val activeExpenses = db.materializedStateQueries.selectExpensesForTrip("trip-1").executeAsList()
        assertEquals(0, activeExpenses.size, "No active expenses after deletion")

        // 5. Archive trip
        repo.append(TestHelpers.makeTripArchivedEvent(tripId = "trip-1"))

        val activeTrips = db.materializedStateQueries.selectAllTrips().executeAsList()
        assertEquals(0, activeTrips.size, "No active trips after archival")

        // 6. All events should be preserved in event log
        assertEquals(5L, repo.count(), "All 5 events should be in event log")
    }
}
