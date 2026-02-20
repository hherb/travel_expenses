package com.travelexpenses.currency

import com.travelexpenses.model.Expense
import com.travelexpenses.repository.ExchangeRate
import com.travelexpenses.repository.ExchangeRateRepository
import kotlinx.datetime.LocalDate
import kotlin.math.roundToLong

/**
 * Display-only currency conversion using cached exchange rates.
 * All conversions are for display purposes — canonical amounts remain in their original currency.
 *
 * Uses Double for conversion math, which is acceptable for display-only purposes
 * (canonical amounts are always stored in original currency as String-encoded decimals).
 *
 * Lookup order:
 * 1. ExchangeRateRepository (date-specific rate)
 * 2. ExchangeRateRepository (latest available rate for pair)
 * 3. Static fallback table (common pairs with approximate rates)
 */
class CurrencyConverter(
    private val exchangeRateRepo: ExchangeRateRepository,
) {

    /**
     * Convert [amount] from [fromCurrency] to [toCurrency] on a given [date].
     * Returns the converted amount as a String-encoded decimal, or null if no rate is available.
     */
    suspend fun convert(
        amount: String,
        fromCurrency: String,
        toCurrency: String,
        date: LocalDate? = null,
    ): ConversionResult? {
        if (fromCurrency == toCurrency) {
            return ConversionResult(
                convertedAmount = amount,
                rate = "1",
                source = "identity",
            )
        }

        val amountDouble = amount.toDoubleOrNull() ?: return null

        // Try direct rate
        val directRate = findRate(fromCurrency, toCurrency, date)
        if (directRate != null) {
            val rateDouble = directRate.rate.toDoubleOrNull() ?: return null
            val converted = amountDouble * rateDouble
            return ConversionResult(
                convertedAmount = roundToTwoDecimals(converted),
                rate = directRate.rate,
                source = directRate.source,
            )
        }

        // Try inverse rate
        val inverseRate = findRate(toCurrency, fromCurrency, date)
        if (inverseRate != null) {
            val rateDouble = inverseRate.rate.toDoubleOrNull() ?: return null
            if (rateDouble == 0.0) return null
            val invertedRate = 1.0 / rateDouble
            val converted = amountDouble * invertedRate
            return ConversionResult(
                convertedAmount = roundToTwoDecimals(converted),
                rate = formatRate(invertedRate),
                source = inverseRate.source,
            )
        }

        // Static fallback
        val fallbackRate = StaticRates.getRate(fromCurrency, toCurrency)
        if (fallbackRate != null) {
            val rateDouble = fallbackRate.toDoubleOrNull() ?: return null
            val converted = amountDouble * rateDouble
            return ConversionResult(
                convertedAmount = roundToTwoDecimals(converted),
                rate = fallbackRate,
                source = "static_fallback",
            )
        }

        return null
    }

    /**
     * Compute total spend for a list of expenses, converted to a single [baseCurrency].
     * Expenses already in [baseCurrency] are summed directly.
     * Returns the total as a String-encoded decimal, plus a list of any expenses
     * that could not be converted.
     */
    suspend fun aggregateTripTotal(
        expenses: List<Expense>,
        baseCurrency: String,
    ): TripTotalResult {
        var total = 0.0
        val unconverted = mutableListOf<Expense>()

        for (expense in expenses) {
            if (expense.currency == baseCurrency) {
                val amt = expense.amount.toDoubleOrNull()
                if (amt != null) total += amt
                else unconverted.add(expense)
            } else {
                val result = convert(expense.amount, expense.currency, baseCurrency, expense.date)
                if (result != null) {
                    val converted = result.convertedAmount.toDoubleOrNull()
                    if (converted != null) total += converted
                    else unconverted.add(expense)
                } else {
                    unconverted.add(expense)
                }
            }
        }

        return TripTotalResult(
            total = roundToTwoDecimals(total),
            baseCurrency = baseCurrency,
            unconvertedExpenses = unconverted,
        )
    }

    private suspend fun findRate(
        from: String,
        to: String,
        date: LocalDate?,
    ): ExchangeRate? {
        if (date != null) {
            val dateRate = exchangeRateRepo.getRate(from, to, date)
            if (dateRate != null) return dateRate
        }
        return exchangeRateRepo.getLatestRate(from, to)
    }
}

/**
 * Round a Double to two decimal places and format as a String.
 * Uses integer arithmetic to avoid floating-point formatting issues.
 */
internal fun roundToTwoDecimals(value: Double): String {
    val cents = (value * 100).roundToLong()
    val whole = cents / 100
    val frac = kotlin.math.abs(cents % 100)
    return if (cents < 0 && whole == 0L) "-0.${frac.toString().padStart(2, '0')}"
    else "$whole.${frac.toString().padStart(2, '0')}"
}

internal fun formatRate(rate: Double): String {
    // Format rate with enough precision (up to 6 decimal places, trimming trailing zeros)
    val formatted = ((rate * 1_000_000).roundToLong().toDouble() / 1_000_000).toString()
    return formatted
}

/**
 * Result of a single currency conversion.
 */
data class ConversionResult(
    val convertedAmount: String,
    val rate: String,
    val source: String,  // "manual", "api", "static_fallback", "identity"
)

/**
 * Result of aggregating trip expenses into a single base currency total.
 */
data class TripTotalResult(
    val total: String,
    val baseCurrency: String,
    val unconvertedExpenses: List<Expense>,
)
