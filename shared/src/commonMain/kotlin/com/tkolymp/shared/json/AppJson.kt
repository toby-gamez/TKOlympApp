package com.tkolymp.shared.json

import kotlinx.serialization.json.Json

val AppJson = Json { ignoreUnknownKeys = true }

val AppJsonWithDefaults = Json { ignoreUnknownKeys = true; encodeDefaults = true }
