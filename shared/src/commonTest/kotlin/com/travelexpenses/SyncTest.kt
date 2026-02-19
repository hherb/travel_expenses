package com.travelexpenses

import com.travelexpenses.sync.*
import com.travelexpenses.event.ExpenseEvent
import com.travelexpenses.model.DeviceId
import com.travelexpenses.repository.EventLogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * In-memory EventLogRepository for testing sync without a database.
 */
private class InMemoryEventLogRepository : EventLogRepository {
    private val events = mutableListOf<ExpenseEvent>()
    private val countFlow = MutableStateFlow(0L)

    override suspend fun append(event: ExpenseEvent) {
        events.add(event)
        countFlow.value = events.size.toLong()
    }

    override suspend fun getAllEvents(): List<ExpenseEvent> = events.toList()

    override suspend fun getEventsForDevice(deviceId: DeviceId): List<ExpenseEvent> {
        return events.filter { it.deviceId == deviceId }
    }

    override fun observeEventCount(): Flow<Long> = countFlow

    override fun observeAllEvents(): Flow<List<ExpenseEvent>> {
        return countFlow.map { events.toList() }
    }

    override suspend fun count(): Long = events.size.toLong()

    override suspend fun clear() {
        events.clear()
        countFlow.value = 0
    }
}

class EventArchiveSerializationTest {

    @Test
    fun emptyArchiveRoundTrip() {
        val archive = EventArchive(
            createdAt = "2024-06-15T10:00:00Z",
            deviceId = "device-1",
            eventCount = 0,
            events = emptyList(),
        )
        val json = EventArchiveSerializer.serialize(archive)
        val restored = EventArchiveSerializer.deserialize(json)
        assertEquals(archive.version, restored.version)
        assertEquals(archive.deviceId, restored.deviceId)
        assertEquals(0, restored.events.size)
    }

    @Test
    fun archiveWithEventsRoundTrip() {
        TestHelpers.resetSequence()
        val trip = TestHelpers.makeTrip()
        val expense = TestHelpers.makeExpense()
        val events = listOf(
            TestHelpers.makeTripCreatedEvent(trip),
            TestHelpers.makeExpenseCreatedEvent(expense),
        )
        val archive = EventArchive(
            createdAt = "2024-06-15T10:00:00Z",
            deviceId = "device-1",
            eventCount = events.size,
            events = events,
            imageReferences = listOf("/receipts/img1.jpg"),
        )
        val json = EventArchiveSerializer.serialize(archive)
        val restored = EventArchiveSerializer.deserialize(json)
        assertEquals(2, restored.events.size)
        assertEquals(1, restored.imageReferences.size)
        assertTrue(restored.events[0] is ExpenseEvent.TripCreated)
        assertTrue(restored.events[1] is ExpenseEvent.ExpenseCreated)
    }

    @Test
    fun archiveVersionIsSet() {
        val archive = EventArchive(
            createdAt = "2024-06-15T10:00:00Z",
            deviceId = "device-1",
            eventCount = 0,
            events = emptyList(),
        )
        assertEquals(EventArchive.CURRENT_VERSION, archive.version)
    }
}

class VectorClockTest {

    @Test
    fun buildVectorClockFromEvents() {
        TestHelpers.resetSequence()
        val events = listOf(
            TestHelpers.makeExpenseCreatedEvent(deviceId = "d1", sequenceNumber = 1),
            TestHelpers.makeExpenseCreatedEvent(deviceId = "d1", sequenceNumber = 2),
            TestHelpers.makeExpenseCreatedEvent(deviceId = "d2", sequenceNumber = 1),
            TestHelpers.makeExpenseCreatedEvent(deviceId = "d2", sequenceNumber = 5),
        )
        val clock = buildVectorClock(events)
        assertEquals(2L, clock["d1"])
        assertEquals(5L, clock["d2"])
    }

    @Test
    fun emptyEventsEmptyClock() {
        val clock = buildVectorClock(emptyList())
        assertTrue(clock.isEmpty())
    }

    @Test
    fun isSupersededReturnsTrueForOlderSequence() {
        val clock = mapOf("d1" to 5L)
        val event = TestHelpers.makeExpenseCreatedEvent(deviceId = "d1", sequenceNumber = 3)
        assertTrue(isSuperseded(event, clock))
    }

