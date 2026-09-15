package com.vela.chat.data

import kotlinx.serialization.json.Json

/** Single lenient JSON instance shared by storage and networking layers. */
val AppJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    isLenient = true
    explicitNulls = false
    coerceInputValues = true
}
