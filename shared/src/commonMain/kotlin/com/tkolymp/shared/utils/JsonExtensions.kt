package com.tkolymp.shared.utils

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

fun JsonElement?.asJsonObjectOrNull(): JsonObject? = when {
    this == null || this is JsonNull -> null
    this is JsonObject -> this
    else -> null
}

fun JsonElement?.asJsonArrayOrNull(): JsonArray? = when {
    this == null || this is JsonNull -> null
    this is JsonArray -> this
    else -> null
}

fun JsonObject.str(key: String): String? =
    try { this[key]?.jsonPrimitive?.contentOrNull } catch (_: Exception) { null }

fun JsonObject.int(key: String): Int? =
    try { this[key]?.jsonPrimitive?.intOrNull } catch (_: Exception) { null }

fun JsonObject.bool(key: String): Boolean? =
    try { this[key]?.jsonPrimitive?.booleanOrNull } catch (_: Exception) { null }
