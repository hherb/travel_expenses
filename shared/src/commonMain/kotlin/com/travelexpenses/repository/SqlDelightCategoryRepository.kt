package com.travelexpenses.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.travelexpenses.db.TravelExpensesDb
import com.travelexpenses.model.Category
import com.travelexpenses.model.CategoryId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

class SqlDelightCategoryRepository(
    private val db: TravelExpensesDb,
    private val queryContext: CoroutineContext,
) : CategoryRepository {

    override suspend fun getAllCategories(): List<Category> = withContext(queryContext) {
        db.materializedStateQueries.selectAllCategories().executeAsList().map { it.toCategory() }
    }

    override suspend fun getCategory(categoryId: CategoryId): Category? = withContext(queryContext) {
        db.materializedStateQueries.selectCategoryById(categoryId).executeAsOneOrNull()?.toCategory()
    }

    override fun observeAllCategories(): Flow<List<Category>> {
        return db.materializedStateQueries.selectAllCategories()
            .asFlow()
            .mapToList(queryContext)
            .map { rows -> rows.map { it.toCategory() } }
    }

    private fun com.travelexpenses.db.Category.toCategory(): Category {
        return Category(
            id = id,
            name = name,
            icon = icon,
            isDefault = is_default != 0L,
        )
    }
}
