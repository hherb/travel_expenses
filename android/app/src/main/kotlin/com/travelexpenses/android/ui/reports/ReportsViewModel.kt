package com.travelexpenses.android.ui.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.travelexpenses.currency.CurrencyConverter
import com.travelexpenses.export.CsvExporter
import com.travelexpenses.model.Trip
import com.travelexpenses.model.TripId
import com.travelexpenses.repository.ExpenseRepository
import com.travelexpenses.repository.TripRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode

data class CategoryBreakdown(
    val categoryId: String,
    val categoryName: String,
    val total: String,
    val count: Int,
    val percentage: Float,
)

data class TripReport(
    val trip: Trip,
    val totalAmount: String,
    val expenseCount: Int,
    val categoryBreakdown: List<CategoryBreakdown>,
    val dailyAverage: String,
)

class ReportsViewModel(
    private val tripRepo: TripRepository,
    private val expenseRepo: ExpenseRepository,
    private val currencyConverter: CurrencyConverter,
    private val csvExporter: CsvExporter,
) : ViewModel() {

    val trips: StateFlow<List<Trip>> = tripRepo.observeActiveTrips()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedTripId = MutableStateFlow<TripId?>(null)
    val selectedTripId: StateFlow<TripId?> = _selectedTripId

    private val _report = MutableStateFlow<TripReport?>(null)
    val report: StateFlow<TripReport?> = _report

    private val _csvOutput = MutableSharedFlow<String>()
    val csvOutput: SharedFlow<String> = _csvOutput

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    fun selectTrip(tripId: TripId) {
        _selectedTripId.value = tripId
        loadReport(tripId)
    }

    private fun loadReport(tripId: TripId) {
        viewModelScope.launch {
            _isLoading.value = true
            val trip = tripRepo.getTrip(tripId)
            if (trip == null) {
                _isLoading.value = false
                return@launch
            }

            val expenses = expenseRepo.getExpensesForTrip(tripId)
            val totalResult = currencyConverter.aggregateTripTotal(expenses, trip.baseCurrency)

            // Category breakdown using BigDecimal for precision
            val byCategory = expenses.groupBy { it.categoryId }
            val grandTotal = totalResult.total.toBigDecimalOrNull() ?: BigDecimal.ZERO

            val breakdown = byCategory.map { (catId, catExpenses) ->
                val catTotal = catExpenses.fold(BigDecimal.ZERO) { acc, expense ->
                    acc + (expense.amount.toBigDecimalOrNull() ?: BigDecimal.ZERO)
                }
                val pct = if (grandTotal > BigDecimal.ZERO) {
                    catTotal.multiply(BigDecimal(100))
                        .divide(grandTotal, 1, RoundingMode.HALF_UP)
                        .toFloat()
                } else 0f

                CategoryBreakdown(
                    categoryId = catId,
                    categoryName = catId,
                    total = catTotal.setScale(2, RoundingMode.HALF_UP).toPlainString(),
                    count = catExpenses.size,
                    percentage = pct,
                )
            }.sortedByDescending { it.total.toBigDecimalOrNull() ?: BigDecimal.ZERO }

            // Daily average
            val days = if (trip.startDate != null && trip.endDate != null) {
                val d = trip.endDate!!.toEpochDays() - trip.startDate!!.toEpochDays() + 1
                if (d > 0) d else 1
            } else if (expenses.isNotEmpty()) {
                val minDate = expenses.minOf { it.date }.toEpochDays()
                val maxDate = expenses.maxOf { it.date }.toEpochDays()
                val d = maxDate - minDate + 1
                if (d > 0) d else 1
            } else 1

            val avg = if (grandTotal > BigDecimal.ZERO) {
                grandTotal.divide(BigDecimal(days), 2, RoundingMode.HALF_UP).toPlainString()
            } else "0.00"

            _report.value = TripReport(
                trip = trip,
                totalAmount = "${totalResult.total} ${trip.baseCurrency}",
                expenseCount = expenses.size,
                categoryBreakdown = breakdown,
                dailyAverage = "$avg ${trip.baseCurrency}",
            )
            _isLoading.value = false
        }
    }

    fun exportCsv() {
        val tripId = _selectedTripId.value ?: return
        viewModelScope.launch {
            val csv = csvExporter.exportTrip(tripId)
            _csvOutput.emit(csv)
        }
    }

    fun exportAllCsv() {
        viewModelScope.launch {
            val csv = csvExporter.exportAll()
            _csvOutput.emit(csv)
        }
    }
}
