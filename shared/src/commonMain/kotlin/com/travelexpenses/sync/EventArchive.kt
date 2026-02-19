package com.travelexpenses.sync

import com.travelexpenses.event.ExpenseEvent
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Portable archive of the full event log for backup/restore.
 * Contains all events plus metadata about the archive itself.
 *
 * v1 scope: local backup/restore only. Prep for v2 multi-device sync.
 */
@Serializable
data class EventArchive(
    val version: Int = CURRENT_VERSION,
    val createdAt: String,              // ISO 8601 instant
    val deviceId: String,               // Device that created the archive
    val eventCount: Int,
    val events: List<ExpenseEvent>,
    val imageReferences: List<String> = emptyList(),  // File paths of associated receipt images
) {
    companion object {
        const val CURRENT_VERSION = 1
    }
}

/**
 * Serializes and deserializes [EventArchive] to/from JSON.
 */
object EventArchiveSerializer {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    fun serialize(archive: EventArchive): String {
        return json.encodeToString(EventArchive.serializer(), archive)
    }

    fun deserialize(jsonString: String): EventArchive {
        return json.decodeFromString(EventArchive.serializer(), jsonString)
    }
}
