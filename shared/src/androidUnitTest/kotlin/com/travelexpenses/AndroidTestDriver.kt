package com.travelexpenses

// Note: Android unit tests (running on JVM) need the JdbcSqliteDriver.
// The commonTest tests will run on iOS via NativeSqliteDriver (iosTest)
// and on Android via instrumented tests (androidInstrumentedTest).
//
// For local Android unit tests, add the JVM SQLite driver:
//   testImplementation("app.cash.sqldelight:sqlite-driver:2.0.2")
// and create a JVM-specific test source set.
//
// The critical validation target is iOS (iosSimulatorArm64Test).
// Android/JVM coroutines are well-proven and lower risk.
