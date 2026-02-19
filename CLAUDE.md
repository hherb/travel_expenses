# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Privacy-first, offline-first travel expense tracker built with Kotlin Multiplatform (KMP). Currently in **Phase 0**: TDD validation of KMP coroutines + SQLDelight on Android and iOS — no UI yet. See `SPEC.md` for the full v1.0 product specification.

## Build & Test Commands

```bash
# Build shared module
./gradlew shared:build

# Android unit tests (fast, runs on JVM)
./gradlew shared:testDebugUnitTest

# iOS simulator tests (requires macOS with Xcode)
./gradlew shared:iosSimulatorArm64Test

# All tests across both platforms
./gradlew shared:allTests

# Single test class (Android)
./gradlew shared:testDebugUnitTest --tests "com.travelexpenses.EventLogBasicTest"
```

Requires `JAVA_HOME` and `ANDROID_HOME` environment variables. See `LOCAL_BUILD_ENV.md` for machine-specific paths, correct task names, and toolchain details.

No dedicated linter is configured. Gradle 8.9 with Kotlin 2.1.0.

## Architecture

### KMP Shared Module (`shared/`)

All business logic lives in `shared/src/commonMain/`. Platform-specific code uses Kotlin's `expect`/`actual` mechanism.

```
commonMain/kotlin/com/travelexpenses/
├── model/          # Domain types: Expense, Trip (type-safe ID wrappers, String-encoded decimals)
├── event/          # Append-only event log: ExpenseEvent sealed class (ExpenseCreated, ExpenseDeleted, TripCreated, TripArchived)
└── repository/     # EventLogRepository interface + SqlDelightEventLogRepository implementation
```

### Key Design Decisions

- **Append-only event log**: All mutations are immutable events stored in SQLite. Materialized state is derived by replaying events. Events carry `eventId`, `timestamp`, `sequenceNumber`, `deviceId`.
- **Polymorphic JSON serialization**: Events are serialized via `kotlinx-serialization` with a `type` discriminator for storage in SQLDelight's `payload` column.
- **Mutex-based write synchronization**: `SqlDelightEventLogRepository` uses `Mutex` to ensure thread-safe appends and monotonic sequence numbers.
- **Dispatcher injection**: Repository accepts a `CoroutineDispatcher` parameter (defaults to `Dispatchers.Default`). Tests inject `StandardTestDispatcher` for deterministic execution.
- **String-encoded decimals**: Monetary amounts use `String` (not `Double`/`Float`) for cross-platform precision.

### Platform Layer

- `androidMain/` — `DatabaseDriverFactory` creates `AndroidSqliteDriver`
- `iosMain/` — `DatabaseDriverFactory` creates `NativeSqliteDriver`
- `androidUnitTest/` — `InMemoryDriverFactory` using `JdbcSqliteDriver` for JVM tests
- `iosTest/` — `InMemoryDriverFactory` using SQLDelight `inMemoryDriver` + Kotlin/Native-specific concurrency tests

### SQLDelight Schema

Single table `event_log` defined in `shared/src/commonMain/sqldelight/com/travelexpenses/EventLog.sq`. Generated code provides type-safe queries for insert, select by device/type, count, and delete operations.

## Testing Conventions

Tests are TDD-first. The test suite validates the riskiest architectural assumption: KMP coroutines + SQLDelight working reliably across platforms.

- **Common tests** (`commonTest/`): serialization round-trips, CRUD operations, concurrent writes (50 from one device, 5 devices × 20 events), Flow observation, cancellation behavior
- **iOS-specific tests** (`iosTest/`): cross-dispatcher memory visibility, real background thread concurrency, SQLite query performance benchmarks (< 100ms for 100 events)
- **Test helpers** (`TestHelpers.kt`): factory methods for all domain types and events with sensible defaults and a global sequence counter

## Dependencies

Defined in `gradle/libs.versions.toml`. Core stack: kotlinx-coroutines 1.9.0, kotlinx-serialization 1.7.3, kotlinx-datetime 0.6.1, SQLDelight 2.0.2. Tests use kotlinx-coroutines-test and kotlin-test.
