package com.travelexpenses.repository

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.travelexpenses.db.TravelExpensesDb

actual class DatabaseDriverFactory(private val context: Context) {
    actual fun createDriver(): SqlDriver {
        return AndroidSqliteDriver(TravelExpensesDb.Schema, context, "travel_expenses.db")
    }
}
