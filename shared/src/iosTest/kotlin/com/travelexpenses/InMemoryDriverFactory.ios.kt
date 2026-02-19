package com.travelexpenses

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.inMemoryDriver
import com.travelexpenses.db.TravelExpensesDb

/**
 * Creates an in-memory SQLite driver for iOS tests (Kotlin/Native).
 * Uses SQLDelight's [inMemoryDriver] backed by a single-connection native SQLite.
 */
actual fun createInMemoryDriver(): SqlDriver {
    return inMemoryDriver(TravelExpensesDb.Schema)
}
