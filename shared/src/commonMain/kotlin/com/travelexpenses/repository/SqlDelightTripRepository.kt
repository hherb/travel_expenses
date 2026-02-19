package com.travelexpenses.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.travelexpenses.db.TravelExpensesDb
import com.travelexpenses.model.Trip
import com.travelexpenses.model.TripId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlin.coroutines.CoroutineContext

class SqlDelightTripRepository(
    private val db: TravelExpensesDb,
    private val queryContext: CoroutineContext,
) : TripRepository {

    override suspend fun getTrip(tripId: TripId): Trip? = withContext(queryContext) {
        db.materializedStateQueries.selectTripById(tripId).executeAsOneOrNull()?.toTrip()
    }

    override suspend fun getActiveTrips(): List<Trip> = withContext(queryContext) {
        db.materializedStateQueries.selectActiveTrips().executeAsList().map { it.toTrip() }
    }

    override suspend fun getArchivedTrips(): List<Trip> = withContext(queryContext) {
        db.materializedStateQueries.selectArchivedTrips().executeAsList().map { it.toTrip() }
    }

    override fun observeActiveTrips(): Flow<List<Trip>> {
        return db.materializedStateQueries.selectActiveTrips()
            .asFlow()
            .mapToList(queryContext)
            .map { rows -> rows.map { it.toTrip() } }
    }

    private fun com.travelexpenses.db.Trip.toTrip(): Trip {
        return Trip(
            id = id,
            name = name,
            destination = destination,
            startDate = start_date?.let { LocalDate.parse(it) },
            endDate = end_date?.let { LocalDate.parse(it) },
            baseCurrency = base_currency,
            createdAt = Instant.parse(created_at),
        )
    }
}
