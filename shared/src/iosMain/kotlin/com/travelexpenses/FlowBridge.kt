package com.travelexpenses

import com.travelexpenses.model.Category
import com.travelexpenses.model.Expense
import com.travelexpenses.model.Tag
import com.travelexpenses.model.Trip
import com.travelexpenses.repository.CategoryRepository
import com.travelexpenses.repository.ExpenseRepository
import com.travelexpenses.repository.TagRepository
import com.travelexpenses.repository.TripRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Generic Flow wrapper that delivers updates to iOS via callback.
 *
 * Swift cannot directly consume Kotlin's Flow<T>, so this bridge class
 * collects the flow on the Main dispatcher and invokes [onEach] with each
 * emitted value.  Call [cancel] when the subscriber is no longer needed.
 *
 * Usage from Swift:
 *   let bridge = TripFlowBridge(repo: tripRepo)
 *   bridge.collect(onEach: { trips in ... }, onError: { _ in })
 *   // later:
 *   bridge.cancel()
 */
class TripFlowBridge(private val repo: TripRepository) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    fun collectActiveTrips(onEach: (List<Trip>) -> Unit, onError: (Throwable) -> Unit) {
        collectFlow(repo.observeActiveTrips(), onEach, onError)
    }

    fun collectAllTrips(onEach: (List<Trip>) -> Unit, onError: (Throwable) -> Unit) {
        collectFlow(repo.observeAllTrips(), onEach, onError)
    }

    fun cancel() { scope.cancel() }

    private fun <T> collectFlow(flow: Flow<T>, onEach: (T) -> Unit, onError: (Throwable) -> Unit) {
        scope.launch {
            try {
                flow.collect { onEach(it) }
            } catch (e: Exception) {
                onError(e)
            }
        }
    }
}

class ExpenseFlowBridge(private val repo: ExpenseRepository) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var job: Job? = null

    fun collectForTrip(tripId: String, onEach: (List<Expense>) -> Unit, onError: (Throwable) -> Unit) {
        job?.cancel()
        job = scope.launch {
            try {
                repo.observeExpensesForTrip(tripId).collect { onEach(it) }
            } catch (e: Exception) {
                onError(e)
            }
        }
    }

    fun cancel() { scope.cancel() }
}

class CategoryFlowBridge(private val repo: CategoryRepository) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    fun collectAll(onEach: (List<Category>) -> Unit, onError: (Throwable) -> Unit) {
        scope.launch {
            try {
                repo.observeAllCategories().collect { onEach(it) }
            } catch (e: Exception) {
                onError(e)
            }
        }
    }

    fun cancel() { scope.cancel() }
}

class TagFlowBridge(private val repo: TagRepository) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    fun collectAll(onEach: (List<Tag>) -> Unit, onError: (Throwable) -> Unit) {
        scope.launch {
            try {
                repo.observeAllTags().collect { onEach(it) }
            } catch (e: Exception) {
                onError(e)
            }
        }
    }

    fun cancel() { scope.cancel() }
}
