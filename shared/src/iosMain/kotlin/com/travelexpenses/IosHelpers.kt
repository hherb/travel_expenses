package com.travelexpenses

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.serialization.json.Json
import kotlin.coroutines.CoroutineContext

/**
 * Exposes coroutine dispatchers and serialization utilities to Swift.
 *
 * Kotlin/Native does not export `Dispatchers` directly to Swift,
 * so we wrap them in an object with simple property accessors.
 */
object IosDispatchers {
    val default_: CoroutineContext = Dispatchers.Default
    val io: CoroutineContext = Dispatchers.IO
}

object IosJson {
    val instance: Json = Json { ignoreUnknownKeys = true }
}
