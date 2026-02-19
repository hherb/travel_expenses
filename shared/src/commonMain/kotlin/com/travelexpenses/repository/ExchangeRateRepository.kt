package com.travelexpenses.repository

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

data class ExchangeRate(
    val fromCurrency: String,
    val toCurrency: String,
    val rate: String,
    val date: LocalDate,
    val source: String,
    val fetchedAt: Instant,
)

interface ExchangeRateRepository {
    suspend fun upsertRate(rate: ExchangeRate)
    suspend fun getRate(fromCurrency: String, toCurrency: String, date: LocalDate): ExchangeRate?
    suspend fun getLatestRate(fromCurrency: String, toCurrency: String): ExchangeRate?
    suspend fun getRatesForCurrencyPair(fromCurrency: String, toCurrency: String): List<ExchangeRate>
    suspend fun clear()
}
