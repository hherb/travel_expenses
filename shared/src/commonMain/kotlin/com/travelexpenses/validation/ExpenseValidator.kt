package com.travelexpenses.validation

import com.travelexpenses.model.Expense
import kotlinx.datetime.LocalDate

/**
 * Validates expense data before creating events.
 * Returns a list of validation errors (empty = valid).
 */
object ExpenseValidator {

    /**
     * Validate an expense and return all errors found.
     */
    fun validate(expense: Expense): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()

        // Amount must be positive
        val amount = expense.amount.toDoubleOrNull()
        if (amount == null) {
            errors.add(ValidationError.INVALID_AMOUNT)
        } else if (amount <= 0) {
            errors.add(ValidationError.NON_POSITIVE_AMOUNT)
        }

        // Currency code must be 3 uppercase letters (ISO 4217)
        if (!isValidCurrencyCode(expense.currency)) {
            errors.add(ValidationError.INVALID_CURRENCY)
        }

        // Category ID is required (non-blank)
        if (expense.categoryId.isBlank()) {
            errors.add(ValidationError.MISSING_CATEGORY)
        }

        // Trip ID is required
        if (expense.tripId.isBlank()) {
            errors.add(ValidationError.MISSING_TRIP)
        }

        // Tax amount if present must be non-negative
        expense.taxAmount?.let { taxStr ->
            if (taxStr.isNotEmpty()) {
                val tax = taxStr.toDoubleOrNull()
                if (tax == null) {
                    errors.add(ValidationError.INVALID_TAX_AMOUNT)
                } else if (tax < 0) {
                    errors.add(ValidationError.NEGATIVE_TAX_AMOUNT)
                }
            }
        }

        // OCR confidence if present must be in [0, 1]
        expense.ocrConfidence?.let { conf ->
            if (conf < 0f || conf > 1f) {
                errors.add(ValidationError.INVALID_OCR_CONFIDENCE)
            }
        }

        return errors
    }

    /**
     * Quick check: is the expense valid?
     */
    fun isValid(expense: Expense): Boolean = validate(expense).isEmpty()
}

/**
 * Validate individual fields for use during input (before constructing an Expense).
 */
object FieldValidator {

    fun isValidAmount(amount: String): Boolean {
        val d = amount.toDoubleOrNull() ?: return false
        return d > 0
    }

    fun isValidCurrencyCode(code: String): Boolean {
        return com.travelexpenses.validation.isValidCurrencyCode(code)
    }

    fun isValidTaxAmount(tax: String): Boolean {
        if (tax.isEmpty()) return true
        val d = tax.toDoubleOrNull() ?: return false
        return d >= 0
    }
}

/**
 * ISO 4217 currency code validation: exactly 3 uppercase ASCII letters.
 */
private fun isValidCurrencyCode(code: String): Boolean {
    return code.length == 3 && code.all { it in 'A'..'Z' }
}

enum class ValidationError(val message: String) {
    INVALID_AMOUNT("Amount must be a valid number"),
    NON_POSITIVE_AMOUNT("Amount must be greater than zero"),
    INVALID_CURRENCY("Currency must be a valid 3-letter ISO 4217 code"),
    MISSING_CATEGORY("Category is required"),
    MISSING_TRIP("Trip is required"),
    INVALID_TAX_AMOUNT("Tax amount must be a valid number"),
    NEGATIVE_TAX_AMOUNT("Tax amount cannot be negative"),
    INVALID_OCR_CONFIDENCE("OCR confidence must be between 0.0 and 1.0"),
}
