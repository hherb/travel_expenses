# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Privacy-first, offline-first travel expense tracker built with Kotlin Multiplatform (KMP). Phases 0-3 complete: shared KMP business logic + Android app with Jetpack Compose UI. See `SPEC.md` for the full v1.0 product specification.

## Build & Test Commands

```bash
# Build shared module
./gradlew shared:build

# Build Android app
./gradlew :android:app:assembleDebug

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
├── model/          # Domain types: Expense, Trip, Category, Tag, ReceiptImage
├── event/          # Append-only event log: ExpenseEvent sealed class (11 event types)
│   └── EventReplayEngine  # Translates events → materialized state tables
├── repository/     # EventLogRepository, ExpenseRepository, TripRepository, CategoryRepository, ExchangeRateRepository
│   └── SqlDelight* # Implementations for all repository interfaces
├── currency/       # CurrencyConverter (display-only), StaticRates, ExchangeRateService (Ktor)
├── ocr/            # OcrParser (regex/heuristic), OcrResult, OcrEngine (expect interface)
├── export/         # CsvExporter (trip, date range, all scopes)
├── validation/     # ExpenseValidator, FieldValidator, DuplicateDetector, DefaultCategories
└── sync/           # SyncManager, EventArchive, EventArchiveSerializer
```

### Android App Module (`android/app/`)

Jetpack Compose UI with Material 3 / Material You.

```
android/app/src/main/kotlin/com/travelexpenses/android/
├── TravelExpensesApp.kt    # Application class (Koin DI init)
├── MainActivity.kt         # Single activity, edge-to-edge Compose
├── di/AppModule.kt         # Koin module: database, repos, ViewModels
├── navigation/             # Compose Navigation with bottom bar
│   ├── Screen.kt           # Route definitions
│   └── AppNavigation.kt    # NavHost + bottom navigation
├── ui/
│   ├── theme/              # Material 3 theme, colors, typography
│   ├── trips/              # TripListScreen, TripDetailScreen, TripViewModel
│   ├── expenses/           # ExpenseEntryScreen, ExpenseViewModel
│   ├── reports/            # ReportsScreen, ReportsViewModel
│   ├── settings/           # SettingsScreen, SettingsViewModel
│   └── capture/            # OcrCaptureScreen, OcrReviewScreen
├── ocr/MlKitOcrEngine.kt  # ML Kit text recognition
└── auth/                   # BiometricHelper, PinManager, LockScreen
```

### Key Design Decisions

- **Append-only event log**: All mutations are immutable events stored in SQLite. Materialized state is derived by replaying events. Events carry `eventId`, `timestamp`, `sequenceNumber`, `deviceId`.
- **Polymorphic JSON serialization**: Events are serialized via `kotlinx-serialization` with a `type` discriminator for storage in SQLDelight's `payload` column.
- **Mutex-based write synchronization**: `SqlDelightEventLogRepository` uses `Mutex` to ensure thread-safe appends and monotonic sequence numbers.
- **Dispatcher injection**: Repository accepts a `CoroutineDispatcher` parameter (defaults to `Dispatchers.Default`). Tests inject `StandardTestDispatcher` for deterministic execution.
- **String-encoded decimals**: Monetary amounts use `String` (not `Double`/`Float`) for cross-platform precision.
- **Koin DI**: Android app uses Koin for dependency injection (`appModule` in `di/`).
- **MVVM**: ViewModels expose StateFlow/SharedFlow for UI state, Compose screens collect via `collectAsState()`.

### Platform Layer

- `androidMain/` — `DatabaseDriverFactory` creates `AndroidSqliteDriver`, Ktor uses OkHttp engine
- `iosMain/` — `DatabaseDriverFactory` creates `NativeSqliteDriver`, Ktor uses Darwin engine
- `androidUnitTest/` — `InMemoryDriverFactory` using `JdbcSqliteDriver` for JVM tests
- `iosTest/` — `InMemoryDriverFactory` using SQLDelight `inMemoryDriver` + Kotlin/Native-specific concurrency tests

### SQLDelight Schema

Three `.sq` files in `shared/src/commonMain/sqldelight/com/travelexpenses/db/`:
- `EventLog.sq` — append-only event store (source of truth)
- `MaterializedState.sq` — 6 read-side tables (trip, expense, category, tag, expense_tag, receipt_image)
- `ExchangeRate.sq` — cached currency conversion rates

## Testing Conventions

Tests are TDD-first. The test suite validates the riskiest architectural assumption: KMP coroutines + SQLDelight working reliably across platforms.

- **Common tests** (`commonTest/`): serialization round-trips, CRUD operations, concurrent writes (50 from one device, 5 devices × 20 events), Flow observation, cancellation behavior, OCR parsing, currency conversion, CSV export, validation, sync
- **iOS-specific tests** (`iosTest/`): cross-dispatcher memory visibility, real background thread concurrency, SQLite query performance benchmarks (< 100ms for 100 events)
- **Test helpers** (`TestHelpers.kt`): factory methods for all domain types and events with sensible defaults and a global sequence counter

## Dependencies

Defined in `gradle/libs.versions.toml`. Core stack:
- **Shared**: kotlinx-coroutines 1.9.0, kotlinx-serialization 1.7.3, kotlinx-datetime 0.6.1, SQLDelight 2.0.2, Ktor 2.3.12
- **Android**: Jetpack Compose (BOM 2024.12.01), Navigation Compose 2.8.5, Lifecycle 2.8.7, Koin 3.5.6, ML Kit Text Recognition 16.0.1, CameraX 1.4.1, BiometricPrompt 1.2.0-alpha05
- **Tests**: kotlinx-coroutines-test, kotlin-test
