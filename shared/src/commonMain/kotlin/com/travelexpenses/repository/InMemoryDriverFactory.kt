package com.travelexpenses.repository

import app.cash.sqldelight.db.SqlDriver

/**
 * Creates an in-memory SQLite driver for testing.
 * Platform implementations provide the actual driver.
 */
expect fun createInMemoryDriver(): SqlDriver
