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

    override suspend fun replaceAllEvents(events: List<ExpenseEvent>) {
        this.events.clear()
        this.events.addAll(events)
        countFlow.value = this.events.size.toLong()
    }

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

class MergeLogicTest {

    private fun makeSyncManager(repo: InMemoryEventLogRepository): SyncManager {
        return SyncManager(
            eventLogRepo = repo,
            deviceId = "test-device",
        )
    }

    @Test
    fun mergeSkipsDuplicateEventIds() = runTest {
        TestHelpers.resetSequence()
        val repo = InMemoryEventLogRepository()
        val syncManager = makeSyncManager(repo)
        val event1 = TestHelpers.makeExpenseCreatedEvent(
            expense = TestHelpers.makeExpense(id = "e1"),
            deviceId = "d1",
            sequenceNumber = 1,
        )
        repo.append(event1)

        val result = syncManager.mergeEvents(listOf(event1))
        assertEquals(0, result.added)
        assertEquals(1, result.skipped)
    }

    @Test
    fun mergeAcceptsNewEventsFromNewDevice() = runTest {
        TestHelpers.resetSequence()
        val repo = InMemoryEventLogRepository()
        val syncManager = makeSyncManager(repo)
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
        val result = syncManager.mergeEvents(listOf(event2))
        assertEquals(1, result.added)
    }

    @Test
    fun mergeAcceptsEventsWithSequenceGaps() = runTest {
        TestHelpers.resetSequence()
        val repo = InMemoryEventLogRepository()
        val syncManager = makeSyncManager(repo)

        // Device d1 has sequences 1, 2, 3, 5 (gap at 4)
        for (i in listOf(1L, 2L, 3L, 5L)) {
            repo.append(TestHelpers.makeExpenseCreatedEvent(
                expense = TestHelpers.makeExpense(id = "e-d1-$i"),
                deviceId = "d1",
                sequenceNumber = i,
            ))
        }

        // Incoming has the missing event 4 — must NOT be dropped
        val missingEvent = TestHelpers.makeExpenseCreatedEvent(
            expense = TestHelpers.makeExpense(id = "e-d1-4"),
            deviceId = "d1",
            sequenceNumber = 4,
        )
        val result = syncManager.mergeEvents(listOf(missingEvent))
        assertEquals(1, result.added)
        assertEquals(0, result.skipped)
    }

    @Test
    fun mergeSkipsDuplicateButAcceptsNew() = runTest {
        TestHelpers.resetSequence()
        val repo = InMemoryEventLogRepository()
        val syncManager = makeSyncManager(repo)

        val existing = TestHelpers.makeExpenseCreatedEvent(
            expense = TestHelpers.makeExpense(id = "e-old"),
            deviceId = "d1",
            sequenceNumber = 3,
        )
        repo.append(existing)

        val incoming = listOf(
            existing, // duplicate
            TestHelpers.makeExpenseCreatedEvent(
                expense = TestHelpers.makeExpense(id = "e-new"),
                deviceId = "d1",
                sequenceNumber = 6,
            ),
        )

        val result = syncManager.mergeEvents(incoming)
        assertEquals(1, result.added)
        assertEquals(1, result.skipped)
        assertEquals(6L, result.newEvents[0].sequenceNumber)
    }
}
