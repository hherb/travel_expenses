package com.travelexpenses.android.ui.trips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.travelexpenses.currency.CurrencyConverter
import com.travelexpenses.event.EventReplayEngine
import com.travelexpenses.event.ExpenseEvent
import com.travelexpenses.model.Expense
import com.travelexpenses.model.Trip
import com.travelexpenses.model.TripId
import com.travelexpenses.repository.EventLogRepository
import com.travelexpenses.repository.ExpenseRepository
import com.travelexpenses.repository.TripRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import java.util.UUID

class TripViewModel(
    private val tripRepo: TripRepository,
    private val expenseRepo: ExpenseRepository,
    private val eventLogRepo: EventLogRepository,
    private val replayEngine: EventReplayEngine,
    private val currencyConverter: CurrencyConverter,
    private val deviceId: String,
) : ViewModel() {

    val activeTrips: StateFlow<List<Trip>> = tripRepo.observeActiveTrips()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _archivedTrips = MutableStateFlow<List<Trip>>(emptyList())
    val archivedTrips: StateFlow<List<Trip>> = _archivedTrips

    private val _selectedTrip = MutableStateFlow<Trip?>(null)
    val selectedTrip: StateFlow<Trip?> = _selectedTrip

    private val _tripExpenses = MutableStateFlow<List<Expense>>(emptyList())
    val tripExpenses: StateFlow<List<Expense>> = _tripExpenses

    private val _tripTotal = MutableStateFlow<String?>(null)
    val tripTotal: StateFlow<String?> = _tripTotal

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    init {
        loadArchivedTrips()
    }

    private fun loadArchivedTrips() {
        viewModelScope.launch {
            _archivedTrips.value = tripRepo.getArchivedTrips()
        }
    }

    fun loadTrip(tripId: TripId) {
        viewModelScope.launch {
            _isLoading.value = true
            _selectedTrip.value = tripRepo.getTrip(tripId)
            _tripExpenses.value = expenseRepo.getExpensesForTrip(tripId)
            val trip = _selectedTrip.value
            if (trip != null && _tripExpenses.value.isNotEmpty()) {
                val result = currencyConverter.aggregateTripTotal(
                    _tripExpenses.value,
                    trip.baseCurrency,
                )
                _tripTotal.value = "${result.total} ${trip.baseCurrency}"
            } else {
                _tripTotal.value = null
            }
            _isLoading.value = false
        }
    }

    private suspend fun nextSequenceNumber(): Long = eventLogRepo.count() + 1

    fun createTrip(
        name: String,
        destination: String?,
        baseCurrency: String,
        startDate: LocalDate?,
        endDate: LocalDate?,
    ) {
        viewModelScope.launch {
            val now = Clock.System.now()
            val tripId = UUID.randomUUID().toString()
            val trip = Trip(
                id = tripId,
                name = name,
                destination = destination,
                startDate = startDate,
                endDate = endDate,
                baseCurrency = baseCurrency,
                createdAt = now,
            )
            val event = ExpenseEvent.TripCreated(
                eventId = UUID.randomUUID().toString(),
                timestamp = now,
                sequenceNumber = nextSequenceNumber(),
                deviceId = deviceId,
                trip = trip,
            )
            eventLogRepo.append(event)
            loadArchivedTrips()
        }
    }

    fun archiveTrip(tripId: TripId) {
        viewModelScope.launch {
            val now = Clock.System.now()
            val event = ExpenseEvent.TripArchived(
                eventId = UUID.randomUUID().toString(),
                timestamp = now,
                sequenceNumber = nextSequenceNumber(),
                deviceId = deviceId,
                tripId = tripId,
            )
            eventLogRepo.append(event)
            loadArchivedTrips()
        }
    }

    fun deleteExpense(expenseId: String) {
        viewModelScope.launch {
            val now = Clock.System.now()
            val event = ExpenseEvent.ExpenseDeleted(
                eventId = UUID.randomUUID().toString(),
                timestamp = now,
                sequenceNumber = nextSequenceNumber(),
                deviceId = deviceId,
                expenseId = expenseId,
            )
            eventLogRepo.append(event)
            _selectedTrip.value?.id?.let { loadTrip(it) }
        }
    }
}
