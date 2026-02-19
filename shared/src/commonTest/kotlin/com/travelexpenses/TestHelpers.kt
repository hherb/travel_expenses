package com.travelexpenses

import com.travelexpenses.event.ExpenseEvent
import com.travelexpenses.model.Expense
import com.travelexpenses.model.Trip
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
}
