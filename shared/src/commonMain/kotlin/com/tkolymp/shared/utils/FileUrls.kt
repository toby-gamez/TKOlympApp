package com.tkolymp.shared.utils

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

const val SITE_ORIGIN = "https://tkolymp.cz"

/** Uploaded files come back as server-relative paths (`/f/...`); makes them absolute so an image loader can fetch them. */
fun resolveFileUrl(url: String?): String? {
    val u = url?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return if (u.startsWith("/") && !u.startsWith("//")) SITE_ORIGIN + u else u
}

/** Absolute URL of the `{ url }` file object under [key] (default `image`) of a GraphQL node, e.g. a person's photo. */
fun JsonObject?.fileUrl(key: String = "image"): String? =
    resolveFileUrl(((this?.get(key) as? JsonObject)?.get("url") as? JsonPrimitive)?.contentOrNull)

/** Server-resized copy (`?w=`) of an image we host under `/f/`; any other URL is returned unchanged. */
fun withImageWidth(url: String?, width: Int): String? {
    if (url == null || !url.startsWith("$SITE_ORIGIN/f/")) return url
    return if ('?' in url) url else "$url?w=$width"
}
