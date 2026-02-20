package com.travelexpenses

import com.travelexpenses.currency.*
import com.travelexpenses.repository.ExchangeRate
import com.travelexpenses.repository.SqlDelightExchangeRateRepository
import com.travelexpenses.db.TravelExpensesDb
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlin.test.*

class CurrencyConverterTest {

    private lateinit var db: TravelExpensesDb
    private lateinit var exchangeRateRepo: SqlDelightExchangeRateRepository
    private lateinit var converter: CurrencyConverter

    @BeforeTest
    fun setUp() {
        val driver = createInMemoryDriver()
        db = TravelExpensesDb(driver)
        val testDispatcher = StandardTestDispatcher()
        exchangeRateRepo = SqlDelightExchangeRateRepository(db, testDispatcher)
        converter = CurrencyConverter(exchangeRateRepo)
    }

    // -- Identity conversion --

    @Test
    fun sameCurrencyReturnsIdentity() = runTest {
        val result = converter.convert("100.00", "USD", "USD")
        assertNotNull(result)
        assertEquals("100.00", result.convertedAmount)
        assertEquals("1", result.rate)
        assertEquals(RateSource.IDENTITY, result.source)
    }

    // -- Exact date rate --

    @Test
    fun usesExactDateRate() = runTest {
        val date = LocalDate(2026, 1, 15)
        exchangeRateRepo.upsertRate(
            ExchangeRate("USD", "EUR", "0.92", date, "api", Clock.System.now())
        )

        val result = converter.convert("100.00", "USD", "EUR", date)
        assertNotNull(result)
        assertEquals("92.00", result.convertedAmount)
        assertEquals("0.92", result.rate)
        assertEquals(RateSource.EXACT_DATE, result.source)
    }

    // -- Latest rate fallback --

    @Test
    fun fallsBackToLatestRate() = runTest {
        val date = LocalDate(2026, 1, 10)
        exchangeRateRepo.upsertRate(
            ExchangeRate("USD", "EUR", "0.93", date, "api", Clock.System.now())
        )

        // Request conversion for a different date
        val result = converter.convert("100.00", "USD", "EUR", LocalDate(2026, 1, 20))
        assertNotNull(result)
        assertEquals("93.00", result.convertedAmount)
        assertEquals(RateSource.LATEST, result.source)
    }

    // -- Inverse rate --

    @Test
    fun usesInverseRateWhenDirectNotAvailable() = runTest {
        val date = LocalDate(2026, 1, 15)
        // We have EUR->USD rate but need USD->EUR
        exchangeRateRepo.upsertRate(
            ExchangeRate("EUR", "USD", "1.09", date, "api", Clock.System.now())
        )

        val result = converter.convert("100.00", "USD", "EUR", date)
        assertNotNull(result)
        assertEquals(RateSource.INVERSE, result.source)
        // 100 / 1.09 ~ 91.74
        val converted = result.convertedAmount.toDouble()
        assertTrue(converted > 91.0 && converted < 92.0)
    }

    // -- Static fallback --

    @Test
    fun fallsBackToStaticRates() = runTest {
        // No rates in repository at all
        val result = converter.convert("100.00", "USD", "EUR")
        assertNotNull(result)
        assertEquals(RateSource.STATIC_FALLBACK, result.source)
        val converted = result.convertedAmount.toDouble()
        assertTrue(converted > 85.0 && converted < 100.0, "Static EUR/USD rate should be reasonable")
    }

    @Test
    fun returnsNullForUnknownCurrencyPair() = runTest {
        val result = converter.convert("100.00", "USD", "XYZ")
        assertNull(result)
    }

    // -- Invalid input --

    @Test
    fun returnsNullForInvalidAmount() = runTest {
        val result = converter.convert("not-a-number", "USD", "EUR")
        assertNull(result)
    }

    // -- Trip total computation --

    @Test
    fun computesTripTotalSameCurrency() = runTest {
        val expenses = listOf(
            ExpenseAmount("10.00", "USD"),
            ExpenseAmount("20.00", "USD"),
            ExpenseAmount("30.00", "USD"),
        )

        val result = converter.computeTripTotal(expenses, "USD")
        assertEquals("60.00", result.total)
        assertEquals("USD", result.baseCurrency)
        assertTrue(result.unconvertibleExpenses.isEmpty())
    }

    @Test
    fun computesTripTotalMixedCurrencies() = runTest {
        val date = LocalDate(2026, 1, 15)
        exchangeRateRepo.upsertRate(
            ExchangeRate("EUR", "USD", "1.09", date, "api", Clock.System.now())
        )

        val expenses = listOf(
            ExpenseAmount("100.00", "USD", date),
            ExpenseAmount("50.00", "EUR", date),
        )

        val result = converter.computeTripTotal(expenses, "USD")
        val total = result.total.toDouble()
        // 100 USD + (50 * 1.09) EUR->USD = 100 + 54.50 = 154.50
        assertTrue(total > 150.0 && total < 160.0)
        assertTrue(result.unconvertibleExpenses.isEmpty())
    }

    @Test
    fun tracksUnconvertibleExpenses() = runTest {
        val expenses = listOf(
            ExpenseAmount("100.00", "USD"),
            ExpenseAmount("50.00", "XYZ"),  // Unknown currency
        )

        val result = converter.computeTripTotal(expenses, "USD")
        assertEquals("100.00", result.total)
        assertEquals(1, result.unconvertibleExpenses.size)
        assertEquals("XYZ", result.unconvertibleExpenses[0].currency)
    }

    @Test
    fun computesTripTotalEmptyList() = runTest {
        val result = converter.computeTripTotal(emptyList(), "USD")
        assertEquals("0.00", result.total)
        assertTrue(result.unconvertibleExpenses.isEmpty())
    }
}

class StaticRatesTest {

    @Test
    fun returnsRateForKnownPair() {
        val rate = StaticRates.getRate("USD", "EUR")
        assertNotNull(rate)
        assertTrue(rate > 0.8 && rate < 1.0, "USD->EUR rate should be around 0.92")
    }

    @Test
    fun returnsInverseRate() {
        val usdToEur = StaticRates.getRate("USD", "EUR")
        val eurToUsd = StaticRates.getRate("EUR", "USD")
        assertNotNull(usdToEur)
        assertNotNull(eurToUsd)
        // They should be inverses of each other (approximately)
        val product = usdToEur * eurToUsd
        assertTrue(product > 0.99 && product < 1.01)
    }

    @Test
    fun returnsNullForUnknownCurrency() {
        assertNull(StaticRates.getRate("USD", "XYZ"))
        assertNull(StaticRates.getRate("XYZ", "USD"))
    }

    @Test
    fun crossRateIsConsistent() {
        // EUR -> GBP via USD should be consistent
        val eurToGbp = StaticRates.getRate("EUR", "GBP")
        assertNotNull(eurToGbp)
        assertTrue(eurToGbp > 0.7 && eurToGbp < 1.0, "EUR->GBP rate should be reasonable")
    }

    @Test
    fun identityCurrencyReturnsOne() {
        val rate = StaticRates.getRate("USD", "USD")
        assertNotNull(rate)
        assertEquals(1.0, rate)
    }
}
