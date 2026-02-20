package com.travelexpenses.android.init

import com.travelexpenses.event.ExpenseEvent
import com.travelexpenses.repository.CategoryRepository
import com.travelexpenses.repository.EventLogRepository
import com.travelexpenses.validation.DefaultCategories
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import java.util.UUID

/**
 * Initializes default categories on first launch.
 * Checks if any categories exist, and if not, creates events for all 9 defaults.
 * Uses a mutex to prevent double-initialization from concurrent calls.
 */
class DefaultCategoryInitializer(
    private val categoryRepo: CategoryRepository,
    private val eventLogRepo: EventLogRepository,
    private val deviceId: String,
    private val scope: CoroutineScope,
) {
    private val mutex = Mutex()

    fun initializeIfNeeded() {
        scope.launch {
            mutex.withLock {
                val existing = categoryRepo.getAllCategories()
                if (existing.isNotEmpty()) return@withLock

                val now = Clock.System.now()
                for (category in DefaultCategories.all) {
                    val seqNum = eventLogRepo.count() + 1
                    val event = ExpenseEvent.CategoryCreated(
                        eventId = UUID.randomUUID().toString(),
                        timestamp = now,
                        sequenceNumber = seqNum,
                        deviceId = deviceId,
                        category = category,
                    )
                    eventLogRepo.append(event)
                }
            }
        }
    }
}
