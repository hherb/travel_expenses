package com.travelexpenses.validation

import com.travelexpenses.model.Expense

/**
 * Validates expense data before creating events.
 * Returns a list of validation errors (empty = valid).
 */
object ExpenseValidator {

    /** ISO 4217 currency codes recognized by the app. */
    private val validCurrencyCodes = setOf(
        "USD", "EUR", "GBP", "JPY", "CHF", "CAD", "AUD", "NZD",
        "SEK", "NOK", "DKK", "CNY", "HKD", "SGD", "KRW", "INR",
        "BRL", "MXN", "ZAR", "THB", "TWD", "PLN", "CZK", "HUF",
        "ILS", "AED", "SAR", "QAR", "KWD", "BHD", "OMR", "JOD",
        "EGP", "TRY", "RUB", "UAH", "PHP", "IDR", "MYR", "VND",
        "CLP", "COP", "PEN", "ARS",
    )

    /**
     * Validate an expense and return all detected validation errors.
     * An empty list means the expense is valid.
     *
     * @param expense The expense to validate.
     * @return List of [ValidationError] entries; empty if valid.
     */
    fun validate(expense: Expense): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()

        // Amount must be a positive number
        val amount = expense.amount.toDoubleOrNull()
        if (amount == null) {
            errors.add(ValidationError.INVALID_AMOUNT)
        } else if (amount <= 0) {
            errors.add(ValidationError.AMOUNT_NOT_POSITIVE)
        }

        // Currency must be a valid ISO 4217 code
        if (expense.currency.isBlank()) {
            errors.add(ValidationError.MISSING_CURRENCY)
        } else if (expense.currency.uppercase() !in validCurrencyCodes) {
            errors.add(ValidationError.INVALID_CURRENCY_CODE)
        }

        // Trip ID required
        if (expense.tripId.isBlank()) {
            errors.add(ValidationError.MISSING_TRIP_ID)
        }

        // Category ID required
        if (expense.categoryId.isBlank()) {
            errors.add(ValidationError.MISSING_CATEGORY)
        }

        // Tax amount if present must be valid
        expense.taxAmount?.let { tax ->
            val taxVal = tax.toDoubleOrNull()
            if (taxVal == null) {
                errors.add(ValidationError.INVALID_TAX_AMOUNT)
            } else if (taxVal < 0) {
                errors.add(ValidationError.TAX_AMOUNT_NEGATIVE)
            }
        }

        return errors
    }

    /** Convenience check: returns `true` if the expense passes all validation rules. */
    fun isValid(expense: Expense): Boolean = validate(expense).isEmpty()
}

enum class ValidationError(val message: String) {
    INVALID_AMOUNT("Amount must be a valid number"),
    AMOUNT_NOT_POSITIVE("Amount must be greater than zero"),
    MISSING_CURRENCY("Currency code is required"),
    INVALID_CURRENCY_CODE("Currency code is not a recognized ISO 4217 code"),
    MISSING_TRIP_ID("Trip ID is required"),
    MISSING_CATEGORY("Category is required"),
    INVALID_TAX_AMOUNT("Tax amount must be a valid number"),
    TAX_AMOUNT_NEGATIVE("Tax amount cannot be negative"),
}
