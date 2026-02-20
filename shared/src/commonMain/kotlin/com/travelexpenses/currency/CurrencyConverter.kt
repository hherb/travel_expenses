package com.travelexpenses.currency

import com.travelexpenses.repository.ExchangeRateRepository
import kotlin.math.round
import kotlinx.datetime.LocalDate

/**
 * Display-only currency conversion using cached exchange rates.
 *
 * Conversion priority:
 * 1. Exact date rate from ExchangeRateRepository
 * 2. Latest available rate from ExchangeRateRepository
 * 3. Static fallback rate table (common pairs)
 *
 * All amounts are String-encoded decimals for cross-platform precision.
 */
class CurrencyConverter(
    private val exchangeRateRepository: ExchangeRateRepository,
) {

    /**
     * Convert [amount] from [fromCurrency] to [toCurrency] on [date].
     * Returns the converted amount as a string-encoded decimal, or null if no rate is available.
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
                source = RateSource.IDENTITY,
            )
        }

        val amountDouble = amount.toDoubleOrNull() ?: return null

        // Try exact date rate
        if (date != null) {
            val exactRate = exchangeRateRepository.getRate(fromCurrency, toCurrency, date)
            if (exactRate != null) {
                val rateDouble = exactRate.rate.toDoubleOrNull() ?: return null
                val result = amountDouble * rateDouble
                return ConversionResult(
                    convertedAmount = formatAmount(result),
                    rate = exactRate.rate,
                    source = RateSource.EXACT_DATE,
                )
            }
        }

        // Try latest available rate
        val latestRate = exchangeRateRepository.getLatestRate(fromCurrency, toCurrency)
        if (latestRate != null) {
            val rateDouble = latestRate.rate.toDoubleOrNull() ?: return null
            val result = amountDouble * rateDouble
            return ConversionResult(
                convertedAmount = formatAmount(result),
                rate = latestRate.rate,
                source = RateSource.LATEST,
            )
        }

        // Try inverse rate (e.g., if we have EUR->USD but need USD->EUR)
        val inverseRate = if (date != null) {
            exchangeRateRepository.getRate(toCurrency, fromCurrency, date)
        } else null
            ?: exchangeRateRepository.getLatestRate(toCurrency, fromCurrency)

        if (inverseRate != null) {
            val rateDouble = inverseRate.rate.toDoubleOrNull() ?: return null
            if (rateDouble != 0.0) {
                val effectiveRate = 1.0 / rateDouble
                val result = amountDouble * effectiveRate
                return ConversionResult(
                    convertedAmount = formatAmount(result),
                    rate = formatAmount(effectiveRate),
                    source = RateSource.INVERSE,
                )
            }
        }

        // Static fallback
        val fallbackRate = StaticRates.getRate(fromCurrency, toCurrency)
        if (fallbackRate != null) {
            val result = amountDouble * fallbackRate
            return ConversionResult(
                convertedAmount = formatAmount(result),
                rate = formatAmount(fallbackRate),
                source = RateSource.STATIC_FALLBACK,
            )
        }

        return null
    }

    /**
     * Compute trip summary: total spend in [baseCurrency] from a list of expenses.
     * Returns the sum of converted amounts, or null if any conversion fails.
     */
    suspend fun computeTripTotal(
        expenses: List<ExpenseAmount>,
        baseCurrency: String,
    ): TripTotalResult {
        var total = 0.0
        val unconvertible = mutableListOf<ExpenseAmount>()

        for (expense in expenses) {
            if (expense.currency == baseCurrency) {
                val amount = expense.amount.toDoubleOrNull()
                if (amount != null) {
                    total += amount
                } else {
                    unconvertible.add(expense)
                }
                continue
            }

            val converted = convert(expense.amount, expense.currency, baseCurrency, expense.date)
            if (converted != null) {
                val convertedDouble = converted.convertedAmount.toDoubleOrNull()
                if (convertedDouble != null) {
                    total += convertedDouble
                } else {
                    unconvertible.add(expense)
                }
            } else {
                unconvertible.add(expense)
            }
        }

        return TripTotalResult(
            total = formatAmount(total),
            baseCurrency = baseCurrency,
            unconvertibleExpenses = unconvertible,
        )
    }

    private fun formatAmount(value: Double): String {
        // Round to 2 decimal places for monetary display
        val rounded = round(value * 100.0) / 100.0
        return if (rounded == rounded.toLong().toDouble()) {
            "${rounded.toLong()}.00"
        } else {
            // Ensure exactly 2 decimal places
            val str = rounded.toString()
            val dotIndex = str.indexOf('.')
            if (dotIndex == -1) {
                "$str.00"
            } else {
                val decimals = str.length - dotIndex - 1
                when {
                    decimals == 1 -> "${str}0"
                    decimals == 2 -> str
                    else -> str.substring(0, dotIndex + 3)
                }
            }
        }
    }
}

/** Result of a single currency conversion, including the applied rate and its source. */
data class ConversionResult(
    val convertedAmount: String,
    val rate: String,
    val source: RateSource,
)

/** Describes where a conversion rate was obtained from, in priority order. */
enum class RateSource {
    /** Same currency — no conversion needed. */
    IDENTITY,
    /** Exact-date rate from the exchange rate repository. */
    EXACT_DATE,
    /** Most recent rate from the exchange rate repository (date-agnostic). */
    LATEST,
    /** Computed as 1/rate from the reverse currency pair. */
    INVERSE,
    /** Approximate rate from the built-in [StaticRates] table. */
    STATIC_FALLBACK,
}

/** Lightweight representation of an expense's monetary value for aggregation. */
data class ExpenseAmount(
    val amount: String,
    val currency: String,
    val date: LocalDate? = null,
)

/** Aggregated trip total in a single base currency, with any unconvertible expenses listed separately. */
data class TripTotalResult(
    val total: String,
    val baseCurrency: String,
    val unconvertibleExpenses: List<ExpenseAmount>,
)
