package com.travelexpenses.validation

import com.travelexpenses.model.Category

/**
 * The 9 default expense categories shipped with the app (from SPEC.md Section 3.2).
 * Users can add/rename/hide these but not delete them.
 */
object DefaultCategories {

    val all: List<Category> = listOf(
        Category(id = "cat-transport", name = "Transport", icon = "plane", isDefault = true),
        Category(id = "cat-accommodation", name = "Accommodation", icon = "bed", isDefault = true),
        Category(id = "cat-food-drink", name = "Food & Drink", icon = "fork", isDefault = true),
        Category(id = "cat-activities", name = "Activities", icon = "ticket", isDefault = true),
        Category(id = "cat-shopping", name = "Shopping", icon = "bag", isDefault = true),
        Category(id = "cat-communication", name = "Communication", icon = "phone", isDefault = true),
        Category(id = "cat-health", name = "Health", icon = "cross", isDefault = true),
        Category(id = "cat-fees-tips", name = "Fees & Tips", icon = "percent", isDefault = true),
        Category(id = "cat-other", name = "Other", icon = "dots", isDefault = true),
    )

    /**
     * Get a default category by its ID.
     */
    fun getById(categoryId: String): Category? = all.find { it.id == categoryId }

    /**
     * Get the IDs of all default categories.
     */
    val ids: Set<String> = all.map { it.id }.toSet()
}
