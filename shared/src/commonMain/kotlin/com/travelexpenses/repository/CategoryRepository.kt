package com.travelexpenses.repository

import com.travelexpenses.model.Category
import com.travelexpenses.model.CategoryId
import kotlinx.coroutines.flow.Flow

interface CategoryRepository {
    suspend fun getAllCategories(): List<Category>
    suspend fun getCategory(categoryId: CategoryId): Category?
    fun observeAllCategories(): Flow<List<Category>>
}
