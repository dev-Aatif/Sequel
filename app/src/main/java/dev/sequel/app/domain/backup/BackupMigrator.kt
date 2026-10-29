package dev.sequel.app.domain.backup

import kotlinx.serialization.json.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles schema migration pipeline between different versions of Sequel backups.
 * Upgrades legacy backups to [SequelBackupModel.CURRENT_SCHEMA_VERSION].
 */
@Singleton
class BackupMigrator @Inject constructor() {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Inspects the raw backup JSON and applies version migration pipeline
     * to bring it up to [SequelBackupModel.CURRENT_SCHEMA_VERSION].
     */
    fun migrate(rawJson: String): String {
        val rootElement = try {
            json.parseToJsonElement(rawJson)
        } catch (e: Exception) {
            // If it's malformed JSON, pass through to let validator report the exact syntax error
            return rawJson
        }

        if (rootElement !is JsonObject) {
            return rawJson
        }

        val versionElement = rootElement["schemaVersion"]
        val version = versionElement?.jsonPrimitive?.intOrNull ?: 0

        // If already at current version, return unchanged
        if (version == SequelBackupModel.CURRENT_SCHEMA_VERSION) {
            return rawJson
        }

        // If newer version, return unchanged so validator can reject it with appropriate message
        if (version > SequelBackupModel.CURRENT_SCHEMA_VERSION) {
            return rawJson
        }

        var migratedObject = rootElement

        // Migration step: v0 (unversioned legacy) -> v1
        if (version < 1) {
            migratedObject = migrateV0ToV1(migratedObject)
        }

        return json.encodeToString(JsonObject.serializer(), migratedObject)
    }

    /**
     * Migrates unversioned (v0) backup to v1:
     * - Injects schemaVersion = 1
     * - Coerces legacy 1..5 star ratings to 0.5..10.0 scale if scale was 5
     * - Ensures default empty lists for any omitted collections (e.g. mountRushmore)
     */
    private fun migrateV0ToV1(source: JsonObject): JsonObject {
        val content = source.toMutableMap()

        // Set schemaVersion = 1
        content["schemaVersion"] = JsonPrimitive(1)

        // Ensure appVersion exists
        if (!content.containsKey("appVersion")) {
            content["appVersion"] = JsonPrimitive("1.0.0")
        }

        // Ensure exportedAt exists
        if (!content.containsKey("exportedAt")) {
            content["exportedAt"] = JsonPrimitive(System.currentTimeMillis())
        }

        // Ensure missing arrays are initialized
        listOf("shows", "watchedMovies", "watchedEpisodes", "watchlist", "reviews", "mountRushmore").forEach { key ->
            if (!content.containsKey(key)) {
                content[key] = JsonArray(emptyList())
            }
        }

        // Migrate reviews if legacy 1..5 ratings exist
        val reviewsArray = content["reviews"] as? JsonArray
        if (reviewsArray != null) {
            val migratedReviews = reviewsArray.map { reviewElement ->
                if (reviewElement is JsonObject) {
                    val reviewMap = reviewElement.toMutableMap()
                    val ratingValue = reviewMap["rating"]?.jsonPrimitive?.floatOrNull
                    val originalScale = reviewMap["originalRatingScale"]?.jsonPrimitive?.floatOrNull
                    // If rating <= 5.0 and scale is 5, scale by 2.0 to convert to 10.0 scale
                    if (ratingValue != null && originalScale == 5.0f && ratingValue <= 5.0f) {
                        reviewMap["rating"] = JsonPrimitive(ratingValue * 2.0f)
                        reviewMap["originalRating"] = JsonPrimitive(ratingValue)
                        reviewMap["originalRatingScale"] = JsonPrimitive(5.0f)
                    }
                    JsonObject(reviewMap)
                } else {
                    reviewElement
                }
            }
            content["reviews"] = JsonArray(migratedReviews)
        }

        return JsonObject(content)
    }
}
