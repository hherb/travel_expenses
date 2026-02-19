package com.travelexpenses.sync

import com.travelexpenses.event.EventReplayEngine
import com.travelexpenses.event.ExpenseEvent
import com.travelexpenses.model.DeviceId
import com.travelexpenses.repository.EventLogRepository
import kotlinx.datetime.Clock

/**
 * Manages backup/restore of the event log archive.
 * v1: local archive export/import only.
 * v2 prep: event merge logic via vector clocks.
 */
class SyncManager(
    private val eventLogRepo: EventLogRepository,
    private val replayEngine: EventReplayEngine,
    private val deviceId: DeviceId,
) {

    /**
     * Export the full event log as an archive.
     * Collects all events and referenced image paths.
     */
    suspend fun exportArchive(): EventArchive {
        val events = eventLogRepo.getAllEvents()
        val imageRefs = events.flatMap { collectImageReferences(it) }.distinct()

        return EventArchive(
            createdAt = Clock.System.now().toString(),
            deviceId = deviceId,
            eventCount = events.size,
            events = events,
            imageReferences = imageRefs,
        )
    }

    /**
     * Import an archive to restore state on this device.
     * Clears existing data and replays all events from the archive.
     */
    suspend fun importArchive(archive: EventArchive) {
        // Clear existing event log
        eventLogRepo.clear()

        // Rebuild materialized state from archive events
        replayEngine.rebuildAll(archive.events)

        // Re-insert events into the event log (for future exports)
        for (event in archive.events) {
            eventLogRepo.append(event)
        }
    }

    /**
     * Merge events from another device/archive into the current event log.
     * Uses vector clock logic to avoid duplicate events.
     * Returns the number of new events added.
     */
    suspend fun mergeEvents(incomingEvents: List<ExpenseEvent>): MergeResult {
        val existingEvents = eventLogRepo.getAllEvents()
        val existingIds = existingEvents.map { it.eventId }.toSet()

        // Vector clock: track (deviceId, maxSequenceNumber) for existing events
        val vectorClock = buildVectorClock(existingEvents)

        val newEvents = mutableListOf<ExpenseEvent>()
        val skipped = mutableListOf<ExpenseEvent>()

        for (event in incomingEvents) {
            when {
                // Skip exact duplicate by eventId
                event.eventId in existingIds -> skipped.add(event)
                // Skip if we already have a higher sequence for this device
                isSuperseded(event, vectorClock) -> skipped.add(event)
                else -> newEvents.add(event)
            }
        }

        // Apply new events in sequence order
        val sorted = newEvents.sortedWith(compareBy({ it.deviceId }, { it.sequenceNumber }))
        for (event in sorted) {
            eventLogRepo.append(event)
        }

        return MergeResult(
            added = sorted.size,
            skipped = skipped.size,
            newEvents = sorted,
        )
    }

    private fun collectImageReferences(event: ExpenseEvent): List<String> {
        return when (event) {
            is ExpenseEvent.ReceiptAttached -> listOfNotNull(event.filePath, event.thumbnailPath)
            else -> emptyList()
        }
    }
}

/**
 * Build a vector clock from a list of events.
 * Maps deviceId -> maximum sequence number seen for that device.
 */
internal fun buildVectorClock(events: List<ExpenseEvent>): Map<DeviceId, Long> {
    val clock = mutableMapOf<DeviceId, Long>()
    for (event in events) {
        val current = clock[event.deviceId] ?: -1L
        if (event.sequenceNumber > current) {
            clock[event.deviceId] = event.sequenceNumber
        }
    }
    return clock
}

/**
 * Check if an event is superseded by the vector clock
 * (i.e., we already have an equal or higher sequence for this device).
 */
internal fun isSuperseded(event: ExpenseEvent, vectorClock: Map<DeviceId, Long>): Boolean {
    val maxSeq = vectorClock[event.deviceId] ?: return false
    return event.sequenceNumber <= maxSeq
}

/**
 * Result of merging events from another source.
 */
data class MergeResult(
    val added: Int,
    val skipped: Int,
    val newEvents: List<ExpenseEvent>,
)
