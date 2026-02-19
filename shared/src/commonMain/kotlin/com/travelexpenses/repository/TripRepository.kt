package com.travelexpenses.repository

import com.travelexpenses.model.Trip
import com.travelexpenses.model.TripId
import kotlinx.coroutines.flow.Flow

interface TripRepository {
    suspend fun getTrip(tripId: TripId): Trip?
    suspend fun getActiveTrips(): List<Trip>
    suspend fun getArchivedTrips(): List<Trip>
    fun observeActiveTrips(): Flow<List<Trip>>
}
