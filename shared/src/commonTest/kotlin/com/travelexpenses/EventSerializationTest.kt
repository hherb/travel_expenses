package com.travelexpenses

import com.travelexpenses.event.ExpenseEvent
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

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
    fun polymorphicDeserialization_preservesType() {
        // Serialize different event types, deserialize as base type, verify discriminator works
        val events: List<ExpenseEvent> = listOf(
            TestHelpers.makeExpenseCreatedEvent(),
            TestHelpers.makeExpenseDeletedEvent("exp-1"),
            TestHelpers.makeTripCreatedEvent(),
            TestHelpers.makeTripArchivedEvent("trip-1"),
        )

        val serialized = events.map { json.encodeToString(ExpenseEvent.serializer(), it) }
        val deserialized = serialized.map { json.decodeFromString(ExpenseEvent.serializer(), it) }

        assertIs<ExpenseEvent.ExpenseCreated>(deserialized[0])
        assertIs<ExpenseEvent.ExpenseDeleted>(deserialized[1])
        assertIs<ExpenseEvent.TripCreated>(deserialized[2])
        assertIs<ExpenseEvent.TripArchived>(deserialized[3])
    }
}
