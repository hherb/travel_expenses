package com.travelexpenses.currency

/**
 * Static fallback exchange rates for common currency pairs.
 * Used when no API-fetched or manually-entered rates are available.
 * These are approximate rates and should only be used as a rough fallback.
 */
object StaticRates {

    // Rates relative to USD (approximate, as of early 2026)
    private val ratesVsUsd: Map<String, Double> = mapOf(
        "USD" to 1.0,
        "EUR" to 0.92,
        "GBP" to 0.79,
        "JPY" to 149.50,
        "CHF" to 0.88,
        "CAD" to 1.36,
        "AUD" to 1.53,
        "NZD" to 1.67,
        "SEK" to 10.42,
        "NOK" to 10.55,
        "DKK" to 6.88,
        "CNY" to 7.24,
        "HKD" to 7.81,
        "SGD" to 1.34,
        "KRW" to 1315.0,
        "INR" to 83.10,
        "BRL" to 4.97,
        "MXN" to 17.15,
        "ZAR" to 18.65,
        "THB" to 35.20,
    )

    /**
     * Get a static fallback rate from [fromCurrency] to [toCurrency].
     * Returns null if either currency is not in the static table.
     */
    fun getRate(fromCurrency: String, toCurrency: String): Double? {
        val fromRate = ratesVsUsd[fromCurrency] ?: return null
        val toRate = ratesVsUsd[toCurrency] ?: return null

        if (fromRate == 0.0) return null

        // Cross rate: convert from -> USD -> to
        return toRate / fromRate
    }
}
