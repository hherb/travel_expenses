package com.travelexpenses.repository

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.travelexpenses.db.TravelExpensesDb

actual fun createInMemoryDriver(): SqlDriver {
    // For Android unit tests, use JdbcSqliteDriver instead (see androidUnitTest)
    // This is for instrumented tests on a real device/emulator
    throw UnsupportedOperationException("Use JdbcSqliteDriver for Android unit tests")
}
