package com.travelexpenses.currency

import com.travelexpenses.repository.ExchangeRate
import com.travelexpenses.repository.ExchangeRateRepository
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Fetches exchange rates from frankfurter.app (free, no API key required)
 * and caches them in the local ExchangeRateRepository.
 *
 * Usage is opportunistic: the app works fully offline using cached rates
 * and static fallback rates. This service fetches fresh rates when
 * connectivity is available.
 */
class ExchangeRateService(
    private val exchangeRateRepo: ExchangeRateRepository,
    httpClient: HttpClient? = null,
) {
    private val client: HttpClient = httpClient ?: HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }

    /**
     * Fetch the latest exchange rates for [baseCurrency] against [targetCurrencies].
     * Results are cached in the local repository.
     *
     * @return list of fetched rates, or empty list if the request fails
     */
    suspend fun fetchLatestRates(
        baseCurrency: String,
        targetCurrencies: List<String>,
    ): List<ExchangeRate> {
        if (targetCurrencies.isEmpty()) return emptyList()

        return try {
            val symbols = targetCurrencies.joinToString(",")
            val response: FrankfurterLatestResponse = client.get(
                "https://api.frankfurter.app/latest"
            ) {
                parameter("from", baseCurrency)
                parameter("to", symbols)
            }.body()

            val now = Clock.System.now()
            val date = LocalDate.parse(response.date)

            response.rates.map { (currency, rate) ->
                val exchangeRate = ExchangeRate(
                    fromCurrency = baseCurrency,
                    toCurrency = currency,
                    rate = rate.toString(),
                    date = date,
                    source = "api",
                    fetchedAt = now,
                )
                exchangeRateRepo.upsertRate(exchangeRate)
                exchangeRate
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Fetch historical exchange rates for a specific [date].
     */
    suspend fun fetchHistoricalRates(
        baseCurrency: String,
        targetCurrencies: List<String>,
        date: LocalDate,
    ): List<ExchangeRate> {
        if (targetCurrencies.isEmpty()) return emptyList()

        return try {
            val symbols = targetCurrencies.joinToString(",")
            val response: FrankfurterLatestResponse = client.get(
                "https://api.frankfurter.app/${date}"
            ) {
                parameter("from", baseCurrency)
                parameter("to", symbols)
            }.body()

            val now = Clock.System.now()
            val responseDate = LocalDate.parse(response.date)

            response.rates.map { (currency, rate) ->
                val exchangeRate = ExchangeRate(
                    fromCurrency = baseCurrency,
                    toCurrency = currency,
                    rate = rate.toString(),
                    date = responseDate,
                    source = "api",
                    fetchedAt = now,
                )
                exchangeRateRepo.upsertRate(exchangeRate)
                exchangeRate
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun close() {
        client.close()
    }
}

/**
 * Response shape from frankfurter.app /latest and /{date} endpoints.
 */
@Serializable
internal data class FrankfurterLatestResponse(
    val base: String,
    val date: String,
    val rates: Map<String, Double>,
)
