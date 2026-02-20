package com.travelexpenses.sync

import com.travelexpenses.event.EventReplayEngine
import com.travelexpenses.event.ExpenseEvent
import com.travelexpenses.model.DeviceId
import com.travelexpenses.repository.EventLogRepository
import kotlinx.datetime.Clock

/**
 * Manages backup/restore of the event log archive.
 * v1: local archive export/import only.
 * v2 prep: event merge logic with eventId-based deduplication.
 */
class SyncManager(
    private val eventLogRepo: EventLogRepository,
    private val replayEngine: EventReplayEngine? = null,
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
     * Atomically replaces the event log, then rebuilds materialized state.
     */
    suspend fun importArchive(archive: EventArchive) {
        // Atomically replace the event log (clear + bulk insert without replay)
        eventLogRepo.replaceAllEvents(archive.events)

        // Rebuild materialized state from the imported events
        replayEngine?.rebuildAll(archive.events)
    }

    /**
     * Merge events from another device/archive into the current event log.
     * Deduplicates by eventId to safely handle gaps in sequence numbers.
     * Returns the number of new events added.
     */
    suspend fun mergeEvents(incomingEvents: List<ExpenseEvent>): MergeResult {
        val existingEvents = eventLogRepo.getAllEvents()
        val existingIds = existingEvents.map { it.eventId }.toSet()

        val newEvents = mutableListOf<ExpenseEvent>()
        val skipped = mutableListOf<ExpenseEvent>()

        for (event in incomingEvents) {
            if (event.eventId in existingIds) {
                skipped.add(event)
            } else {
                newEvents.add(event)
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
 * Result of merging events from another source.
 */
data class MergeResult(
    val added: Int,
    val skipped: Int,
    val newEvents: List<ExpenseEvent>,
)
