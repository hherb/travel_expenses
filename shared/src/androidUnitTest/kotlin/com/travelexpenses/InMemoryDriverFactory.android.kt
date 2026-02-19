package com.travelexpenses

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.travelexpenses.db.TravelExpensesDb

/**
 * Creates an in-memory SQLite driver for Android unit tests (JVM).
 * Uses the JDBC SQLite driver since Android unit tests run on the JVM.
 */
actual fun createInMemoryDriver(): SqlDriver {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    TravelExpensesDb.Schema.create(driver)
    return driver
}
