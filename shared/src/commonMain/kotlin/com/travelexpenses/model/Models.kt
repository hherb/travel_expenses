package com.travelexpenses.model

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/** Typealias wrappers for domain IDs -- cheap, no-overhead type safety. */
typealias ExpenseId = String
typealias TripId = String
typealias CategoryId = String
typealias TagId = String
typealias ImageId = String
typealias DeviceId = String
typealias EventId = String

@Serializable
data class Expense(
    val id: ExpenseId,
    val tripId: TripId,
    val amount: String,           // String-encoded decimal for cross-platform precision
    val currency: String,         // ISO 4217
    val categoryId: CategoryId,
    val vendor: String? = null,
    val date: LocalDate,
    val notes: String? = null,
    val tags: List<TagId> = emptyList(),
    val receiptImageIds: List<ImageId> = emptyList(),
    val ocrConfidence: Float? = null,
    val taxAmount: String? = null,
    val createdAt: Instant,
    val lastModifiedAt: Instant,
)

@Serializable
data class Trip(
    val id: TripId,
    val name: String,
    val destination: String? = null,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val baseCurrency: String,
    val createdAt: Instant,
)
