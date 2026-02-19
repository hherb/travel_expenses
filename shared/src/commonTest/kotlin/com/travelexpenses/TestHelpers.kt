package com.travelexpenses

import com.travelexpenses.event.ExpenseEvent
import com.travelexpenses.model.*
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlin.random.Random

/**
 * Shared test helpers for creating domain objects.
 * Every test should use these to avoid boilerplate.
 */
object TestHelpers {

    private var sequenceCounter = 0L

    fun nextSequence(): Long = ++sequenceCounter

    fun resetSequence() {
        sequenceCounter = 0L
    }

    fun makeExpense(
        id: String = "exp-${Random.nextInt(100000)}",
        tripId: String = "trip-1",
        amount: String = "25.50",
        currency: String = "USD",
        categoryId: String = "food",
        vendor: String? = "Test Vendor",
        date: LocalDate = LocalDate(2026, 1, 15),
    ): Expense {
        val now = Clock.System.now()
        return Expense(
            id = id,
            tripId = tripId,
            amount = amount,
            currency = currency,
            categoryId = categoryId,
            vendor = vendor,
            date = date,
            createdAt = now,
            lastModifiedAt = now,
        )
    }

    fun makeTrip(
        id: String = "trip-1",
        name: String = "Test Trip",
        baseCurrency: String = "USD",
    ): Trip {
        return Trip(
            id = id,
            name = name,
            baseCurrency = baseCurrency,
            createdAt = Clock.System.now(),
        )
    }

    fun makeExpenseCreatedEvent(
        expense: Expense = makeExpense(),
        deviceId: String = "device-1",
        sequenceNumber: Long = nextSequence(),
    ): ExpenseEvent.ExpenseCreated {
        return ExpenseEvent.ExpenseCreated(
            eventId = "evt-${Random.nextInt(100000)}",
            timestamp = Clock.System.now(),
            sequenceNumber = sequenceNumber,
            deviceId = deviceId,
            expense = expense,
        )
    }

    fun makeTripCreatedEvent(
        trip: Trip = makeTrip(),
        deviceId: String = "device-1",
        sequenceNumber: Long = nextSequence(),
    ): ExpenseEvent.TripCreated {
        return ExpenseEvent.TripCreated(
            eventId = "evt-${Random.nextInt(100000)}",
            timestamp = Clock.System.now(),
            sequenceNumber = sequenceNumber,
            deviceId = deviceId,
            trip = trip,
        )
    }

    fun makeExpenseDeletedEvent(
        expenseId: String,
        deviceId: String = "device-1",
        sequenceNumber: Long = nextSequence(),
    ): ExpenseEvent.ExpenseDeleted {
        return ExpenseEvent.ExpenseDeleted(
            eventId = "evt-${Random.nextInt(100000)}",
            timestamp = Clock.System.now(),
            sequenceNumber = sequenceNumber,
            deviceId = deviceId,
            expenseId = expenseId,
        )
    }

    fun makeTripArchivedEvent(
        tripId: String,
        deviceId: String = "device-1",
        sequenceNumber: Long = nextSequence(),
    ): ExpenseEvent.TripArchived {
        return ExpenseEvent.TripArchived(
            eventId = "evt-${Random.nextInt(100000)}",
            timestamp = Clock.System.now(),
            sequenceNumber = sequenceNumber,
            deviceId = deviceId,
            tripId = tripId,
        )
    }

    fun makeCategory(
        id: String = "cat-${Random.nextInt(100000)}",
        name: String = "Food & Drink",
        icon: String = "fork",
        isDefault: Boolean = true,
    ): Category {
        return Category(id = id, name = name, icon = icon, isDefault = isDefault)
    }

    fun makeTag(
        id: String = "tag-${Random.nextInt(100000)}",
        label: String = "reimbursable",
    ): Tag {
        return Tag(id = id, label = label)
    }

