package com.travelexpenses.event

import com.travelexpenses.model.*
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Append-only event representing a single mutation.
 * The event log is the source of truth; materialized state is derived from replay.
 */
@Serializable
sealed class ExpenseEvent {
    abstract val eventId: EventId
    abstract val timestamp: Instant
    abstract val sequenceNumber: Long
    abstract val deviceId: DeviceId

    @Serializable
    @SerialName("expense_created")
    data class ExpenseCreated(
        override val eventId: EventId,
        override val timestamp: Instant,
        override val sequenceNumber: Long,
        override val deviceId: DeviceId,
        val expense: Expense,
    ) : ExpenseEvent()

    @Serializable
    @SerialName("expense_deleted")
    data class ExpenseDeleted(
        override val eventId: EventId,
        override val timestamp: Instant,
        override val sequenceNumber: Long,
        override val deviceId: DeviceId,
        val expenseId: ExpenseId,
    ) : ExpenseEvent()

    @Serializable
    @SerialName("trip_created")
    data class TripCreated(
        override val eventId: EventId,
        override val timestamp: Instant,
        override val sequenceNumber: Long,
        override val deviceId: DeviceId,
        val trip: Trip,
    ) : ExpenseEvent()

    @Serializable
    @SerialName("trip_archived")
    data class TripArchived(
        override val eventId: EventId,
        override val timestamp: Instant,
        override val sequenceNumber: Long,
        override val deviceId: DeviceId,
        val tripId: TripId,
    ) : ExpenseEvent()

    /**
     * Partial update to an existing expense. Null fields mean "no change".
     * For fields that are already nullable on Expense (vendor, notes, taxAmount),
     * use empty string "" to mean "clear the value".
     */
    @Serializable
    @SerialName("expense_updated")
    data class ExpenseUpdated(
        override val eventId: EventId,
        override val timestamp: Instant,
        override val sequenceNumber: Long,
        override val deviceId: DeviceId,
        val expenseId: ExpenseId,
        val amount: String? = null,
        val currency: String? = null,
        val categoryId: CategoryId? = null,
        val vendor: String? = null,
        val date: LocalDate? = null,
        val notes: String? = null,
        val tags: List<TagId>? = null,
        val taxAmount: String? = null,
        val ocrConfidence: Float? = null,
        val lastModifiedAt: Instant,
    ) : ExpenseEvent()

    @Serializable
    @SerialName("trip_updated")
    data class TripUpdated(
        override val eventId: EventId,
        override val timestamp: Instant,
        override val sequenceNumber: Long,
        override val deviceId: DeviceId,
        val tripId: TripId,
        val name: String? = null,
        val destination: String? = null,
        val startDate: LocalDate? = null,
        val endDate: LocalDate? = null,
        val baseCurrency: String? = null,
    ) : ExpenseEvent()

    @Serializable
    @SerialName("category_created")
    data class CategoryCreated(
        override val eventId: EventId,
        override val timestamp: Instant,
        override val sequenceNumber: Long,
        override val deviceId: DeviceId,
        val category: Category,
    ) : ExpenseEvent()

    @Serializable
    @SerialName("category_updated")
    data class CategoryUpdated(
        override val eventId: EventId,
        override val timestamp: Instant,
        override val sequenceNumber: Long,
        override val deviceId: DeviceId,
        val categoryId: CategoryId,
        val name: String? = null,
        val icon: String? = null,
    ) : ExpenseEvent()

    @Serializable
    @SerialName("tag_created")
    data class TagCreated(
        override val eventId: EventId,
        override val timestamp: Instant,
        override val sequenceNumber: Long,
        override val deviceId: DeviceId,
        val tag: Tag,
    ) : ExpenseEvent()

    @Serializable
    @SerialName("receipt_attached")
    data class ReceiptAttached(
        override val eventId: EventId,
        override val timestamp: Instant,
        override val sequenceNumber: Long,
        override val deviceId: DeviceId,
        val expenseId: ExpenseId,
        val imageId: ImageId,
    ) : ExpenseEvent()

    @Serializable
    @SerialName("receipt_detached")
    data class ReceiptDetached(
        override val eventId: EventId,
        override val timestamp: Instant,
        override val sequenceNumber: Long,
        override val deviceId: DeviceId,
        val expenseId: ExpenseId,
        val imageId: ImageId,
    ) : ExpenseEvent()
}
