package com.travelexpenses.validation

import com.travelexpenses.model.Expense
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * Heuristic duplicate detection for expenses.
 * Flags potential duplicates based on same amount + vendor + date within a threshold.
 */
class DuplicateDetector(
    private val dayThreshold: Int = 1,
) {

    /**
     * Find potential duplicates for [candidate] within [existingExpenses].
     * Returns expenses that match the heuristic: same amount, same vendor (case-insensitive),
     * and date within [dayThreshold] days.
     */
    fun findDuplicates(candidate: Expense, existingExpenses: List<Expense>): List<Expense> {
        return existingExpenses.filter { existing ->
            existing.id != candidate.id && isDuplicate(candidate, existing)
        }
    }

    /**
     * Check whether two expenses are potential duplicates.
     */
    fun isDuplicate(a: Expense, b: Expense): Boolean {
        // Amount must match exactly (string comparison for precision)
        if (a.amount != b.amount) return false

        // Currency must match
        if (a.currency != b.currency) return false

        // Vendor must match (case-insensitive, both non-null)
        val vendorA = a.vendor?.trim()?.lowercase() ?: return false
        val vendorB = b.vendor?.trim()?.lowercase() ?: return false
        if (vendorA != vendorB) return false

        // Date must be within threshold
        val minDate = a.date.minus(dayThreshold, DateTimeUnit.DAY)
        val maxDate = a.date.plus(dayThreshold, DateTimeUnit.DAY)
        return b.date in minDate..maxDate
    }
}
