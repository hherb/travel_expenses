package com.travelexpenses.repository

import app.cash.sqldelight.db.SqlDriver

/**
 * Platform-specific factory for SQLDelight drivers.
 * Each platform provides its own implementation via expect/actual.
 */
expect class DatabaseDriverFactory {
    fun createDriver(): SqlDriver
}
