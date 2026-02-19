package com.travelexpenses

import app.cash.sqldelight.db.SqlDriver

/**
 * Creates an in-memory SQLite driver for testing.
 * Each platform provides the actual implementation:
 * - Android (JVM): JdbcSqliteDriver
 * - iOS (Native): NativeSqliteDriver with inMemory configuration
 */
expect fun createInMemoryDriver(): SqlDriver
