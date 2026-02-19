package com.travelexpenses

import com.travelexpenses.event.ExpenseEvent
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * Test 1: Event serialization round-trip.
 *
 * Validates that kotlinx-serialization sealed class polymorphism works
 * correctly on all platforms. This is a prerequisite for the event log
 * (events are stored as JSON in SQLite).
 */
class EventSerializationTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun expenseCreatedEvent_roundTrips() {
        val event = TestHelpers.makeExpenseCreatedEvent()
        val serialized = json.encodeToString(ExpenseEvent.serializer(), event)
        val deserialized = json.decodeFromString(ExpenseEvent.serializer(), serialized)

        assertIs<ExpenseEvent.ExpenseCreated>(deserialized)
        assertEquals(event.eventId, deserialized.eventId)
        assertEquals(event.expense.amount, deserialized.expense.amount)
        assertEquals(event.expense.currency, deserialized.expense.currency)
        assertEquals(event.expense.vendor, deserialized.expense.vendor)
        assertEquals(event.expense.date, deserialized.expense.date)
    }

    @Test
    fun expenseDeletedEvent_roundTrips() {
        val event = TestHelpers.makeExpenseDeletedEvent("exp-123")
        val serialized = json.encodeToString(ExpenseEvent.serializer(), event)
        val deserialized = json.decodeFromString(ExpenseEvent.serializer(), serialized)

        assertIs<ExpenseEvent.ExpenseDeleted>(deserialized)
        assertEquals("exp-123", deserialized.expenseId)
    }

    @Test
    fun tripCreatedEvent_roundTrips() {
        val event = TestHelpers.makeTripCreatedEvent()
        val serialized = json.encodeToString(ExpenseEvent.serializer(), event)
        val deserialized = json.decodeFromString(ExpenseEvent.serializer(), serialized)

        assertIs<ExpenseEvent.TripCreated>(deserialized)
        assertEquals(event.trip.name, deserialized.trip.name)
    }

    @Test
    fun tripArchivedEvent_roundTrips() {
        val event = TestHelpers.makeTripArchivedEvent("trip-42")
        val serialized = json.encodeToString(ExpenseEvent.serializer(), event)
        val deserialized = json.decodeFromString(ExpenseEvent.serializer(), serialized)

        assertIs<ExpenseEvent.TripArchived>(deserialized)
        assertEquals("trip-42", deserialized.tripId)
    }

    @Test
    fun expenseUpdatedEvent_roundTrips() {
        val event = TestHelpers.makeExpenseUpdatedEvent(
            expenseId = "exp-42",
            amount = "99.99",
            vendor = "New Vendor",
        )
        val serialized = json.encodeToString(ExpenseEvent.serializer(), event)
        val deserialized = json.decodeFromString(ExpenseEvent.serializer(), serialized)

        assertIs<ExpenseEvent.ExpenseUpdated>(deserialized)
        assertEquals("exp-42", deserialized.expenseId)
        assertEquals("99.99", deserialized.amount)
        assertEquals("New Vendor", deserialized.vendor)
        assertNull(deserialized.currency, "Null fields should remain null (no change)")
        assertNull(deserialized.categoryId)
        assertNull(deserialized.tags)
    }

    @Test
    fun tripUpdatedEvent_roundTrips() {
        val event = TestHelpers.makeTripUpdatedEvent(
            tripId = "trip-7",
            name = "Updated Trip Name",
            startDate = LocalDate(2026, 6, 1),
        )
        val serialized = json.encodeToString(ExpenseEvent.serializer(), event)
        val deserialized = json.decodeFromString(ExpenseEvent.serializer(), serialized)

        assertIs<ExpenseEvent.TripUpdated>(deserialized)
        assertEquals("trip-7", deserialized.tripId)
        assertEquals("Updated Trip Name", deserialized.name)
        assertEquals(LocalDate(2026, 6, 1), deserialized.startDate)
        assertNull(deserialized.destination)
        assertNull(deserialized.baseCurrency)
    }

    @Test
    fun categoryCreatedEvent_roundTrips() {
        val category = TestHelpers.makeCategory(id = "cat-1", name = "Transport", icon = "plane")
        val event = TestHelpers.makeCategoryCreatedEvent(category = category)
        val serialized = json.encodeToString(ExpenseEvent.serializer(), event)
        val deserialized = json.decodeFromString(ExpenseEvent.serializer(), serialized)

        assertIs<ExpenseEvent.CategoryCreated>(deserialized)
        assertEquals("cat-1", deserialized.category.id)
        assertEquals("Transport", deserialized.category.name)
        assertEquals("plane", deserialized.category.icon)
    }

    @Test
    fun categoryUpdatedEvent_roundTrips() {
        val event = TestHelpers.makeCategoryUpdatedEvent(
            categoryId = "cat-1",
            name = "Renamed Category",
        )
        val serialized = json.encodeToString(ExpenseEvent.serializer(), event)
        val deserialized = json.decodeFromString(ExpenseEvent.serializer(), serialized)

        assertIs<ExpenseEvent.CategoryUpdated>(deserialized)
        assertEquals("cat-1", deserialized.categoryId)
        assertEquals("Renamed Category", deserialized.name)
        assertNull(deserialized.icon)
    }

    @Test
    fun tagCreatedEvent_roundTrips() {
        val tag = TestHelpers.makeTag(id = "tag-1", label = "business")
        val event = TestHelpers.makeTagCreatedEvent(tag = tag)
        val serialized = json.encodeToString(ExpenseEvent.serializer(), event)
        val deserialized = json.decodeFromString(ExpenseEvent.serializer(), serialized)

        assertIs<ExpenseEvent.TagCreated>(deserialized)
        assertEquals("tag-1", deserialized.tag.id)
        assertEquals("business", deserialized.tag.label)
    }

    @Test
    fun receiptAttachedEvent_roundTrips() {
        val event = TestHelpers.makeReceiptAttachedEvent(expenseId = "exp-5", imageId = "img-10")
        val serialized = json.encodeToString(ExpenseEvent.serializer(), event)
        val deserialized = json.decodeFromString(ExpenseEvent.serializer(), serialized)

        assertIs<ExpenseEvent.ReceiptAttached>(deserialized)
        assertEquals("exp-5", deserialized.expenseId)
        assertEquals("img-10", deserialized.imageId)
    }

    @Test
    fun receiptDetachedEvent_roundTrips() {
        val event = TestHelpers.makeReceiptDetachedEvent(expenseId = "exp-5", imageId = "img-10")
        val serialized = json.encodeToString(ExpenseEvent.serializer(), event)
        val deserialized = json.decodeFromString(ExpenseEvent.serializer(), serialized)

        assertIs<ExpenseEvent.ReceiptDetached>(deserialized)
        assertEquals("exp-5", deserialized.expenseId)
        assertEquals("img-10", deserialized.imageId)
    }

    @Test
    fun polymorphicDeserialization_preservesAllElevenTypes() {
        val events: List<ExpenseEvent> = listOf(
            TestHelpers.makeExpenseCreatedEvent(),
            TestHelpers.makeExpenseUpdatedEvent(expenseId = "exp-1", amount = "10.00"),
            TestHelpers.makeExpenseDeletedEvent("exp-1"),
            TestHelpers.makeTripCreatedEvent(),
            TestHelpers.makeTripUpdatedEvent(tripId = "trip-1", name = "Updated"),
            TestHelpers.makeTripArchivedEvent("trip-1"),
            TestHelpers.makeCategoryCreatedEvent(),
            TestHelpers.makeCategoryUpdatedEvent(categoryId = "cat-1", name = "New"),
            TestHelpers.makeTagCreatedEvent(),
            TestHelpers.makeReceiptAttachedEvent(),
            TestHelpers.makeReceiptDetachedEvent(),
        )

        val serialized = events.map { json.encodeToString(ExpenseEvent.serializer(), it) }
        val deserialized = serialized.map { json.decodeFromString(ExpenseEvent.serializer(), it) }

        assertIs<ExpenseEvent.ExpenseCreated>(deserialized[0])
        assertIs<ExpenseEvent.ExpenseUpdated>(deserialized[1])
        assertIs<ExpenseEvent.ExpenseDeleted>(deserialized[2])
        assertIs<ExpenseEvent.TripCreated>(deserialized[3])
        assertIs<ExpenseEvent.TripUpdated>(deserialized[4])
        assertIs<ExpenseEvent.TripArchived>(deserialized[5])
        assertIs<ExpenseEvent.CategoryCreated>(deserialized[6])
        assertIs<ExpenseEvent.CategoryUpdated>(deserialized[7])
        assertIs<ExpenseEvent.TagCreated>(deserialized[8])
        assertIs<ExpenseEvent.ReceiptAttached>(deserialized[9])
        assertIs<ExpenseEvent.ReceiptDetached>(deserialized[10])
    }
}
