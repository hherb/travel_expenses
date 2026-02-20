package com.travelexpenses.android.ui.expenses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.travelexpenses.event.ExpenseEvent
import com.travelexpenses.model.*
import com.travelexpenses.repository.CategoryRepository
import com.travelexpenses.repository.EventLogRepository
import com.travelexpenses.repository.ExpenseRepository
import com.travelexpenses.repository.TripRepository
import com.travelexpenses.validation.ExpenseValidator
import com.travelexpenses.validation.ValidationError
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import java.util.UUID

data class ExpenseFormState(
    val amount: String = "",
    val currency: String = "USD",
    val categoryId: String = "",
    val vendor: String = "",
    val date: LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault()),
    val notes: String = "",
    val taxAmount: String = "",
    val tripId: String = "",
    val tags: List<String> = emptyList(),
    val ocrConfidence: Float? = null,
)

class ExpenseViewModel(
    private val expenseRepo: ExpenseRepository,
    private val tripRepo: TripRepository,
    private val categoryRepo: CategoryRepository,
    private val eventLogRepo: EventLogRepository,
    private val validator: ExpenseValidator,
    private val defaultCategories: com.travelexpenses.validation.DefaultCategories,
) : ViewModel() {

    private val _formState = MutableStateFlow(ExpenseFormState())
    val formState: StateFlow<ExpenseFormState> = _formState

    val categories: StateFlow<List<Category>> = categoryRepo.observeAllCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _validationErrors = MutableStateFlow<List<ValidationError>>(emptyList())
    val validationErrors: StateFlow<List<ValidationError>> = _validationErrors

    private val _saveComplete = MutableSharedFlow<Boolean>()
    val saveComplete: SharedFlow<Boolean> = _saveComplete

    private var editingExpenseId: String? = null

    fun initForTrip(tripId: String) {
        viewModelScope.launch {
            val trip = tripRepo.getTrip(tripId)
            _formState.update {
                it.copy(
                    tripId = tripId,
                    currency = trip?.baseCurrency ?: "USD",
                )
            }
        }
    }

    fun initForEdit(expenseId: String) {
        editingExpenseId = expenseId
        viewModelScope.launch {
            val expense = expenseRepo.getExpense(expenseId) ?: return@launch
            _formState.value = ExpenseFormState(
                amount = expense.amount,
                currency = expense.currency,
                categoryId = expense.categoryId,
                vendor = expense.vendor ?: "",
                date = expense.date,
                notes = expense.notes ?: "",
                taxAmount = expense.taxAmount ?: "",
                tripId = expense.tripId,
                tags = expense.tags,
                ocrConfidence = expense.ocrConfidence,
            )
        }
    }

    fun initFromOcr(
        tripId: String,
        amount: String?,
        currency: String?,
        vendor: String?,
        date: LocalDate?,
        taxAmount: String?,
        ocrConfidence: Float?,
    ) {
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        _formState.value = ExpenseFormState(
            amount = amount ?: "",
            currency = currency ?: "USD",
            vendor = vendor ?: "",
            date = date ?: today,
            taxAmount = taxAmount ?: "",
            tripId = tripId,
            ocrConfidence = ocrConfidence,
        )
    }

    fun updateAmount(amount: String) = _formState.update { it.copy(amount = amount) }
    fun updateCurrency(currency: String) = _formState.update { it.copy(currency = currency) }
    fun updateCategory(categoryId: String) = _formState.update { it.copy(categoryId = categoryId) }
    fun updateVendor(vendor: String) = _formState.update { it.copy(vendor = vendor) }
    fun updateDate(date: LocalDate) = _formState.update { it.copy(date = date) }
    fun updateNotes(notes: String) = _formState.update { it.copy(notes = notes) }
    fun updateTaxAmount(tax: String) = _formState.update { it.copy(taxAmount = tax) }

    fun save() {
        viewModelScope.launch {
            val form = _formState.value
            val now = Clock.System.now()
            val today = Clock.System.todayIn(TimeZone.currentSystemDefault())

            if (editingExpenseId != null) {
                // Update existing expense
                val seq = eventLogRepo.count() + 1
                val event = ExpenseEvent.ExpenseUpdated(
                    eventId = UUID.randomUUID().toString(),
                    timestamp = now,
                    sequenceNumber = seq,
                    deviceId = "android",
                    expenseId = editingExpenseId!!,
                    amount = form.amount,
                    currency = form.currency,
                    categoryId = form.categoryId.ifEmpty { null },
                    vendor = form.vendor.ifEmpty { "" },
                    date = form.date,
                    notes = form.notes.ifEmpty { "" },
                    taxAmount = form.taxAmount.ifEmpty { "" },
                    tags = form.tags,
                    lastModifiedAt = now,
                )
                eventLogRepo.append(event)
                _saveComplete.emit(true)
            } else {
                // Create new expense
                val expenseId = UUID.randomUUID().toString()
                val expense = Expense(
                    id = expenseId,
                    tripId = form.tripId,
                    amount = form.amount,
                    currency = form.currency,
                    categoryId = form.categoryId,
                    vendor = form.vendor.ifEmpty { null },
                    date = form.date,
                    notes = form.notes.ifEmpty { null },
                    tags = form.tags,
                    ocrConfidence = form.ocrConfidence,
                    taxAmount = form.taxAmount.ifEmpty { null },
                    createdAt = now,
                    lastModifiedAt = now,
                )

                val errors = validator.validate(expense)
                if (errors.isNotEmpty()) {
                    _validationErrors.value = errors
                    return@launch
                }

                val seq = eventLogRepo.count() + 1
                val event = ExpenseEvent.ExpenseCreated(
                    eventId = UUID.randomUUID().toString(),
                    timestamp = now,
                    sequenceNumber = seq,
                    deviceId = "android",
                    expense = expense,
                )
                eventLogRepo.append(event)
                _saveComplete.emit(true)
            }
        }
    }
}
