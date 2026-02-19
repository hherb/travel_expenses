package com.travelexpenses

import com.travelexpenses.db.TravelExpensesDb
import com.travelexpenses.repository.ExchangeRate
import com.travelexpenses.repository.SqlDelightExchangeRateRepository
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ExchangeRateRepositoryTest {

    private lateinit var db: TravelExpensesDb
    private lateinit var repo: SqlDelightExchangeRateRepository
    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        val driver = createInMemoryDriver()
        db = TravelExpensesDb(driver)
        repo = SqlDelightExchangeRateRepository(db, testDispatcher)
    }

    private fun makeRate(
        from: String = "USD",
        to: String = "EUR",
        rate: String = "0.92",
        date: LocalDate = LocalDate(2026, 1, 15),
        source: String = "api",
    ) = ExchangeRate(
        fromCurrency = from,
        toCurrency = to,
        rate = rate,
        date = date,
        source = source,
        fetchedAt = Clock.System.now(),
    )

    @Test
    fun upsertAndGetRate_roundTrips() = runTest(testDispatcher) {
        val rate = makeRate(from = "USD", to = "JPY", rate = "150.25", date = LocalDate(2026, 2, 1))
        repo.upsertRate(rate)

        val result = repo.getRate("USD", "JPY", LocalDate(2026, 2, 1))
        assertNotNull(result)
        assertEquals("USD", result.fromCurrency)
        assertEquals("JPY", result.toCurrency)
        assertEquals("150.25", result.rate)
        assertEquals(LocalDate(2026, 2, 1), result.date)
        assertEquals("api", result.source)
    }

    @Test
    fun getRate_returnsNullForMissing() = runTest(testDispatcher) {
        val result = repo.getRate("USD", "GBP", LocalDate(2026, 1, 1))
        assertNull(result)
    }

    @Test
    fun upsertRate_updatesExistingRate() = runTest(testDispatcher) {
        val date = LocalDate(2026, 1, 15)
        repo.upsertRate(makeRate(rate = "0.90", date = date))
        repo.upsertRate(makeRate(rate = "0.93", date = date))

        val result = repo.getRate("USD", "EUR", date)
        assertNotNull(result)
        assertEquals("0.93", result.rate, "Upsert should overwrite the existing rate")
    }

    @Test
    fun getLatestRate_returnsMostRecentByDate() = runTest(testDispatcher) {
        repo.upsertRate(makeRate(rate = "0.90", date = LocalDate(2026, 1, 10)))
        repo.upsertRate(makeRate(rate = "0.92", date = LocalDate(2026, 1, 15)))
        repo.upsertRate(makeRate(rate = "0.91", date = LocalDate(2026, 1, 12)))

        val latest = repo.getLatestRate("USD", "EUR")
        assertNotNull(latest)
        assertEquals("0.92", latest.rate, "Should return the rate with the latest date")
        assertEquals(LocalDate(2026, 1, 15), latest.date)
    }

    @Test
    fun getRatesForCurrencyPair_returnsAllSortedByDateDesc() = runTest(testDispatcher) {
        repo.upsertRate(makeRate(rate = "0.90", date = LocalDate(2026, 1, 10)))
        repo.upsertRate(makeRate(rate = "0.92", date = LocalDate(2026, 1, 15)))
        repo.upsertRate(makeRate(rate = "0.91", date = LocalDate(2026, 1, 12)))

        val rates = repo.getRatesForCurrencyPair("USD", "EUR")
        assertEquals(3, rates.size)
        assertEquals(LocalDate(2026, 1, 15), rates[0].date, "Most recent first")
        assertEquals(LocalDate(2026, 1, 12), rates[1].date)
        assertEquals(LocalDate(2026, 1, 10), rates[2].date)
    }

    @Test
    fun clear_removesAllRates() = runTest(testDispatcher) {
        repo.upsertRate(makeRate(from = "USD", to = "EUR"))
        repo.upsertRate(makeRate(from = "USD", to = "JPY"))
        repo.clear()

        assertNull(repo.getLatestRate("USD", "EUR"))
        assertNull(repo.getLatestRate("USD", "JPY"))
    }
}
