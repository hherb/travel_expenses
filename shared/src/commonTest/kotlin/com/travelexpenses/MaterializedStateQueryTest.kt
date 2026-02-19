package com.travelexpenses

import com.travelexpenses.db.TravelExpensesDb
import com.travelexpenses.event.EventReplayEngine
import com.travelexpenses.model.*
import com.travelexpenses.repository.SqlDelightCategoryRepository
import com.travelexpenses.repository.SqlDelightExpenseRepository
import com.travelexpenses.repository.SqlDelightTripRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests read-side repositories that query materialized state.
 * Uses the replay engine to set up state, then verifies repository queries.
 */
class MaterializedStateQueryTest {

    private lateinit var db: TravelExpensesDb
    private lateinit var engine: EventReplayEngine
    private lateinit var expenseRepo: SqlDelightExpenseRepository
    private lateinit var tripRepo: SqlDelightTripRepository
    private lateinit var categoryRepo: SqlDelightCategoryRepository
    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        val driver = createInMemoryDriver()
        db = TravelExpensesDb(driver)
        engine = EventReplayEngine(db, testDispatcher)
        expenseRepo = SqlDelightExpenseRepository(db, testDispatcher)
        tripRepo = SqlDelightTripRepository(db, testDispatcher)
        categoryRepo = SqlDelightCategoryRepository(db, testDispatcher)
        TestHelpers.resetSequence()
    }

    @Test
    fun selectExpensesForTrip_returnsOnlyMatchingTrip() = runTest(testDispatcher) {
        // Create two trips with expenses
        engine.apply(TestHelpers.makeTripCreatedEvent(trip = TestHelpers.makeTrip(id = "trip-1")))
        engine.apply(TestHelpers.makeTripCreatedEvent(trip = TestHelpers.makeTrip(id = "trip-2")))
        engine.apply(TestHelpers.makeExpenseCreatedEvent(
            expense = TestHelpers.makeExpense(id = "exp-1", tripId = "trip-1")
        ))
        engine.apply(TestHelpers.makeExpenseCreatedEvent(
            expense = TestHelpers.makeExpense(id = "exp-2", tripId = "trip-1")
        ))
        engine.apply(TestHelpers.makeExpenseCreatedEvent(
            expense = TestHelpers.makeExpense(id = "exp-3", tripId = "trip-2")
        ))

        val trip1Expenses = expenseRepo.getExpensesForTrip("trip-1")
        assertEquals(2, trip1Expenses.size)
        assertTrue(trip1Expenses.all { it.tripId == "trip-1" })

        val trip2Expenses = expenseRepo.getExpensesForTrip("trip-2")
        assertEquals(1, trip2Expenses.size)
        assertEquals("trip-2", trip2Expenses[0].tripId)
    }

    @Test
    fun selectExpensesForTrip_excludesSoftDeleted() = runTest(testDispatcher) {
        engine.apply(TestHelpers.makeExpenseCreatedEvent(
            expense = TestHelpers.makeExpense(id = "exp-1", tripId = "trip-1")
        ))
        engine.apply(TestHelpers.makeExpenseCreatedEvent(
            expense = TestHelpers.makeExpense(id = "exp-2", tripId = "trip-1")
        ))
        engine.apply(TestHelpers.makeExpenseDeletedEvent(expenseId = "exp-1"))

        val expenses = expenseRepo.getExpensesForTrip("trip-1")
        assertEquals(1, expenses.size)
        assertEquals("exp-2", expenses[0].id)
    }

    @Test
    fun selectActiveTrips_excludesArchived() = runTest(testDispatcher) {
        engine.apply(TestHelpers.makeTripCreatedEvent(trip = TestHelpers.makeTrip(id = "trip-1", name = "Active")))
        engine.apply(TestHelpers.makeTripCreatedEvent(trip = TestHelpers.makeTrip(id = "trip-2", name = "Archived")))
        engine.apply(TestHelpers.makeTripArchivedEvent(tripId = "trip-2"))

        val activeTrips = tripRepo.getActiveTrips()
        assertEquals(1, activeTrips.size)
        assertEquals("Active", activeTrips[0].name)

        val archivedTrips = tripRepo.getArchivedTrips()
        assertEquals(1, archivedTrips.size)
        assertEquals("Archived", archivedTrips[0].name)
    }

    @Test
    fun selectTagsForExpense_returnsCorrectJunction() = runTest(testDispatcher) {
        engine.apply(TestHelpers.makeTagCreatedEvent(tag = TestHelpers.makeTag(id = "tag-a", label = "alpha")))
        engine.apply(TestHelpers.makeTagCreatedEvent(tag = TestHelpers.makeTag(id = "tag-b", label = "beta")))
        engine.apply(TestHelpers.makeTagCreatedEvent(tag = TestHelpers.makeTag(id = "tag-c", label = "gamma")))

        engine.apply(TestHelpers.makeExpenseCreatedEvent(
            expense = TestHelpers.makeExpense(id = "exp-1").copy(tags = listOf("tag-a", "tag-b"))
        ))

        val tags = expenseRepo.getTagsForExpense("exp-1")
        assertEquals(2, tags.size)
        assertEquals(setOf("tag-a", "tag-b"), tags.map { it.id }.toSet())
    }

    @Test
    fun selectAllCategories_orderedByName() = runTest(testDispatcher) {
        engine.apply(TestHelpers.makeCategoryCreatedEvent(
            category = TestHelpers.makeCategory(id = "cat-z", name = "Zebra", icon = "z")
        ))
        engine.apply(TestHelpers.makeCategoryCreatedEvent(
            category = TestHelpers.makeCategory(id = "cat-a", name = "Alpha", icon = "a")
        ))
        engine.apply(TestHelpers.makeCategoryCreatedEvent(
            category = TestHelpers.makeCategory(id = "cat-m", name = "Middle", icon = "m")
        ))

        val categories = categoryRepo.getAllCategories()
        assertEquals(3, categories.size)
        assertEquals("Alpha", categories[0].name)
        assertEquals("Middle", categories[1].name)
        assertEquals("Zebra", categories[2].name)
    }

    @Test
    fun getExpense_populatesTagsAndReceiptImageIds() = runTest(testDispatcher) {
        engine.apply(TestHelpers.makeTagCreatedEvent(tag = TestHelpers.makeTag(id = "tag-x", label = "x")))
        engine.apply(TestHelpers.makeTagCreatedEvent(tag = TestHelpers.makeTag(id = "tag-y", label = "y")))
        engine.apply(TestHelpers.makeExpenseCreatedEvent(
            expense = TestHelpers.makeExpense(id = "exp-1").copy(tags = listOf("tag-x", "tag-y"))
        ))
        engine.apply(TestHelpers.makeReceiptAttachedEvent(expenseId = "exp-1", imageId = "img-1"))
        engine.apply(TestHelpers.makeReceiptAttachedEvent(expenseId = "exp-1", imageId = "img-2"))

        val expense = expenseRepo.getExpense("exp-1")
        assertNotNull(expense)
        assertEquals(setOf("tag-x", "tag-y"), expense.tags.toSet())
        assertEquals(setOf("img-1", "img-2"), expense.receiptImageIds.toSet())
    }

    @Test
    fun getExpense_returnsNullForNonExistent() = runTest(testDispatcher) {
        val expense = expenseRepo.getExpense("non-existent")
        assertNull(expense)
    }

    @Test
    fun getTrip_returnsCorrectTrip() = runTest(testDispatcher) {
        engine.apply(TestHelpers.makeTripCreatedEvent(
            trip = TestHelpers.makeTrip(id = "trip-1", name = "Tokyo Trip", baseCurrency = "JPY")
        ))

        val trip = tripRepo.getTrip("trip-1")
        assertNotNull(trip)
        assertEquals("Tokyo Trip", trip.name)
        assertEquals("JPY", trip.baseCurrency)
    }

    // -- Flow observation tests --

    @Test
    fun observeActiveTrips_emitsCurrentState() = runTest(testDispatcher) {
        engine.apply(TestHelpers.makeTripCreatedEvent(trip = TestHelpers.makeTrip(id = "trip-1", name = "Trip A")))
        engine.apply(TestHelpers.makeTripCreatedEvent(trip = TestHelpers.makeTrip(id = "trip-2", name = "Trip B")))

        val trips = tripRepo.observeActiveTrips().first()
        assertEquals(2, trips.size)
    }

    @Test
    fun observeExpensesForTrip_emitsCurrentState() = runTest(testDispatcher) {
        engine.apply(TestHelpers.makeExpenseCreatedEvent(
            expense = TestHelpers.makeExpense(id = "exp-1", tripId = "trip-1")
        ))
        engine.apply(TestHelpers.makeExpenseCreatedEvent(
            expense = TestHelpers.makeExpense(id = "exp-2", tripId = "trip-1")
        ))

        val expenses = expenseRepo.observeExpensesForTrip("trip-1").first()
        assertEquals(2, expenses.size)
    }

    @Test
    fun observeAllCategories_emitsCurrentState() = runTest(testDispatcher) {
        engine.apply(TestHelpers.makeCategoryCreatedEvent(
            category = TestHelpers.makeCategory(id = "cat-1", name = "Food", icon = "fork")
        ))

        val categories = categoryRepo.observeAllCategories().first()
        assertEquals(1, categories.size)
        assertEquals("Food", categories[0].name)
    }
}
