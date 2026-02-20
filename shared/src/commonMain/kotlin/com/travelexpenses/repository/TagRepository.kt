package com.travelexpenses.repository

import com.travelexpenses.model.Tag
import com.travelexpenses.model.TagId
import kotlinx.coroutines.flow.Flow

interface TagRepository {
    suspend fun getAllTags(): List<Tag>
    suspend fun getTag(tagId: TagId): Tag?
    fun observeAllTags(): Flow<List<Tag>>
}
