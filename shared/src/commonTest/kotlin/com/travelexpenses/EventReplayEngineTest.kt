package com.travelexpenses

import com.travelexpenses.db.TravelExpensesDb
import com.travelexpenses.event.EventReplayEngine
import com.travelexpenses.event.ExpenseEvent
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class EventReplayEngineTest {

    private lateinit var db: TravelExpensesDb
    private lateinit var engine: EventReplayEngine
    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        val driver = createInMemoryDriver()
        db = TravelExpensesDb(driver)
        engine = EventReplayEngine(db, testDispatcher)
        TestHelpers.resetSequence()
    }

    // -- Basic replay: TripCreated --

    @Test
    fun replayTripCreated_materializesTripRow() = runTest(testDispatcher) {
        val trip = TestHelpers.makeTrip(id = "trip-1", name = "Tokyo Trip", baseCurrency = "JPY")
        val event = TestHelpers.makeTripCreatedEvent(trip = trip)

        engine.apply(event)

        val row = db.materializedStateQueries.selectTripById("trip-1").executeAsOneOrNull()
        assertNotNull(row)
        assertEquals("Tokyo Trip", row.name)
        assertEquals("JPY", row.base_currency)
        assertEquals(0L, row.is_archived)
    }

    // -- Basic replay: ExpenseCreated --

    @Test
    fun replayExpenseCreated_materializesExpenseRow() = runTest(testDispatcher) {
        val expense = TestHelpers.makeExpense(
            id = "exp-1",
            tripId = "trip-1",
            amount = "42.50",
            currency = "EUR",
            categoryId = "food",
            vendor = "Cafe Berlin",
        )
        val event = TestHelpers.makeExpenseCreatedEvent(expense = expense)

        engine.apply(event)

        val row = db.materializedStateQueries.selectExpenseById("exp-1").executeAsOneOrNull()
        assertNotNull(row)
        assertEquals("42.50", row.amount)
        assertEquals("EUR", row.currency)
        assertEquals("food", row.category_id)
        assertEquals("Cafe Berlin", row.vendor)
        assertEquals(0L, row.is_deleted)
    }

    @Test
    fun replayExpenseCreated_insertsExpenseTagJunctions() = runTest(testDispatcher) {
        // Create tags first so the JOIN works
        engine.apply(TestHelpers.makeTagCreatedEvent(
            tag = TestHelpers.makeTag(id = "tag-a", label = "business")
        ))
        engine.apply(TestHelpers.makeTagCreatedEvent(
            tag = TestHelpers.makeTag(id = "tag-b", label = "reimbursable")
        ))

        val expense = TestHelpers.makeExpense(id = "exp-1").copy(
            tags = listOf("tag-a", "tag-b"),
        )
        engine.apply(TestHelpers.makeExpenseCreatedEvent(expense = expense))

        val tags = db.materializedStateQueries.selectTagsForExpense("exp-1").executeAsList()
        assertEquals(2, tags.size)
        assertEquals(setOf("tag-a", "tag-b"), tags.map { it.id }.toSet())
    }

    // -- Basic replay: CategoryCreated --

    @Test
    fun replayCategoryCreated_materializesCategoryRow() = runTest(testDispatcher) {
        val category = TestHelpers.makeCategory(
            id = "cat-transport",
            name = "Transport",
            icon = "plane",
            isDefault = true,
        )
        val event = TestHelpers.makeCategoryCreatedEvent(category = category)

        engine.apply(event)

        val row = db.materializedStateQueries.selectCategoryById("cat-transport").executeAsOneOrNull()
        assertNotNull(row)
        assertEquals("Transport", row.name)
        assertEquals("plane", row.icon)
        assertEquals(1L, row.is_default)
    }

    // -- Basic replay: TagCreated --

    @Test
    fun replayTagCreated_materializesTagRow() = runTest(testDispatcher) {
        val tag = TestHelpers.makeTag(id = "tag-biz", label = "business")
        val event = TestHelpers.makeTagCreatedEvent(tag = tag)

        engine.apply(event)

        val row = db.materializedStateQueries.selectTagById("tag-biz").executeAsOneOrNull()
        assertNotNull(row)
        assertEquals("business", row.label)
    }

    // -- Update events --

    @Test
    fun replayExpenseUpdated_updatesOnlyChangedFields() = runTest(testDispatcher) {
        // Create initial expense
        val expense = TestHelpers.makeExpense(
            id = "exp-1",
            amount = "10.00",
            currency = "USD",
            categoryId = "food",
            vendor = "Original Vendor",
        )
        engine.apply(TestHelpers.makeExpenseCreatedEvent(expense = expense))

        // Update only the amount
        engine.apply(TestHelpers.makeExpenseUpdatedEvent(
            expenseId = "exp-1",
            amount = "99.99",
        ))

        val row = db.materializedStateQueries.selectExpenseById("exp-1").executeAsOneOrNull()
        assertNotNull(row)
        assertEquals("99.99", row.amount, "Amount should be updated")
        assertEquals("USD", row.currency, "Currency should remain unchanged")
        assertEquals("food", row.category_id, "Category should remain unchanged")
        assertEquals("Original Vendor", row.vendor, "Vendor should remain unchanged")
    }

    @Test
    fun replayExpenseUpdated_replacesTagsList() = runTest(testDispatcher) {
        // Create tags
        engine.apply(TestHelpers.makeTagCreatedEvent(tag = TestHelpers.makeTag(id = "tag-a", label = "a")))
        engine.apply(TestHelpers.makeTagCreatedEvent(tag = TestHelpers.makeTag(id = "tag-b", label = "b")))
        engine.apply(TestHelpers.makeTagCreatedEvent(tag = TestHelpers.makeTag(id = "tag-c", label = "c")))

        // Create expense with tags a, b
        val expense = TestHelpers.makeExpense(id = "exp-1").copy(tags = listOf("tag-a", "tag-b"))
        engine.apply(TestHelpers.makeExpenseCreatedEvent(expense = expense))

        // Update tags to only c
        engine.apply(TestHelpers.makeExpenseUpdatedEvent(
            expenseId = "exp-1",
            tags = listOf("tag-c"),
        ))

        val tags = db.materializedStateQueries.selectTagsForExpense("exp-1").executeAsList()
        assertEquals(1, tags.size)
        assertEquals("tag-c", tags[0].id)
    }

    @Test
    fun replayTripUpdated_updatesOnlyChangedFields() = runTest(testDispatcher) {
        val trip = TestHelpers.makeTrip(id = "trip-1", name = "Original", baseCurrency = "USD")
        engine.apply(TestHelpers.makeTripCreatedEvent(trip = trip))

        engine.apply(TestHelpers.makeTripUpdatedEvent(
            tripId = "trip-1",
            name = "Renamed Trip",
        ))

        val row = db.materializedStateQueries.selectTripById("trip-1").executeAsOneOrNull()
        assertNotNull(row)
        assertEquals("Renamed Trip", row.name, "Name should be updated")
        assertEquals("USD", row.base_currency, "BaseCurrency should remain unchanged")
    }

    @Test
    fun replayCategoryUpdated_updatesOnlyChangedFields() = runTest(testDispatcher) {
        val category = TestHelpers.makeCategory(id = "cat-1", name = "Food", icon = "fork")
        engine.apply(TestHelpers.makeCategoryCreatedEvent(category = category))

        engine.apply(TestHelpers.makeCategoryUpdatedEvent(
            categoryId = "cat-1",
            icon = "utensils",
        ))

        val row = db.materializedStateQueries.selectCategoryById("cat-1").executeAsOneOrNull()
        assertNotNull(row)
        assertEquals("Food", row.name, "Name should remain unchanged")
        assertEquals("utensils", row.icon, "Icon should be updated")
    }

    // -- Delete/archive --

    @Test
    fun replayExpenseDeleted_setsIsDeletedFlag() = runTest(testDispatcher) {
        val expense = TestHelpers.makeExpense(id = "exp-1")
        engine.apply(TestHelpers.makeExpenseCreatedEvent(expense = expense))

        engine.apply(TestHelpers.makeExpenseDeletedEvent(expenseId = "exp-1"))

        // selectExpenseById filters out is_deleted=1
        val row = db.materializedStateQueries.selectExpenseById("exp-1").executeAsOneOrNull()
        assertNull(row, "Deleted expense should not be returned by selectExpenseById")
    }

    @Test
    fun replayTripArchived_setsIsArchivedFlag() = runTest(testDispatcher) {
        val trip = TestHelpers.makeTrip(id = "trip-1")
        engine.apply(TestHelpers.makeTripCreatedEvent(trip = trip))

        engine.apply(TestHelpers.makeTripArchivedEvent(tripId = "trip-1"))

        val activeTrips = db.materializedStateQueries.selectAllTrips().executeAsList()
        assertEquals(0, activeTrips.size, "Archived trip should not appear in active trips")

        val archivedTrips = db.materializedStateQueries.selectArchivedTrips().executeAsList()
        assertEquals(1, archivedTrips.size, "Archived trip should appear in archived trips")
    }

    // -- Receipt events --

    @Test
    fun replayReceiptAttached_createsReceiptImageRow() = runTest(testDispatcher) {
        engine.apply(TestHelpers.makeReceiptAttachedEvent(expenseId = "exp-1", imageId = "img-1"))

        val receipts = db.materializedStateQueries.selectReceiptsForExpense("exp-1").executeAsList()
        assertEquals(1, receipts.size)
        assertEquals("img-1", receipts[0].id)
        assertEquals("exp-1", receipts[0].expense_id)
    }

    @Test
    fun replayReceiptDetached_removesReceiptImageRow() = runTest(testDispatcher) {
        engine.apply(TestHelpers.makeReceiptAttachedEvent(expenseId = "exp-1", imageId = "img-1"))
        engine.apply(TestHelpers.makeReceiptDetachedEvent(expenseId = "exp-1", imageId = "img-1"))

        val receipts = db.materializedStateQueries.selectReceiptsForExpense("exp-1").executeAsList()
        assertEquals(0, receipts.size)
    }

    // -- Full rebuild --

    @Test
    fun rebuildAll_producesIdenticalStateToIncrementalApply() = runTest(testDispatcher) {
        // Build a sequence of events
        val events = listOf(
            TestHelpers.makeTripCreatedEvent(
                trip = TestHelpers.makeTrip(id = "trip-1", name = "Japan", baseCurrency = "JPY")
            ),
            TestHelpers.makeCategoryCreatedEvent(
                category = TestHelpers.makeCategory(id = "cat-food", name = "Food", icon = "fork")
            ),
            TestHelpers.makeTagCreatedEvent(
                tag = TestHelpers.makeTag(id = "tag-biz", label = "business")
            ),
            TestHelpers.makeExpenseCreatedEvent(
                expense = TestHelpers.makeExpense(id = "exp-1", tripId = "trip-1", amount = "1000", currency = "JPY").copy(
                    tags = listOf("tag-biz"),
                )
            ),
            TestHelpers.makeExpenseUpdatedEvent(expenseId = "exp-1", amount = "1500"),
            TestHelpers.makeTripUpdatedEvent(tripId = "trip-1", name = "Japan 2026"),
        )

        // Apply incrementally
        events.forEach { engine.apply(it) }

        // Snapshot the incremental state
        val incTrip = db.materializedStateQueries.selectTripById("trip-1").executeAsOneOrNull()
        val incExpense = db.materializedStateQueries.selectExpenseById("exp-1").executeAsOneOrNull()
        val incCategory = db.materializedStateQueries.selectCategoryById("cat-food").executeAsOneOrNull()

        // Now rebuild from scratch
        engine.rebuildAll(events)

        // Snapshot the rebuilt state
        val rebTrip = db.materializedStateQueries.selectTripById("trip-1").executeAsOneOrNull()
        val rebExpense = db.materializedStateQueries.selectExpenseById("exp-1").executeAsOneOrNull()
        val rebCategory = db.materializedStateQueries.selectCategoryById("cat-food").executeAsOneOrNull()

        // Compare
        assertNotNull(incTrip)
        assertNotNull(rebTrip)
        assertEquals(incTrip.name, rebTrip.name)
        assertEquals(incTrip.base_currency, rebTrip.base_currency)

        assertNotNull(incExpense)
        assertNotNull(rebExpense)
        assertEquals(incExpense.amount, rebExpense.amount)
        assertEquals(incExpense.currency, rebExpense.currency)

        assertNotNull(incCategory)
        assertNotNull(rebCategory)
        assertEquals(incCategory.name, rebCategory.name)
    }

    @Test
    fun rebuildAll_clearsPreviousState() = runTest(testDispatcher) {
        // Create some initial state
        engine.apply(TestHelpers.makeTripCreatedEvent(
            trip = TestHelpers.makeTrip(id = "trip-old", name = "Old Trip")
        ))

        // Rebuild with a completely different set of events
        engine.rebuildAll(listOf(
            TestHelpers.makeTripCreatedEvent(
                trip = TestHelpers.makeTrip(id = "trip-new", name = "New Trip")
            ),
        ))

        val oldTrip = db.materializedStateQueries.selectTripById("trip-old").executeAsOneOrNull()
        assertNull(oldTrip, "Old trip should be cleared after rebuild")

        val newTrip = db.materializedStateQueries.selectTripById("trip-new").executeAsOneOrNull()
        assertNotNull(newTrip, "New trip should exist after rebuild")
    }

    @Test
    fun rebuildAll_emptyEventList_clearsAllState() = runTest(testDispatcher) {
        // Create some state
        engine.apply(TestHelpers.makeTripCreatedEvent(trip = TestHelpers.makeTrip(id = "trip-1")))
        engine.apply(TestHelpers.makeExpenseCreatedEvent(expense = TestHelpers.makeExpense(id = "exp-1")))

        // Rebuild with empty list
        engine.rebuildAll(emptyList())

        val trips = db.materializedStateQueries.selectAllTrips().executeAsList()
        val expenses = db.materializedStateQueries.selectAllActiveExpenses().executeAsList()
        assertEquals(0, trips.size)
        assertEquals(0, expenses.size)
    }

    // -- Ordering --

    @Test
    fun eventsAppliedInOrder_producesCorrectFinalState() = runTest(testDispatcher) {
        // Create -> Update -> Update -> Delete
        engine.apply(TestHelpers.makeExpenseCreatedEvent(
            expense = TestHelpers.makeExpense(id = "exp-1", amount = "10.00")
        ))
        engine.apply(TestHelpers.makeExpenseUpdatedEvent(expenseId = "exp-1", amount = "20.00"))
        engine.apply(TestHelpers.makeExpenseUpdatedEvent(expenseId = "exp-1", amount = "30.00"))
        engine.apply(TestHelpers.makeExpenseDeletedEvent(expenseId = "exp-1"))

        val row = db.materializedStateQueries.selectExpenseById("exp-1").executeAsOneOrNull()
        assertNull(row, "Expense should be deleted after sequence of events")
    }
}
