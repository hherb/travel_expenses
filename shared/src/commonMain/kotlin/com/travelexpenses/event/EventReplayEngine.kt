package com.travelexpenses.event

import com.travelexpenses.db.TravelExpensesDb
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/**
 * Translates append-only events into materialized state tables.
 *
 * Two modes of operation:
 * - [apply]: Apply a single event to existing materialized state (runtime path).
 * - [rebuildAll]: Clear and rebuild all materialized state from a full event list (initialization/recovery).
 */
class EventReplayEngine(
    private val db: TravelExpensesDb,
    private val queryContext: CoroutineContext,
) {

    private val mutex = Mutex()

    /**
     * Apply a single event to the materialized state tables.
     * Wraps the operation in a transaction for atomicity (e.g. per-field updates)
     * and a mutex for thread safety.
     */
    suspend fun apply(event: ExpenseEvent) = withContext(queryContext) {
        mutex.withLock {
            db.transaction {
                applyWithinTransaction(event)
            }
        }
    }

    /**
     * Rebuild all materialized state from scratch by replaying a list of events.
     * Clears all materialized tables first, then replays events in order.
     * Runs within a single transaction for atomicity.
     */
    suspend fun rebuildAll(events: List<ExpenseEvent>) = withContext(queryContext) {
        mutex.withLock {
            db.transaction {
                clearAll()
                events.forEach { applyWithinTransaction(it) }
            }
        }
    }

    /**
     * Apply a single event within an existing transaction context.
     * Caller must already be on [queryContext] and within a `db.transaction {}`.
     */
    internal fun applyWithinTransaction(event: ExpenseEvent) {
        when (event) {
            is ExpenseEvent.ExpenseCreated -> applyExpenseCreated(event)
            is ExpenseEvent.ExpenseUpdated -> applyExpenseUpdated(event)
            is ExpenseEvent.ExpenseDeleted -> applyExpenseDeleted(event)
            is ExpenseEvent.TripCreated -> applyTripCreated(event)
            is ExpenseEvent.TripUpdated -> applyTripUpdated(event)
            is ExpenseEvent.TripArchived -> applyTripArchived(event)
            is ExpenseEvent.CategoryCreated -> applyCategoryCreated(event)
            is ExpenseEvent.CategoryUpdated -> applyCategoryUpdated(event)
            is ExpenseEvent.TagCreated -> applyTagCreated(event)
            is ExpenseEvent.ReceiptAttached -> applyReceiptAttached(event)
            is ExpenseEvent.ReceiptDetached -> applyReceiptDetached(event)
        }
    }

    private fun clearAll() {
        val q = db.materializedStateQueries
        q.clearAllExpenseTags()
        q.clearAllReceiptImages()
        q.clearAllExpenses()
        q.clearAllTags()
        q.clearAllCategories()
        q.clearAllTrips()
    }

    private fun applyExpenseCreated(event: ExpenseEvent.ExpenseCreated) {
        val exp = event.expense
        db.materializedStateQueries.upsertExpense(
            id = exp.id,
            trip_id = exp.tripId,
            amount = exp.amount,
            currency = exp.currency,
            category_id = exp.categoryId,
            vendor = exp.vendor,
            date = exp.date.toString(),
            notes = exp.notes,
            tax_amount = exp.taxAmount,
            ocr_confidence = exp.ocrConfidence?.toDouble(),
            created_at = exp.createdAt.toString(),
            last_modified_at = exp.lastModifiedAt.toString(),
            is_deleted = 0L,
        )
        exp.tags.forEach { tagId ->
            db.materializedStateQueries.insertExpenseTag(
                expense_id = exp.id,
                tag_id = tagId,
            )
        }
    }

    private fun applyExpenseUpdated(event: ExpenseEvent.ExpenseUpdated) {
        val q = db.materializedStateQueries
        val ts = event.lastModifiedAt.toString()
        event.amount?.let { q.updateExpenseAmount(it, ts, event.expenseId) }
        event.currency?.let { q.updateExpenseCurrency(it, ts, event.expenseId) }
        event.categoryId?.let { q.updateExpenseCategoryId(it, ts, event.expenseId) }
        // For nullable fields: "" means "clear to null", non-empty means "set value"
        event.vendor?.let { q.updateExpenseVendor(it.ifEmpty { null }, ts, event.expenseId) }
        event.date?.let { q.updateExpenseDate(it.toString(), ts, event.expenseId) }
        event.notes?.let { q.updateExpenseNotes(it.ifEmpty { null }, ts, event.expenseId) }
        event.taxAmount?.let { q.updateExpenseTaxAmount(it.ifEmpty { null }, ts, event.expenseId) }
        event.ocrConfidence?.let { q.updateExpenseOcrConfidence(it.toDouble(), ts, event.expenseId) }
        event.tags?.let { newTags ->
            q.deleteExpenseTagsForExpense(event.expenseId)
            newTags.forEach { tagId ->
                q.insertExpenseTag(event.expenseId, tagId)
            }
        }
    }

    private fun applyExpenseDeleted(event: ExpenseEvent.ExpenseDeleted) {
        db.materializedStateQueries.softDeleteExpense(event.expenseId)
    }

    private fun applyTripCreated(event: ExpenseEvent.TripCreated) {
        val trip = event.trip
        db.materializedStateQueries.upsertTrip(
            id = trip.id,
            name = trip.name,
            destination = trip.destination,
            start_date = trip.startDate?.toString(),
            end_date = trip.endDate?.toString(),
            base_currency = trip.baseCurrency,
            created_at = trip.createdAt.toString(),
            is_archived = 0L,
        )
    }

    private fun applyTripUpdated(event: ExpenseEvent.TripUpdated) {
        val q = db.materializedStateQueries
        event.name?.let { q.updateTripName(it, event.tripId) }
        // destination is nullable on Trip: "" means "clear to null"
        event.destination?.let { q.updateTripDestination(it.ifEmpty { null }, event.tripId) }
        event.startDate?.let { q.updateTripStartDate(it.toString(), event.tripId) }
        event.endDate?.let { q.updateTripEndDate(it.toString(), event.tripId) }
        event.baseCurrency?.let { q.updateTripBaseCurrency(it, event.tripId) }
    }

    private fun applyTripArchived(event: ExpenseEvent.TripArchived) {
        db.materializedStateQueries.archiveTrip(event.tripId)
    }

    private fun applyCategoryCreated(event: ExpenseEvent.CategoryCreated) {
        val cat = event.category
        db.materializedStateQueries.upsertCategory(
            id = cat.id,
            name = cat.name,
            icon = cat.icon,
            is_default = if (cat.isDefault) 1L else 0L,
        )
    }

    private fun applyCategoryUpdated(event: ExpenseEvent.CategoryUpdated) {
        val q = db.materializedStateQueries
        event.name?.let { q.updateCategoryName(it, event.categoryId) }
        event.icon?.let { q.updateCategoryIcon(it, event.categoryId) }
    }

    private fun applyTagCreated(event: ExpenseEvent.TagCreated) {
        db.materializedStateQueries.insertTag(
            id = event.tag.id,
            label = event.tag.label,
        )
    }

    private fun applyReceiptAttached(event: ExpenseEvent.ReceiptAttached) {
        db.materializedStateQueries.insertReceiptImage(
            id = event.imageId,
            expense_id = event.expenseId,
            file_path = event.filePath,
            thumbnail_path = event.thumbnailPath,
            width = event.width?.toLong(),
            height = event.height?.toLong(),
            created_at = event.timestamp.toString(),
        )
    }

    private fun applyReceiptDetached(event: ExpenseEvent.ReceiptDetached) {
        db.materializedStateQueries.deleteReceiptImage(
            id = event.imageId,
            expense_id = event.expenseId,
        )
    }
}