    @Test
    fun isSupersededReturnsFalseForNewDevice() {
        val clock = mapOf("d1" to 5L)
        val event = TestHelpers.makeExpenseCreatedEvent(deviceId = "d2", sequenceNumber = 1)
        assertTrue(!isSuperseded(event, clock))
    }

    @Test
    fun isSupersededReturnsTrueForEqualSequence() {
        val clock = mapOf("d1" to 5L)
        val event = TestHelpers.makeExpenseCreatedEvent(deviceId = "d1", sequenceNumber = 5)
        assertTrue(isSuperseded(event, clock))
    }

    @Test
    fun isSupersededReturnsFalseForNewerSequence() {
        val clock = mapOf("d1" to 5L)
        val event = TestHelpers.makeExpenseCreatedEvent(deviceId = "d1", sequenceNumber = 6)
        assertTrue(!isSuperseded(event, clock))
    }
}

class MergeLogicTest {

    @Test
    fun mergeSkipsDuplicateEventIds() = runTest {
        TestHelpers.resetSequence()
        val repo = InMemoryEventLogRepository()
        val event1 = TestHelpers.makeExpenseCreatedEvent(
            expense = TestHelpers.makeExpense(id = "e1"),
            deviceId = "d1",
            sequenceNumber = 1,
        )
        repo.append(event1)

        // Incoming contains the same event
        val incoming = listOf(event1)

        val existingEvents = repo.getAllEvents()
        val existingIds = existingEvents.map { it.eventId }.toSet()
        val vectorClock = buildVectorClock(existingEvents)

        val newEvents = incoming.filter { event ->
            event.eventId !in existingIds && !isSuperseded(event, vectorClock)
        }
        assertEquals(0, newEvents.size)
    }

    @Test
    fun mergeAcceptsNewEventsFromNewDevice() = runTest {
        TestHelpers.resetSequence()
        val repo = InMemoryEventLogRepository()
        val event1 = TestHelpers.makeExpenseCreatedEvent(
            expense = TestHelpers.makeExpense(id = "e1"),
            deviceId = "d1",
            sequenceNumber = 1,
        )
        repo.append(event1)

        val event2 = TestHelpers.makeExpenseCreatedEvent(
            expense = TestHelpers.makeExpense(id = "e2"),
            deviceId = "d2",
            sequenceNumber = 1,
        )
        val incoming = listOf(event2)

        val existingEvents = repo.getAllEvents()
        val existingIds = existingEvents.map { it.eventId }.toSet()
        val vectorClock = buildVectorClock(existingEvents)

        val newEvents = incoming.filter { event ->
            event.eventId !in existingIds && !isSuperseded(event, vectorClock)
        }
        assertEquals(1, newEvents.size)
    }

    @Test
    fun mergeSkipsSupersededEvents() = runTest {
        TestHelpers.resetSequence()
        val repo = InMemoryEventLogRepository()

        // Device d1 already has sequence up to 5
        for (i in 1L..5L) {
            repo.append(TestHelpers.makeExpenseCreatedEvent(
                expense = TestHelpers.makeExpense(id = "e-d1-$i"),
                deviceId = "d1",
                sequenceNumber = i,
            ))
        }

        // Incoming has d1 sequence 3 (already seen) and d1 sequence 6 (new)
        val incoming = listOf(
            TestHelpers.makeExpenseCreatedEvent(
                expense = TestHelpers.makeExpense(id = "e-old"),
                deviceId = "d1",
                sequenceNumber = 3,
            ),
            TestHelpers.makeExpenseCreatedEvent(
                expense = TestHelpers.makeExpense(id = "e-new"),
                deviceId = "d1",
                sequenceNumber = 6,
            ),
        )

        val existingEvents = repo.getAllEvents()
        val existingIds = existingEvents.map { it.eventId }.toSet()
        val vectorClock = buildVectorClock(existingEvents)

        val newEvents = incoming.filter { event ->
            event.eventId !in existingIds && !isSuperseded(event, vectorClock)
        }
        assertEquals(1, newEvents.size)
        assertEquals("d1", newEvents[0].deviceId)
        assertEquals(6L, newEvents[0].sequenceNumber)
    }
}
