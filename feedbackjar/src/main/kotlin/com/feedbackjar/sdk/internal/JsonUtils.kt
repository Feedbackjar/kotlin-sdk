package com.feedbackjar.sdk.internal

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * Converts a flat Kotlin map of custom properties (String, Number, Boolean, or null values)
 * into a [JsonObject]. Nested maps/lists aren't supported — non-primitive values are stringified.
 */
internal fun Map<String, Any?>.toJsonObject(): JsonObject = buildJsonObject {
    forEach { (key, value) -> put(key, value.toJsonPrimitiveOrNull()) }
}

private fun Any?.toJsonPrimitiveOrNull(): JsonElement = when (this) {
    null -> JsonNull
    is String -> JsonPrimitive(this)
    is Boolean -> JsonPrimitive(this)
    is Number -> JsonPrimitive(this)
    else -> JsonPrimitive(this.toString())
}
