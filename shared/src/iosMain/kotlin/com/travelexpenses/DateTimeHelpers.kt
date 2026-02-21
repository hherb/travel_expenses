package com.travelexpenses

import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

/**
 * Helpers that expose kotlinx-datetime APIs to Swift.
 *
 * Clock, TimeZone, and their extension functions are not directly
 * accessible from Swift via Kotlin/Native interop, so we wrap them
 * in simple top-level functions.
 */
object DateTimeHelpers {
    fun now() = Clock.System.now()
    fun todayLocal(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())
}
