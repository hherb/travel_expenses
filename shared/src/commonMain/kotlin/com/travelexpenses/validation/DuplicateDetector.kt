package com.travelexpenses.validation

import com.travelexpenses.model.Expense

/**
 * Heuristic duplicate detection: flags expenses that share the same
 * amount + vendor + date as potential duplicates.
 */
object DuplicateDetector {

    /**
     * From a list of expenses, return groups of potential duplicates.
     * Each group contains 2+ expenses that share (amount, vendor, date).
     * Vendor comparison is case-insensitive and trimmed.
     */
    fun findDuplicates(expenses: List<Expense>): List<DuplicateGroup> {
        return expenses
            .groupBy { expense ->
                DuplicateKey(
                    amount = expense.amount,
                    vendor = expense.vendor?.trim()?.lowercase() ?: "",
                    date = expense.date.toString(),
                )
            }
            .filter { it.value.size > 1 }
            .map { (key, group) ->
                DuplicateGroup(
                    amount = key.amount,
                    vendor = key.vendor,
                    date = key.date,
                    expenses = group,
                )
            }
    }

    /**
     * Check if a single new expense would be a duplicate of any existing expense.
     */
    fun isDuplicate(newExpense: Expense, existingExpenses: List<Expense>): Boolean {
        val newKey = DuplicateKey(
            amount = newExpense.amount,
            vendor = newExpense.vendor?.trim()?.lowercase() ?: "",
            date = newExpense.date.toString(),
        )

        return existingExpenses.any { existing ->
            existing.id != newExpense.id &&
            DuplicateKey(
                amount = existing.amount,
                vendor = existing.vendor?.trim()?.lowercase() ?: "",
                date = existing.date.toString(),
            ) == newKey
        }
    }
}

private data class DuplicateKey(
    val amount: String,
    val vendor: String,
    val date: String,
)

/**
 * A group of 2+ expenses that share the same (amount, vendor, date)
 * and are flagged as potential duplicates.
 */
data class DuplicateGroup(
    val amount: String,
    val vendor: String,
    val date: String,
    val expenses: List<Expense>,
)
