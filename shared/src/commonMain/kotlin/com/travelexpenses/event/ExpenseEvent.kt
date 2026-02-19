package com.travelexpenses.event

import com.travelexpenses.model.*
import kotlinx.datetime.Instant
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
}