    fun makeExpenseUpdatedEvent(
        expenseId: String = "exp-1",
        deviceId: String = "device-1",
        sequenceNumber: Long = nextSequence(),
        amount: String? = null,
        currency: String? = null,
        categoryId: String? = null,
        vendor: String? = null,
        date: LocalDate? = null,
        notes: String? = null,
        tags: List<String>? = null,
        taxAmount: String? = null,
        ocrConfidence: Float? = null,
    ): ExpenseEvent.ExpenseUpdated {
        return ExpenseEvent.ExpenseUpdated(
            eventId = "evt-${Random.nextInt(100000)}",
            timestamp = Clock.System.now(),
            sequenceNumber = sequenceNumber,
            deviceId = deviceId,
            expenseId = expenseId,
            amount = amount,
            currency = currency,
            categoryId = categoryId,
            vendor = vendor,
            date = date,
            notes = notes,
            tags = tags,
            taxAmount = taxAmount,
            ocrConfidence = ocrConfidence,
            lastModifiedAt = Clock.System.now(),
        )
    }

    fun makeTripUpdatedEvent(
        tripId: String = "trip-1",
        deviceId: String = "device-1",
        sequenceNumber: Long = nextSequence(),
        name: String? = null,
        destination: String? = null,
        startDate: LocalDate? = null,
        endDate: LocalDate? = null,
        baseCurrency: String? = null,
    ): ExpenseEvent.TripUpdated {
        return ExpenseEvent.TripUpdated(
            eventId = "evt-${Random.nextInt(100000)}",
            timestamp = Clock.System.now(),
            sequenceNumber = sequenceNumber,
            deviceId = deviceId,
            tripId = tripId,
            name = name,
            destination = destination,
            startDate = startDate,
            endDate = endDate,
            baseCurrency = baseCurrency,
        )
    }

    fun makeCategoryCreatedEvent(
        category: Category = makeCategory(),
        deviceId: String = "device-1",
        sequenceNumber: Long = nextSequence(),
    ): ExpenseEvent.CategoryCreated {
        return ExpenseEvent.CategoryCreated(
            eventId = "evt-${Random.nextInt(100000)}",
            timestamp = Clock.System.now(),
            sequenceNumber = sequenceNumber,
            deviceId = deviceId,
            category = category,
        )
    }

    fun makeCategoryUpdatedEvent(
        categoryId: String = "cat-1",
        deviceId: String = "device-1",
        sequenceNumber: Long = nextSequence(),
        name: String? = null,
        icon: String? = null,
    ): ExpenseEvent.CategoryUpdated {
        return ExpenseEvent.CategoryUpdated(
            eventId = "evt-${Random.nextInt(100000)}",
            timestamp = Clock.System.now(),
            sequenceNumber = sequenceNumber,
            deviceId = deviceId,
            categoryId = categoryId,
            name = name,
            icon = icon,
        )
    }

    fun makeTagCreatedEvent(
        tag: Tag = makeTag(),
        deviceId: String = "device-1",
        sequenceNumber: Long = nextSequence(),
    ): ExpenseEvent.TagCreated {
        return ExpenseEvent.TagCreated(
            eventId = "evt-${Random.nextInt(100000)}",
            timestamp = Clock.System.now(),
            sequenceNumber = sequenceNumber,
            deviceId = deviceId,
            tag = tag,
        )
    }

    fun makeReceiptAttachedEvent(
        expenseId: String = "exp-1",
        imageId: String = "img-${Random.nextInt(100000)}",
        deviceId: String = "device-1",
        sequenceNumber: Long = nextSequence(),
    ): ExpenseEvent.ReceiptAttached {
        return ExpenseEvent.ReceiptAttached(
            eventId = "evt-${Random.nextInt(100000)}",
            timestamp = Clock.System.now(),
            sequenceNumber = sequenceNumber,
            deviceId = deviceId,
            expenseId = expenseId,
            imageId = imageId,
        )
    }

    fun makeReceiptDetachedEvent(
        expenseId: String = "exp-1",
        imageId: String = "img-1",
        deviceId: String = "device-1",
        sequenceNumber: Long = nextSequence(),
    ): ExpenseEvent.ReceiptDetached {
        return ExpenseEvent.ReceiptDetached(
            eventId = "evt-${Random.nextInt(100000)}",
            timestamp = Clock.System.now(),
            sequenceNumber = sequenceNumber,
            deviceId = deviceId,
            expenseId = expenseId,
            imageId = imageId,
        )
    }
}
