package com.travelexpenses.currency

/**
 * Static fallback exchange rates for common currency pairs.
 * Used when no rate is available from the repository (no API/manual rate cached).
 * These are approximate rates and should be treated as rough estimates.
 */
object StaticRates {

    // Rates are expressed as "1 unit of FROM = X units of TO"
    // Approximate rates as of early 2025 — intentionally conservative.
    private val rates: Map<Pair<String, String>, String> = mapOf(
        // USD base
        ("USD" to "EUR") to "0.92",
        ("USD" to "GBP") to "0.79",
        ("USD" to "JPY") to "149.50",
        ("USD" to "CHF") to "0.88",
        ("USD" to "CAD") to "1.36",
        ("USD" to "AUD") to "1.53",
        ("USD" to "NZD") to "1.67",
        ("USD" to "CNY") to "7.24",
        ("USD" to "INR") to "83.50",
        ("USD" to "BRL") to "4.95",
        ("USD" to "MXN") to "17.15",
        ("USD" to "KRW") to "1330.00",
        ("USD" to "SGD") to "1.34",
        ("USD" to "HKD") to "7.82",
        ("USD" to "SEK") to "10.45",
        ("USD" to "NOK") to "10.55",
        ("USD" to "DKK") to "6.88",
        ("USD" to "THB") to "35.50",
        ("USD" to "ZAR") to "18.80",
        ("USD" to "TRY") to "30.50",
        ("USD" to "PLN") to "4.02",
        ("USD" to "CZK") to "23.10",
        ("USD" to "HUF") to "358.00",
        ("USD" to "ILS") to "3.65",
        ("USD" to "TWD") to "31.50",
        ("USD" to "PHP") to "56.00",
        ("USD" to "MYR") to "4.72",
        ("USD" to "IDR") to "15700.00",

        // EUR base (common travel pairs)
        ("EUR" to "USD") to "1.09",
        ("EUR" to "GBP") to "0.86",
        ("EUR" to "CHF") to "0.96",
        ("EUR" to "JPY") to "162.50",
        ("EUR" to "SEK") to "11.35",
        ("EUR" to "NOK") to "11.45",
        ("EUR" to "DKK") to "7.46",
        ("EUR" to "PLN") to "4.37",
        ("EUR" to "CZK") to "25.10",
        ("EUR" to "HUF") to "389.00",

        // GBP base
        ("GBP" to "USD") to "1.27",
        ("GBP" to "EUR") to "1.17",
        ("GBP" to "JPY") to "189.00",

        // JPY base (inverse for convenience)
        ("JPY" to "USD") to "0.00669",
        ("JPY" to "EUR") to "0.00615",
    )

    /**
     * Get a static fallback rate for the given currency pair.
     * Returns null if no static rate is available.
     */
    fun getRate(fromCurrency: String, toCurrency: String): String? {
        return rates[fromCurrency to toCurrency]
    }

    /**
     * Check if a static rate exists for the given pair.
     */
    fun hasRate(fromCurrency: String, toCurrency: String): Boolean {
        return rates.containsKey(fromCurrency to toCurrency)
    }

    /**
     * All currency codes that have at least one static rate defined.
     */
    val supportedCurrencies: Set<String> by lazy {
        rates.keys.flatMap { (from, to) -> listOf(from, to) }.toSet()
    }
}
