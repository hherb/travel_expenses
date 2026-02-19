package com.travelexpenses.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import com.travelexpenses.db.TravelExpensesDb
import com.travelexpenses.event.EventReplayEngine
import com.travelexpenses.event.ExpenseEvent
import com.travelexpenses.model.DeviceId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlin.coroutines.CoroutineContext

/**
 * SQLDelight-backed implementation of [EventLogRepository].
 *
 * Key design decisions validated by Phase 0 tests:
 * - Uses a [Mutex] for thread-safe writes (non-reentrant, deadlock-free with [withLock])
 * - All DB operations dispatch to [queryContext] (injectable for testing)
 * - Flow observation uses SQLDelight's built-in query listener mechanism
 *
 * @param db The SQLDelight database instance.
 * @param queryContext Coroutine context for DB operations. Use [kotlinx.coroutines.Dispatchers.Default]
 *   in production, or a test dispatcher in tests.
 * @param json JSON serializer for event payloads.
 */
class SqlDelightEventLogRepository(
    private val db: TravelExpensesDb,
    private val queryContext: CoroutineContext,
    private val replayEngine: EventReplayEngine? = null,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : EventLogRepository {

    private val mutex = Mutex()

    override suspend fun append(event: ExpenseEvent) = withContext(queryContext) {
        mutex.withLock {
            db.transaction {
                val eventType = eventTypeOf(event)
                val payload = json.encodeToString(ExpenseEvent.serializer(), event)
                db.eventLogQueries.insert(
                    event_id = event.eventId,
                    sequence_number = event.sequenceNumber,
                    device_id = event.deviceId,
                    timestamp = event.timestamp.toString(),
                    event_type = eventType,
                    payload = payload,
                )
                replayEngine?.applyWithinTransaction(event)
            }
        }
    }

    override suspend fun getAllEvents(): List<ExpenseEvent> = withContext(queryContext) {
        db.eventLogQueries.selectAll().executeAsList().map { row ->
            json.decodeFromString(ExpenseEvent.serializer(), row.payload)
        }
    }

    override suspend fun getEventsForDevice(deviceId: DeviceId): List<ExpenseEvent> =
        withContext(queryContext) {
            db.eventLogQueries.selectByDevice(deviceId).executeAsList().map { row ->
                json.decodeFromString(ExpenseEvent.serializer(), row.payload)
            }
        }

    override fun observeEventCount(): Flow<Long> {
        return db.eventLogQueries.selectCount()
            .asFlow()
            .mapToOne(queryContext)
    }

    override fun observeAllEvents(): Flow<List<ExpenseEvent>> {
        return db.eventLogQueries.selectAll()
            .asFlow()
            .mapToList(queryContext)
            .map { rows ->
                rows.map { row ->
                    json.decodeFromString(ExpenseEvent.serializer(), row.payload)
                }
            }
    }

    override suspend fun count(): Long = withContext(queryContext) {
        db.eventLogQueries.selectCount().executeAsOne()
    }

    override suspend fun clear() = withContext(queryContext) {
        db.eventLogQueries.deleteAll()
    }
}

/** Maps an [ExpenseEvent] subtype to its serialization discriminator string. */
private fun eventTypeOf(event: ExpenseEvent): String = when (event) {
    is ExpenseEvent.ExpenseCreated -> "expense_created"
    is ExpenseEvent.ExpenseUpdated -> "expense_updated"
    is ExpenseEvent.ExpenseDeleted -> "expense_deleted"
    is ExpenseEvent.TripCreated -> "trip_created"
    is ExpenseEvent.TripUpdated -> "trip_updated"
    is ExpenseEvent.TripArchived -> "trip_archived"
    is ExpenseEvent.CategoryCreated -> "category_created"
    is ExpenseEvent.CategoryUpdated -> "category_updated"
    is ExpenseEvent.TagCreated -> "tag_created"
    is ExpenseEvent.ReceiptAttached -> "receipt_attached"
    is ExpenseEvent.ReceiptDetached -> "receipt_detached"
}
