package com.travelexpenses

import com.travelexpenses.currency.CurrencyConverter
import com.travelexpenses.currency.StaticRates
import com.travelexpenses.repository.ExchangeRate
import com.travelexpenses.repository.ExchangeRateRepository
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * In-memory ExchangeRateRepository for testing CurrencyConverter without a database.
 */
class InMemoryExchangeRateRepository : ExchangeRateRepository {
    private val rates = mutableListOf<ExchangeRate>()

    override suspend fun upsertRate(rate: ExchangeRate) {
        rates.removeAll { it.fromCurrency == rate.fromCurrency && it.toCurrency == rate.toCurrency && it.date == rate.date }
        rates.add(rate)
    }

    override suspend fun getRate(fromCurrency: String, toCurrency: String, date: LocalDate): ExchangeRate? {
        return rates.find { it.fromCurrency == fromCurrency && it.toCurrency == toCurrency && it.date == date }
    }

    override suspend fun getLatestRate(fromCurrency: String, toCurrency: String): ExchangeRate? {
        return rates.filter { it.fromCurrency == fromCurrency && it.toCurrency == toCurrency }
            .maxByOrNull { it.date }
    }

    override suspend fun getRatesForCurrencyPair(fromCurrency: String, toCurrency: String): List<ExchangeRate> {
        return rates.filter { it.fromCurrency == fromCurrency && it.toCurrency == toCurrency }
            .sortedByDescending { it.date }
    }

    override suspend fun clear() {
        rates.clear()
    }
}

class CurrencyConverterTest {

    private val repo = InMemoryExchangeRateRepository()
    private val converter = CurrencyConverter(repo)

    private fun makeRate(from: String, to: String, rate: String, date: LocalDate = LocalDate(2024, 1, 15)): ExchangeRate {
        return ExchangeRate(
            fromCurrency = from,
            toCurrency = to,
            rate = rate,
            date = date,
            source = "manual",
            fetchedAt = Clock.System.now(),
        )
    }

    // -- Basic conversion -------------------------------------------------

    @Test
    fun sameCurrencyReturnsIdentity() = runTest {
        val result = converter.convert("100.00", "USD", "USD")
        assertNotNull(result)
        assertEquals("100.00", result.convertedAmount)
        assertEquals("1", result.rate)
        assertEquals("identity", result.source)
    }

    @Test
    fun directRateConversion() = runTest {
        repo.upsertRate(makeRate("USD", "EUR", "0.92"))
        val result = converter.convert("100.00", "USD", "EUR")
        assertNotNull(result)
        assertEquals("92.00", result.convertedAmount)
        assertEquals("0.92", result.rate)
        assertEquals("manual", result.source)
    }

    @Test
    fun inverseRateConversion() = runTest {
        repo.upsertRate(makeRate("EUR", "USD", "1.09"))
        val result = converter.convert("100.00", "USD", "EUR")
        assertNotNull(result)
        // 100 / 1.09 ≈ 91.74
        assertEquals("91.74", result.convertedAmount)
    }

    @Test
    fun dateSpecificRatePreferred() = runTest {
        repo.upsertRate(makeRate("USD", "EUR", "0.90", LocalDate(2024, 1, 10)))
        repo.upsertRate(makeRate("USD", "EUR", "0.95", LocalDate(2024, 1, 15)))
        val result = converter.convert("100.00", "USD", "EUR", date = LocalDate(2024, 1, 15))
        assertNotNull(result)
        assertEquals("95.00", result.convertedAmount)
    }

    @Test
    fun fallsBackToLatestWhenNoDateMatch() = runTest {
        repo.upsertRate(makeRate("USD", "EUR", "0.92", LocalDate(2024, 1, 10)))
        val result = converter.convert("100.00", "USD", "EUR", date = LocalDate(2024, 6, 1))
        assertNotNull(result)
        assertEquals("92.00", result.convertedAmount)
    }

    // -- Static fallback --------------------------------------------------

    @Test
    fun staticFallbackUsedWhenNoRepoRate() = runTest {
        val result = converter.convert("100.00", "USD", "EUR")
        assertNotNull(result)
        assertEquals("static_fallback", result.source)
        // Should use StaticRates USD->EUR rate of 0.92
        assertEquals("92.00", result.convertedAmount)
    }

    @Test
    fun noConversionForUnknownPair() = runTest {
        val result = converter.convert("100.00", "XYZ", "ABC")
        assertNull(result)
    }

    @Test
    fun invalidAmountReturnsNull() = runTest {
        val result = converter.convert("not-a-number", "USD", "EUR")
        assertNull(result)
    }

    // -- Static rates table -----------------------------------------------

    @Test
    fun staticRatesHaveCommonPairs() {
        assertNotNull(StaticRates.getRate("USD", "EUR"))
        assertNotNull(StaticRates.getRate("USD", "GBP"))
        assertNotNull(StaticRates.getRate("USD", "JPY"))
        assertNotNull(StaticRates.getRate("EUR", "USD"))
        assertNotNull(StaticRates.getRate("GBP", "USD"))
    }

    @Test
    fun staticRatesReturnNullForUnknownPair() {
        assertNull(StaticRates.getRate("XYZ", "ABC"))
    }

    // -- Trip aggregation -------------------------------------------------

    @Test
    fun aggregateSameCurrencyExpenses() = runTest {
        val expenses = listOf(
            TestHelpers.makeExpense(id = "e1", amount = "25.50", currency = "USD"),
            TestHelpers.makeExpense(id = "e2", amount = "30.00", currency = "USD"),
            TestHelpers.makeExpense(id = "e3", amount = "15.75", currency = "USD"),
        )
        val result = converter.aggregateTripTotal(expenses, "USD")
        assertEquals("71.25", result.total)
        assertEquals("USD", result.baseCurrency)
        assertTrue(result.unconvertedExpenses.isEmpty())
    }

    @Test
    fun aggregateMixedCurrencyExpenses() = runTest {
        repo.upsertRate(makeRate("EUR", "USD", "1.09"))
        val expenses = listOf(
            TestHelpers.makeExpense(id = "e1", amount = "50.00", currency = "USD"),
            TestHelpers.makeExpense(id = "e2", amount = "40.00", currency = "EUR"),
        )
        val result = converter.aggregateTripTotal(expenses, "USD")
        // 50 + 40 * 1.09 = 50 + 43.60 = 93.60
        assertEquals("93.60", result.total)
        assertTrue(result.unconvertedExpenses.isEmpty())
    }

    @Test
    fun aggregateTracksUnconvertedExpenses() = runTest {
        val expenses = listOf(
            TestHelpers.makeExpense(id = "e1", amount = "50.00", currency = "USD"),
            TestHelpers.makeExpense(id = "e2", amount = "100.00", currency = "XYZ"),
        )
        val result = converter.aggregateTripTotal(expenses, "USD")
        assertEquals("50.00", result.total)
        assertEquals(1, result.unconvertedExpenses.size)
        assertEquals("e2", result.unconvertedExpenses[0].id)
    }

    @Test
    fun aggregateEmptyListReturnsZero() = runTest {
        val result = converter.aggregateTripTotal(emptyList(), "USD")
        assertEquals("0.00", result.total)
        assertTrue(result.unconvertedExpenses.isEmpty())
    }
}
