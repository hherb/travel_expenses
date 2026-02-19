package com.travelexpenses

import com.travelexpenses.db.TravelExpensesDb
import com.travelexpenses.event.ExpenseEvent
import com.travelexpenses.repository.SqlDelightEventLogRepository
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Test 2: Basic event log CRUD operations with coroutines.
 *
 * Validates that:
 * - suspend functions work correctly for DB writes/reads
 * - Events are persisted and retrievable
 * - Ordering is maintained
 * - Filtering by device works
 */
class EventLogBasicTest {

    private val driver = createInMemoryDriver()
    private val db = TravelExpensesDb(driver)
    private val testDispatcher = UnconfinedTestDispatcher()
    private val repo = SqlDelightEventLogRepository(db, queryContext = testDispatcher)

    @BeforeTest
    fun setup() {
        TestHelpers.resetSequence()
    }

    @AfterTest
    fun teardown() {
        driver.close()
    }

    @Test
    fun appendAndRetrieve_singleEvent() = runTest {
        val event = TestHelpers.makeExpenseCreatedEvent()
        repo.append(event)

        val all = repo.getAllEvents()
        assertEquals(1, all.size)
        assertIs<ExpenseEvent.ExpenseCreated>(all[0])
        assertEquals(event.eventId, all[0].eventId)
    }

    @Test
    fun appendMultipleEvents_maintainsOrder() = runTest {
        val events = (1..5).map { i ->
            TestHelpers.makeExpenseCreatedEvent(
                expense = TestHelpers.makeExpense(id = "exp-$i", amount = "${i * 10}.00"),
                sequenceNumber = i.toLong(),
            )
        }

        events.forEach { repo.append(it) }

        val all = repo.getAllEvents()
        assertEquals(5, all.size)
        // Should be ordered by sequence number
        all.forEachIndexed { index, event ->
            assertEquals((index + 1).toLong(), event.sequenceNumber)
        }
    }

    @Test
    fun filterByDevice_returnsOnlyMatchingEvents() = runTest {
        val event1 = TestHelpers.makeExpenseCreatedEvent(
            deviceId = "device-A",
            sequenceNumber = 1,
        )
        val event2 = TestHelpers.makeExpenseCreatedEvent(
            deviceId = "device-B",
            sequenceNumber = 1,
        )
        val event3 = TestHelpers.makeExpenseCreatedEvent(
            deviceId = "device-A",
            sequenceNumber = 2,
        )

        repo.append(event1)
        repo.append(event2)
        repo.append(event3)

        val deviceAEvents = repo.getEventsForDevice("device-A")
        assertEquals(2, deviceAEvents.size)

        val deviceBEvents = repo.getEventsForDevice("device-B")
        assertEquals(1, deviceBEvents.size)
    }

    @Test
    fun count_returnsCorrectValue() = runTest {
        assertEquals(0, repo.count())

        repo.append(TestHelpers.makeExpenseCreatedEvent(sequenceNumber = 1))
        assertEquals(1, repo.count())

        repo.append(TestHelpers.makeExpenseCreatedEvent(sequenceNumber = 2))
        assertEquals(2, repo.count())
    }

    @Test
    fun clear_removesAllEvents() = runTest {
        repo.append(TestHelpers.makeExpenseCreatedEvent(sequenceNumber = 1))
        repo.append(TestHelpers.makeExpenseCreatedEvent(sequenceNumber = 2))
        assertEquals(2, repo.count())

        repo.clear()
        assertEquals(0, repo.count())
    }

    @Test
    fun mixedEventTypes_allPersistCorrectly() = runTest {
        val trip = TestHelpers.makeTrip(id = "trip-1")
        val expense = TestHelpers.makeExpense(id = "exp-1", tripId = "trip-1")

        repo.append(TestHelpers.makeTripCreatedEvent(trip = trip, sequenceNumber = 1))
        repo.append(TestHelpers.makeExpenseCreatedEvent(expense = expense, sequenceNumber = 2))
        repo.append(TestHelpers.makeExpenseDeletedEvent("exp-1", sequenceNumber = 3))
        repo.append(TestHelpers.makeTripArchivedEvent("trip-1", sequenceNumber = 4))

        val all = repo.getAllEvents()
        assertEquals(4, all.size)
        assertIs<ExpenseEvent.TripCreated>(all[0])
        assertIs<ExpenseEvent.ExpenseCreated>(all[1])
        assertIs<ExpenseEvent.ExpenseDeleted>(all[2])
        assertIs<ExpenseEvent.TripArchived>(all[3])
    }
}
