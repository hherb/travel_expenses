package com.travelexpenses.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.travelexpenses.db.TravelExpensesDb
import com.travelexpenses.model.Tag
import com.travelexpenses.model.TagId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

class SqlDelightTagRepository(
    private val db: TravelExpensesDb,
    private val queryContext: CoroutineContext,
) : TagRepository {

    override suspend fun getAllTags(): List<Tag> = withContext(queryContext) {
        db.materializedStateQueries.selectAllTags().executeAsList().map { it.toTag() }
    }

    override suspend fun getTag(tagId: TagId): Tag? = withContext(queryContext) {
        db.materializedStateQueries.selectTagById(tagId).executeAsOneOrNull()?.toTag()
    }

    override fun observeAllTags(): Flow<List<Tag>> {
        return db.materializedStateQueries.selectAllTags()
            .asFlow()
            .mapToList(queryContext)
            .map { rows -> rows.map { it.toTag() } }
    }

    private fun com.travelexpenses.db.Tag.toTag(): Tag {
        return Tag(id = id, label = label)
    }
}
