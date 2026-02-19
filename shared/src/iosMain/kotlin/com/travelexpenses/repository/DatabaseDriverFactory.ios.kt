package com.travelexpenses.repository

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.travelexpenses.db.TravelExpensesDb

actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver {
        return NativeSqliteDriver(TravelExpensesDb.Schema, "travel_expenses.db")
    }
}
