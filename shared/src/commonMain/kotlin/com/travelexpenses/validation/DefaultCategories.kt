package com.travelexpenses.validation

import com.travelexpenses.model.Category

/**
 * Default categories shipped with the app (see SPEC.md Section 3.2).
 * Users can add/rename/hide but not delete these.
 */
object DefaultCategories {

    val ALL: List<Category> = listOf(
        Category(id = "cat-transport", name = "Transport", icon = "plane", isDefault = true),
        Category(id = "cat-accommodation", name = "Accommodation", icon = "bed", isDefault = true),
        Category(id = "cat-food", name = "Food & Drink", icon = "fork", isDefault = true),
        Category(id = "cat-activities", name = "Activities", icon = "ticket", isDefault = true),
        Category(id = "cat-shopping", name = "Shopping", icon = "bag", isDefault = true),
        Category(id = "cat-communication", name = "Communication", icon = "phone", isDefault = true),
        Category(id = "cat-health", name = "Health", icon = "cross", isDefault = true),
        Category(id = "cat-fees", name = "Fees & Tips", icon = "percent", isDefault = true),
        Category(id = "cat-other", name = "Other", icon = "dots", isDefault = true),
    )

    /**
     * Generate CategoryCreated events for all default categories.
     * Intended to be called at app first-launch to seed the database.
     */
    fun asCategoryCreatedEvents(
        deviceId: String,
        sequenceNumberStart: Long,
        eventIdGenerator: () -> String,
        timestampProvider: () -> kotlinx.datetime.Instant,
    ): List<com.travelexpenses.event.ExpenseEvent.CategoryCreated> {
        return ALL.mapIndexed { index, category ->
            com.travelexpenses.event.ExpenseEvent.CategoryCreated(
                eventId = eventIdGenerator(),
                timestamp = timestampProvider(),
                sequenceNumber = sequenceNumberStart + index,
                deviceId = deviceId,
                category = category,
            )
        }
    }
}
