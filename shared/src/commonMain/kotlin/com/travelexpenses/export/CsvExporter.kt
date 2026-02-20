package com.travelexpenses.export

import com.travelexpenses.currency.CurrencyConverter
import com.travelexpenses.currency.ExpenseAmount
import com.travelexpenses.model.Expense
import com.travelexpenses.model.TripId
import com.travelexpenses.repository.ExpenseRepository
import com.travelexpenses.repository.TripRepository
import com.travelexpenses.repository.CategoryRepository
import kotlinx.datetime.LocalDate

/**
 * Generates CSV reports from materialized state.
 * UTF-8 with BOM for Excel compatibility.
 *
 * Export scopes:
 * - Single trip
 * - Date range
 * - All data
 */
class CsvExporter(
    private val expenseRepository: ExpenseRepository,
    private val tripRepository: TripRepository,
    private val categoryRepository: CategoryRepository,
    private val currencyConverter: CurrencyConverter?,
) {

    companion object {
        /** UTF-8 BOM for Excel compatibility. */
        const val UTF8_BOM = "\uFEFF"

        val HEADER = listOf(
            "Expense ID",
            "Trip",
            "Date",
            "Amount",
            "Currency",
            "Converted Amount",
            "Trip Currency",
            "Category",
            "Vendor",
            "Notes",
            "Tags",
            "Tax Amount",
            "OCR Confidence",
            "Created At",
        )
    }

    /**
     * Export expenses for a single trip.
     */
    suspend fun exportTrip(tripId: TripId): String {
        val trip = tripRepository.getTrip(tripId) ?: return buildCsv(emptyList())
        val expenses = expenseRepository.getExpensesForTrip(tripId)
        return buildCsvFromExpenses(expenses, trip.baseCurrency)
    }

    /**
     * Export expenses within a date range (across all trips).
     */
    suspend fun exportDateRange(startDate: LocalDate, endDate: LocalDate): String {
        val allExpenses = expenseRepository.getAllActiveExpenses()
        val filtered = allExpenses.filter { expense ->
            expense.date in startDate..endDate
        }
        return buildCsvFromExpenses(filtered, baseCurrency = null)
    }

    /**
     * Export all active expenses.
     */
    suspend fun exportAll(): String {
        val allExpenses = expenseRepository.getAllActiveExpenses()
        return buildCsvFromExpenses(allExpenses, baseCurrency = null)
    }

    private suspend fun buildCsvFromExpenses(
        expenses: List<Expense>,
        baseCurrency: String?,
    ): String {
        // Pre-load categories and trips for lookups (avoids N+1 queries)
        val categories = categoryRepository.getAllCategories().associateBy { it.id }
        val tripCache = mutableMapOf<TripId, String>()

        val rows = expenses.map { expense ->
            val tags = expenseRepository.getTagsForExpense(expense.id)
            val categoryName = categories[expense.categoryId]?.name ?: expense.categoryId

            // Attempt currency conversion if converter is available and we have a base currency
            val convertedAmount = if (baseCurrency != null && currencyConverter != null && expense.currency != baseCurrency) {
                currencyConverter.convert(
                    expense.amount, expense.currency, baseCurrency, expense.date
                )?.convertedAmount
            } else if (baseCurrency != null && expense.currency == baseCurrency) {
                expense.amount
            } else {
                null
            }

            // Look up trip name (cached to avoid repeated queries)
            val tripName = tripCache.getOrPut(expense.tripId) {
                tripRepository.getTrip(expense.tripId)?.name ?: expense.tripId
            }

            listOf(
                expense.id,
                tripName,
                expense.date.toString(),
                expense.amount,
                expense.currency,
                convertedAmount ?: "",
                baseCurrency ?: "",
                categoryName,
                expense.vendor ?: "",
                expense.notes ?: "",
                tags.joinToString("; ") { it.label },
                expense.taxAmount ?: "",
                expense.ocrConfidence?.toString() ?: "",
                expense.createdAt.toString(),
            )
        }

        return buildCsv(rows)
    }

    /**
     * Build a CSV string from rows of field values, prepended with a UTF-8 BOM and the header row.
     */
    internal fun buildCsv(rows: List<List<String>>): String {
        val sb = StringBuilder()
        sb.append(UTF8_BOM)
        sb.appendLine(HEADER.joinToString(",") { escapeCsvField(it) })
        for (row in rows) {
            sb.appendLine(row.joinToString(",") { escapeCsvField(it) })
        }
        return sb.toString()
    }

    /** Escape a CSV field value, quoting it if it contains commas, double quotes, or newlines. */
    internal fun escapeCsvField(field: String): String {
        return if (field.contains(',') || field.contains('"') || field.contains('\n')) {
            "\"${field.replace("\"", "\"\"")}\""
        } else {
            field
        }
    }
}
