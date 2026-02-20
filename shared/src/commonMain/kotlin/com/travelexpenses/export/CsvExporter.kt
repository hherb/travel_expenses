package com.travelexpenses.export

import com.travelexpenses.currency.CurrencyConverter
import com.travelexpenses.model.Expense
import com.travelexpenses.model.TripId
import com.travelexpenses.repository.ExpenseRepository
import com.travelexpenses.repository.TripRepository
import com.travelexpenses.repository.CategoryRepository
import kotlinx.datetime.LocalDate

/**
 * Generates CSV reports from materialized expense state.
 * One row per expense, all fields, UTF-8 with BOM for Excel compatibility.
 *
 * Export scopes:
 * - Single trip
 * - Date range (across all trips)
 * - All data
 */
class CsvExporter(
    private val expenseRepo: ExpenseRepository,
    private val tripRepo: TripRepository,
    private val categoryRepo: CategoryRepository,
    private val currencyConverter: CurrencyConverter? = null,
) {

    /**
     * Export expenses for a single trip.
     */
    suspend fun exportTrip(tripId: TripId): String {
        val trip = tripRepo.getTrip(tripId)
        val expenses = expenseRepo.getExpensesForTrip(tripId)
        val baseCurrency = trip?.baseCurrency
        return buildCsv(expenses, baseCurrency)
    }

    /**
     * Export expenses within a date range (across all trips).
     */
    suspend fun exportDateRange(startDate: LocalDate, endDate: LocalDate): String {
        val allExpenses = expenseRepo.getAllActiveExpenses()
        val filtered = allExpenses.filter { it.date in startDate..endDate }
        return buildCsv(filtered, baseCurrency = null)
    }

    /**
     * Export all active expenses.
     */
    suspend fun exportAll(): String {
        val expenses = expenseRepo.getAllActiveExpenses()
        return buildCsv(expenses, baseCurrency = null)
    }

    private suspend fun buildCsv(expenses: List<Expense>, baseCurrency: String?): String {
        val categories = categoryRepo.getAllCategories().associateBy { it.id }

        val sb = StringBuilder()
        // UTF-8 BOM for Excel
        sb.append('\uFEFF')

        // Header row
        val headers = mutableListOf(
            "Expense ID",
            "Trip ID",
            "Date",
            "Vendor",
            "Amount",
            "Currency",
            "Category",
            "Tax",
            "Notes",
            "Tags",
            "OCR Confidence",
        )
        if (baseCurrency != null && currencyConverter != null) {
            headers.add("Converted Amount ($baseCurrency)")
        }
        sb.appendLine(headers.joinToString(","))

        // Data rows
        for (expense in expenses.sortedBy { it.date }) {
            val categoryName = categories[expense.categoryId]?.name ?: expense.categoryId
            val tags = expenseRepo.getTagsForExpense(expense.id)
            val tagLabels = tags.joinToString("; ") { it.label }

            val fields = mutableListOf(
                escapeCsv(expense.id),
                escapeCsv(expense.tripId),
                expense.date.toString(),
                escapeCsv(expense.vendor ?: ""),
                expense.amount,
                expense.currency,
                escapeCsv(categoryName),
                expense.taxAmount ?: "",
                escapeCsv(expense.notes ?: ""),
                escapeCsv(tagLabels),
                expense.ocrConfidence?.toString() ?: "",
            )

            if (baseCurrency != null && currencyConverter != null) {
                val converted = currencyConverter.convert(
                    expense.amount,
                    expense.currency,
                    baseCurrency,
                    expense.date,
                )
                fields.add(converted?.convertedAmount ?: "N/A")
            }

            sb.appendLine(fields.joinToString(","))
        }

        return sb.toString()
    }
}

/**
 * Escape a value for CSV: wrap in quotes if it contains commas, quotes, or newlines.
 * Double any existing quotes per RFC 4180.
 */
internal fun escapeCsv(value: String): String {
    return if (value.contains(',') || value.contains('"') || value.contains('\n') || value.contains('\r')) {
        "\"${value.replace("\"", "\"\"")}\""
    } else {
        value
    }
}
