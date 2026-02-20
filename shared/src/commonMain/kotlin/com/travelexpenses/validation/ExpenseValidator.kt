package com.travelexpenses.validation

import com.travelexpenses.model.Expense

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

        // Amount must be a valid positive decimal (string-based, no Double)
        when (decimalSign(expense.amount)) {
            null -> errors.add(ValidationError.INVALID_AMOUNT)
            0, -1 -> errors.add(ValidationError.NON_POSITIVE_AMOUNT)
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
                when (decimalSign(taxStr)) {
                    null -> errors.add(ValidationError.INVALID_TAX_AMOUNT)
                    -1 -> errors.add(ValidationError.NEGATIVE_TAX_AMOUNT)
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
        val sign = decimalSign(amount) ?: return false
        return sign == 1
    }

    fun isValidCurrencyCode(code: String): Boolean {
        return com.travelexpenses.validation.isValidCurrencyCode(code)
    }

    fun isValidTaxAmount(tax: String): Boolean {
        if (tax.isEmpty()) return true
        val sign = decimalSign(tax) ?: return false
        return sign >= 0
    }
}

/**
 * ISO 4217 currency code validation: exactly 3 uppercase ASCII letters.
 */
private fun isValidCurrencyCode(code: String): Boolean {
    return code.length == 3 && code.all { it in 'A'..'Z' }
}

private val DECIMAL_PATTERN = Regex("""^-?\d+(\.\d+)?$""")

/**
 * Returns the sign of a string-encoded decimal: 1 (positive), 0 (zero), -1 (negative),
 * or null if the string is not a valid decimal. Avoids Double/Float to preserve precision.
 */
internal fun decimalSign(value: String): Int? {
    if (!DECIMAL_PATTERN.matches(value)) return null
    val negative = value.startsWith('-')
    val digits = if (negative) value.drop(1) else value
    val isZero = digits.all { it == '0' || it == '.' }
    return when {
        isZero -> 0
        negative -> -1
        else -> 1
    }
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
