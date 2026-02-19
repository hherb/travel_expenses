package com.travelexpenses.repository

import com.travelexpenses.db.TravelExpensesDb
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlin.coroutines.CoroutineContext

class SqlDelightExchangeRateRepository(
    private val db: TravelExpensesDb,
    private val queryContext: CoroutineContext,
) : ExchangeRateRepository {

    override suspend fun upsertRate(rate: ExchangeRate) = withContext(queryContext) {
        db.exchangeRateQueries.upsertRate(
            from_currency = rate.fromCurrency,
            to_currency = rate.toCurrency,
            rate = rate.rate,
            date = rate.date.toString(),
            source = rate.source,
            fetched_at = rate.fetchedAt.toString(),
        )
    }

    override suspend fun getRate(
        fromCurrency: String,
        toCurrency: String,
        date: LocalDate,
    ): ExchangeRate? = withContext(queryContext) {
        db.exchangeRateQueries.selectRate(fromCurrency, toCurrency, date.toString())
            .executeAsOneOrNull()?.toExchangeRate()
    }

    override suspend fun getLatestRate(
        fromCurrency: String,
        toCurrency: String,
    ): ExchangeRate? = withContext(queryContext) {
        db.exchangeRateQueries.selectLatestRate(fromCurrency, toCurrency)
            .executeAsOneOrNull()?.toExchangeRate()
    }

    override suspend fun getRatesForCurrencyPair(
        fromCurrency: String,
        toCurrency: String,
    ): List<ExchangeRate> = withContext(queryContext) {
        db.exchangeRateQueries.selectRatesForCurrencyPair(fromCurrency, toCurrency)
            .executeAsList().map { it.toExchangeRate() }
    }

    override suspend fun clear() = withContext(queryContext) {
        db.exchangeRateQueries.deleteAll()
    }

    private fun com.travelexpenses.db.Exchange_rate.toExchangeRate(): ExchangeRate {
        return ExchangeRate(
            fromCurrency = from_currency,
            toCurrency = to_currency,
            rate = rate,
            date = LocalDate.parse(date),
            source = source,
            fetchedAt = Instant.parse(fetched_at),
        )
    }
}
