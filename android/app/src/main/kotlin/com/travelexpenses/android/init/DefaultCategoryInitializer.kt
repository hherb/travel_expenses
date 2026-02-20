package com.travelexpenses.android.init

import com.travelexpenses.event.ExpenseEvent
import com.travelexpenses.repository.CategoryRepository
import com.travelexpenses.repository.EventLogRepository
import com.travelexpenses.validation.DefaultCategories
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import java.util.UUID

/**
 * Initializes default categories on first launch.
 * Checks if any categories exist, and if not, creates events for all 9 defaults.
 */
class DefaultCategoryInitializer(
    private val categoryRepo: CategoryRepository,
    private val eventLogRepo: EventLogRepository,
    private val deviceId: String,
    private val scope: CoroutineScope,
) {
    fun initializeIfNeeded() {
        scope.launch {
            val existing = categoryRepo.getAllCategories()
            if (existing.isNotEmpty()) return@launch

            val now = Clock.System.now()
            DefaultCategories.all.forEachIndexed { index, category ->
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
