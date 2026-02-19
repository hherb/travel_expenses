package com.travelexpenses.repository

import com.travelexpenses.event.ExpenseEvent
import com.travelexpenses.model.DeviceId
import kotlinx.coroutines.flow.Flow

/**
 * Repository for the append-only event log.
 *
 * This is the interface under test in Phase 0 -- we need to validate that:
 * 1. Concurrent writes from coroutines are safe
 * 2. Flow-based observation works reliably on iOS
 * 3. Suspend functions behave correctly from Swift callers
 */
interface EventLogRepository {
    /**
     * Append a single event. Thread-safe and suspending.
     * Assigns the next sequence number for this device automatically.
     */
    suspend fun append(event: ExpenseEvent)

    /**
     * Retrieve all events ordered by (deviceId, sequenceNumber).
     */
    suspend fun getAllEvents(): List<ExpenseEvent>

    /**
     * Retrieve events for a specific device, ordered by sequenceNumber.
     */
    suspend fun getEventsForDevice(deviceId: DeviceId): List<ExpenseEvent>

    /**
     * Observe the total event count as a Flow.
     * This validates that SQLDelight Flow integration works on iOS.
     */
    fun observeEventCount(): Flow<Long>

    /**
     * Observe all events as a Flow, emitting the full list on each change.
     */
    fun observeAllEvents(): Flow<List<ExpenseEvent>>

    /** Total number of events stored. */
    suspend fun count(): Long

    /** Delete all events (for testing). */
    suspend fun clear()
}
