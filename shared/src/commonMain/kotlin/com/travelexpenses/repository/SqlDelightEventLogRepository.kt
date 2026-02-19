package com.travelexpenses.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import com.travelexpenses.db.TravelExpensesDb
import com.travelexpenses.event.ExpenseEvent
import com.travelexpenses.model.DeviceId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * SQLDelight-backed implementation of [EventLogRepository].
 *
 * Key design decisions validated by Phase 0 tests:
 * - Uses a Mutex for sequence number assignment (not DB-level locking)
 * - Queries dispatched to Dispatchers.Default (background thread)
 * - Flow observation uses SQLDelight's built-in query listener mechanism
 */
class SqlDelightEventLogRepository(
    private val db: TravelExpensesDb,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : EventLogRepository {

    private val mutex = Mutex()

    override suspend fun append(event: ExpenseEvent) = withContext(Dispatchers.Default) {
        mutex.withLock {
            val eventType = when (event) {
                is ExpenseEvent.ExpenseCreated -> "expense_created"
                is ExpenseEvent.ExpenseDeleted -> "expense_deleted"
                is ExpenseEvent.TripCreated -> "trip_created"
                is ExpenseEvent.TripArchived -> "trip_archived"
            }
            val payload = json.encodeToString(ExpenseEvent.serializer(), event)
            db.eventLogQueries.insert(
                event_id = event.eventId,
                sequence_number = event.sequenceNumber,
                device_id = event.deviceId,
                timestamp = event.timestamp.toString(),
                event_type = eventType,
                payload = payload,
            )
        }
    }

    override suspend fun getAllEvents(): List<ExpenseEvent> = withContext(Dispatchers.Default) {
        db.eventLogQueries.selectAll().executeAsList().map { row ->
            json.decodeFromString(ExpenseEvent.serializer(), row.payload)
        }
    }

    override suspend fun getEventsForDevice(deviceId: DeviceId): List<ExpenseEvent> =
        withContext(Dispatchers.Default) {
            db.eventLogQueries.selectByDevice(deviceId).executeAsList().map { row ->
                json.decodeFromString(ExpenseEvent.serializer(), row.payload)
            }
        }

    override fun observeEventCount(): Flow<Long> {
        return db.eventLogQueries.selectCount()
            .asFlow()
            .mapToOne(Dispatchers.Default)
    }

    override fun observeAllEvents(): Flow<List<ExpenseEvent>> {
        return db.eventLogQueries.selectAll()
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows ->
                rows.map { row ->
                    json.decodeFromString(ExpenseEvent.serializer(), row.payload)
                }
            }
    }

    override suspend fun count(): Long = withContext(Dispatchers.Default) {
        db.eventLogQueries.selectCount().executeAsOne()
    }

    override suspend fun clear() = withContext(Dispatchers.Default) {
        db.eventLogQueries.deleteAll()
    }
}
