package com.travelexpenses.repository

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import app.cash.sqldelight.driver.native.inMemoryDriver
import com.travelexpenses.db.TravelExpensesDb

actual fun createInMemoryDriver(): SqlDriver {
    return inMemoryDriver(TravelExpensesDb.Schema)
}
