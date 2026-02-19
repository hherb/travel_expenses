# Travel Expenses -- Product Specification v1.0

## 1. Overview

A privacy-first, offline-first mobile expense tracker for travelers. Capture receipts, extract data via on-device OCR, organize by trip, and export reports -- all without requiring an internet connection or cloud account.

### Design Principles

1. **Offline-first** -- every feature works without connectivity; sync is additive
2. **Privacy-first** -- all data stays on-device by default; no telemetry, no cloud requirement
3. **Append-only data model** -- events are never mutated, enabling reliable sync and full audit trail
4. **Native UI, shared logic** -- SwiftUI (iOS/macOS) and Jetpack Compose (Android) for UI; Kotlin Multiplatform (KMP) for all business logic

---

## 2. Architecture

### 2.1 Platform Strategy

| Layer | Technology | Rationale |
|-------|-----------|-----------|
| **Shared logic** | Kotlin Multiplatform (KMP) | Single source of truth for data models, business rules, event log, sync protocol, OCR coordination, currency handling |
| **iOS/macOS UI** | SwiftUI | Native look and feel, multiplatform via SwiftUI |
| **Android UI** | Jetpack Compose | Native Material 3 UI |
| **On-device OCR** | Apple Vision (iOS), ML Kit (Android) | Platform-native, no network required |
| **Local storage** | SQLDelight (KMP) | Cross-platform SQLite with type-safe queries |
| **Image storage** | Platform filesystem | Original + compressed copies, referenced by event ID |

### 2.2 KMP Shared Module Boundary

The shared Kotlin module (`shared/`) owns:

- **Data models** -- all domain types (Expense, Trip, Currency, Tag, etc.)
- **Event log engine** -- append-only event store, event types, sequencing
- **Business rules** -- validation, duplicate detection, currency conversion logic
- **Sync protocol** -- merge strategy, conflict resolution, vector clocks
- **OCR result parsing** -- structured extraction from raw OCR text
- **Export logic** -- CSV generation, report aggregation
- **Repository interfaces** -- platform-specific implementations injected via expect/actual

The shared module does **not** own:
- UI components (platform-native)
- OCR engine calls (platform-native APIs wrapped behind an `expect` interface)
- Biometric/PIN authentication (platform-native)
- File I/O for images (platform-native, behind `expect` interface)

### 2.3 Data Flow

```
Camera/Gallery -> Platform OCR Engine -> KMP OCR Parser -> Event Created -> Event Log -> SQLDelight -> UI State
```

---

## 3. Data Model

### 3.1 Core Types

```kotlin
// -- Expense --
data class Expense(
    val id: ExpenseId,           // UUID
    val tripId: TripId,
    val amount: BigDecimalCompat, // platform-mapped precise decimal
    val currency: CurrencyCode,   // ISO 4217
    val category: Category,
    val vendor: String?,
    val date: LocalDate,
    val notes: String?,
    val tags: List<Tag>,
    val receiptImageIds: List<ImageId>,
    val ocrConfidence: Float?,    // 0.0-1.0, null if manually entered
    val taxAmount: BigDecimalCompat?,
    val createdAt: Instant,
    val lastModifiedAt: Instant
)

// -- Trip --
data class Trip(
    val id: TripId,
    val name: String,
    val destination: String?,
    val startDate: LocalDate?,
    val endDate: LocalDate?,
    val baseCurrency: CurrencyCode, // for summary/conversion display
    val createdAt: Instant
)

// -- Category (user-extensible) --
data class Category(
    val id: CategoryId,
    val name: String,            // e.g. "Transport", "Food", "Lodging"
    val icon: String,            // icon identifier
    val isDefault: Boolean       // shipped with app vs user-created
)

// -- Tag --
data class Tag(
    val id: TagId,
    val label: String            // freeform, e.g. "client-dinner", "reimbursable"
)
```

### 3.2 Default Categories

Shipped with the app (user can add/rename/hide but not delete):

| Category | Icon | Notes |
|----------|------|-------|
| Transport | plane/car | Flights, taxis, trains, fuel, tolls |
| Accommodation | bed | Hotels, hostels, Airbnb |
| Food & Drink | fork | Restaurants, groceries, coffee |
| Activities | ticket | Tours, museums, entertainment |
| Shopping | bag | Souvenirs, clothing, gear |
| Communication | phone | SIM cards, roaming, wifi |
| Health | cross | Insurance, pharmacy, medical |
| Fees & Tips | percent | Visa fees, ATM fees, tips |
| Other | dots | Catch-all |

### 3.3 Append-Only Event Log

All mutations are recorded as immutable events. Current state is derived by replaying the log.

```kotlin
sealed class ExpenseEvent {
    abstract val eventId: EventId      // UUID
    abstract val timestamp: Instant    // wall clock
    abstract val sequenceNumber: Long  // monotonic per device
    abstract val deviceId: DeviceId

    data class ExpenseCreated(/* ... full expense snapshot ... */)
    data class ExpenseUpdated(/* ... diff fields ... */)
    data class ExpenseDeleted(val expenseId: ExpenseId)
    data class TripCreated(/* ... full trip snapshot ... */)
    data class TripUpdated(/* ... diff fields ... */)
    data class TripArchived(val tripId: TripId)
    data class CategoryCreated(/* ... */)
    data class CategoryUpdated(/* ... */)
    data class TagCreated(/* ... */)
    // Image events reference filesystem paths, not blobs
    data class ReceiptAttached(val expenseId: ExpenseId, val imageId: ImageId)
    data class ReceiptDetached(val expenseId: ExpenseId, val imageId: ImageId)
}
```

**Why append-only:**
- Sync becomes "merge two ordered logs" instead of "resolve field-level conflicts"
- Full audit trail / undo for free
- Offline edits on multiple devices naturally merge
- Event replay enables migration to new schema versions

---

## 4. Features -- v1

### 4.1 Expense Capture

**Manual entry:**
- Amount (numeric keyboard, decimal handling per locale)
- Currency selector (recent currencies promoted to top)
- Category picker
- Date (defaults to today, calendar picker available)
- Vendor name (free text, autocomplete from history)
- Notes (free text)
- Tags (multi-select from existing + create inline)
- Trip assignment (current active trip or picker)

**Receipt scan (camera/gallery):**
- On-device OCR extracts: **total amount, date, vendor name, currency, tax amount**
- Results presented for user review/correction before saving
- Confidence indicator per field
- Original receipt image stored locally
- Multiple receipts per expense supported

### 4.2 OCR Pipeline

```
Image capture -> Preprocessing (deskew, contrast) -> Platform OCR engine -> Raw text
    -> KMP field extraction (regex + heuristics) -> Structured result -> User review screen
```

**Extracted fields (moderate depth):**

| Field | Strategy | Confidence signal |
|-------|----------|-------------------|
| Total amount | Pattern match for "total", "amount due", largest number near bottom | High if single clear match |
| Date | Date pattern recognition (multiple formats, locale-aware) | High if single date found |
| Vendor | Top of receipt, largest text block, or text near logo region | Medium -- often needs correction |
| Currency | Symbol detection ($/EUR/etc.), or inferred from trip's locale | Medium |
| Tax amount | Pattern match for "tax", "VAT", "GST" + adjacent number | Medium |

**Not in v1:** Line-item extraction, receipt categorization ML, multi-language OCR optimization.

### 4.3 Trip Management

- Create/edit/archive trips
- Set base currency per trip (for summary display)
- Active trip concept (new expenses default to active trip)
- Trip summary: total spend, breakdown by category, daily average
- Trips are never hard-deleted (archived, filterable)

### 4.4 Currency Handling

- All amounts stored in original currency (never auto-converted in storage)
- Trip summaries show converted totals in trip's base currency
- Exchange rates:
  - **Offline:** user can manually set a rate per currency pair per trip
  - **Online (opportunistic):** fetch rates when connectivity available, cache locally
  - Rate source: free API (e.g., exchangerate.host or frankfurter.app), refreshed at most daily
- Conversion is always display-only; the canonical amount is the original currency

### 4.5 Reporting & Export

**In-app (v1):**
- Per-trip summary: total, by-category breakdown, by-day breakdown, by-currency breakdown
- Visual: simple bar/pie chart for category distribution

**Export (v1):**
- **CSV** -- one row per expense, all fields, UTF-8 with BOM for Excel compatibility
- Export scope: single trip, date range, or all data
- Share via system share sheet (email, AirDrop, Files app, etc.)

**Deferred to v2:** PDF reports with receipt thumbnails, corporate expense report templates.

### 4.6 App Security

- **Biometric unlock** -- Face ID / Touch ID (iOS), BiometricPrompt (Android)
- **PIN fallback** -- 6-digit PIN for devices without biometrics
- **Auto-lock** -- configurable timeout (immediate, 1 min, 5 min, 15 min)
- **No app-level encryption in v1** -- relies on OS-level storage encryption (iOS Data Protection, Android file-based encryption)
- Screen content hidden in app switcher when locked

### 4.7 Sync (v1 -- Foundation)

v1 ships with the **sync protocol implemented but limited to local backup/restore**:

- Export full event log + images as encrypted archive
- Import archive on new device (full restore)
- This validates the event log merge logic without requiring a sync backend

**Deferred to v2:** Real-time multi-device sync via:
- Option A: iCloud (iOS) + Google Drive (Android) as transport
- Option B: Self-hosted sync server (for privacy purists)
- The append-only log + vector clocks make this a transport problem, not a conflict problem

---

## 5. UI/UX Guidelines

### 5.1 Navigation Structure

```
Tab Bar:
  [Expenses]  [Trips]  [Reports]  [Settings]

Expenses tab:
  - List view (grouped by date, filterable by trip/category/tag)
  - FAB / + button -> capture flow (manual or scan)
  - Swipe actions: edit, delete, change category

Trips tab:
  - Active trip prominent at top
  - Trip cards with summary stats
  - Tap -> trip detail (expenses list, summary, edit)

Reports tab:
  - Trip selector
  - Summary cards + charts
  - Export button

Settings tab:
  - App lock (biometric/PIN toggle, timeout)
  - Default currency
  - Categories management
  - Data management (export/import archive)
  - About / licenses
```

### 5.2 Capture Flow (Optimized for Speed)

The #1 UX goal is **< 10 seconds from app open to expense saved** for the common case.

```
1. Tap + (or dedicated camera button)
2. Choice: Scan Receipt / Enter Manually
3a. Scan: camera opens -> capture -> OCR runs -> review screen (fields pre-filled) -> Save
3b. Manual: form with smart defaults (today's date, last-used currency, active trip) -> Save
4. Confirmation toast, return to expense list
```

### 5.3 Platform Conventions

- **iOS:** SF Symbols for icons, system colors, standard navigation patterns, haptic feedback
- **Android:** Material 3, dynamic color (Material You), standard back gesture, predictive back
- Both: dark mode support from day 1, dynamic type / font scaling, accessibility labels on all controls

---

## 6. Technical Details

### 6.1 Project Structure

```
travel-expenses/
  shared/                    # KMP shared module
    src/
      commonMain/            # All shared logic
        kotlin/
          model/             # Domain types
          event/             # Event log engine
          ocr/               # OCR result parser
          sync/              # Sync protocol
          export/            # CSV export
          repository/        # Repository interfaces
          currency/          # Conversion logic
      androidMain/           # Android expect/actual implementations
      iosMain/               # iOS expect/actual implementations
    build.gradle.kts

  android/                   # Android app
    app/
      src/main/
        kotlin/
          ui/                # Jetpack Compose screens
          ocr/               # ML Kit integration
          auth/              # BiometricPrompt
          di/                # Dependency injection
        AndroidManifest.xml
    build.gradle.kts

  ios/                       # iOS/macOS app
    TravelExpenses/
      UI/                    # SwiftUI views
      OCR/                   # Apple Vision integration
      Auth/                  # LocalAuthentication
      DI/                    # Dependency wiring
    TravelExpenses.xcodeproj

  test-vectors/              # Shared JSON test fixtures
    ocr/                     # OCR input -> expected parsed output
    events/                  # Event sequences -> expected state
    currency/                # Conversion test cases
    export/                  # Expected CSV output
```

### 6.2 SQLDelight Schema (Key Tables)

```sql
-- Materialized state (rebuilt from event log, queryable)
CREATE TABLE expense (
    id TEXT NOT NULL PRIMARY KEY,
    trip_id TEXT NOT NULL REFERENCES trip(id),
    amount TEXT NOT NULL,          -- string-encoded decimal for precision
    currency TEXT NOT NULL,        -- ISO 4217
    category_id TEXT NOT NULL,
    vendor TEXT,
    date TEXT NOT NULL,            -- ISO 8601 date
    notes TEXT,
    tax_amount TEXT,
    ocr_confidence REAL,
    created_at TEXT NOT NULL,      -- ISO 8601 instant
    last_modified_at TEXT NOT NULL,
    is_deleted INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE trip (
    id TEXT NOT NULL PRIMARY KEY,
    name TEXT NOT NULL,
    destination TEXT,
    start_date TEXT,
    end_date TEXT,
    base_currency TEXT NOT NULL,
    created_at TEXT NOT NULL,
    is_archived INTEGER NOT NULL DEFAULT 0
);

-- The source of truth
CREATE TABLE event_log (
    event_id TEXT NOT NULL PRIMARY KEY,
    sequence_number INTEGER NOT NULL,
    device_id TEXT NOT NULL,
    timestamp TEXT NOT NULL,
    event_type TEXT NOT NULL,
    payload TEXT NOT NULL,         -- JSON-serialized event
    UNIQUE(device_id, sequence_number)
);

CREATE TABLE expense_tag (
    expense_id TEXT NOT NULL REFERENCES expense(id),
    tag_id TEXT NOT NULL REFERENCES tag(id),
    PRIMARY KEY (expense_id, tag_id)
);

CREATE TABLE receipt_image (
    id TEXT NOT NULL PRIMARY KEY,
    expense_id TEXT NOT NULL REFERENCES expense(id),
    file_path TEXT NOT NULL,       -- relative to app storage
    thumbnail_path TEXT,
    width INTEGER,
    height INTEGER,
    created_at TEXT NOT NULL
);

CREATE TABLE exchange_rate (
    from_currency TEXT NOT NULL,
    to_currency TEXT NOT NULL,
    rate TEXT NOT NULL,             -- string-encoded decimal
    date TEXT NOT NULL,             -- rate valid for this date
    source TEXT NOT NULL,           -- "manual" | "api"
    fetched_at TEXT NOT NULL,
    PRIMARY KEY (from_currency, to_currency, date)
);
```

### 6.3 Key Dependencies (KMP Shared)

| Dependency | Purpose |
|-----------|---------|
| SQLDelight | Cross-platform SQLite |
| kotlinx-serialization | JSON serialization for events |
| kotlinx-datetime | Cross-platform date/time |
| Ktor (optional, v1) | HTTP client for exchange rate API |
| kotlinx-coroutines | Async operations |

### 6.4 Image Storage Strategy

- Original receipt saved at capture resolution
- Compressed thumbnail generated (300px wide) for list views
- Images stored in app-sandboxed directory, referenced by UUID filename
- Image paths stored in DB relative to app container (portable across backup/restore)
- Deferred: image deduplication, progressive JPEG for large receipts

---

## 7. Testing Strategy

### 7.1 Shared Test Vectors

JSON fixtures in `test-vectors/` validated by both platforms:

```json
// test-vectors/ocr/receipt_us_restaurant.json
{
  "input_text": "DOWNTOWN GRILL\n123 Main St\nDate: 03/15/2026\n\nBurger  $12.50\nBeer     $8.00\nSubtotal $20.50\nTax       $1.74\nTotal    $22.24\nThank you!",
  "expected": {
    "vendor": "DOWNTOWN GRILL",
    "date": "2026-03-15",
    "currency": "USD",
    "total": "22.24",
    "tax": "1.74"
  }
}
```

### 7.2 Test Layers

| Layer | Scope | Framework |
|-------|-------|-----------|
| KMP unit tests | Business logic, event replay, OCR parsing, export | kotlin.test |
| Shared integration tests | Event log -> materialized state consistency | kotlin.test + SQLDelight in-memory |
| Test vector validation | Both platforms produce identical results from same input | Platform test runners consuming shared JSON |
| Android UI tests | Compose UI flows | Compose UI testing |
| iOS UI tests | SwiftUI flows | XCTest + ViewInspector |
| Android instrumented | OCR integration, camera flow | AndroidX Test |
| iOS integration | Vision OCR, camera flow | XCTest |

---

## 8. Version Roadmap

### v1.0 (This Spec)
- Expense capture (manual + receipt scan with moderate OCR)
- Trip management
- Category and tag organization
- Currency handling with manual + opportunistic online rates
- Per-trip reports with CSV export
- Biometric + PIN app lock
- Local backup/restore via event log archive
- iOS + Android

### v1.x (Fast Follows)
- macOS companion app (SwiftUI, same KMP shared module)
- Widgets (iOS/Android) for quick capture
- Recurring expenses
- Budget per trip / per category with alerts
- Search across all expenses

### v2.0
- Multi-device sync (iCloud / Google Drive / self-hosted)
- E2E encryption for synced data
- PDF report generation with receipt thumbnails
- Line-item OCR extraction
- Shared trips (multi-user)
- Corporate expense report templates

---

## 9. Implementation Strategy -- TDD Validation First

Before building the full shared module, we validate the riskiest architectural assumption:
**KMP coroutines + SQLDelight on iOS/Kotlin Native.**

### Phase 0: Coroutine Validation (Go/No-Go Gate)

Build a minimal KMP shared module with:

1. **Event log repository** using SQLDelight + coroutines
2. **Tests exercising:**
   - Concurrent event writes from multiple coroutines
   - SQLDelight query execution from background dispatchers
   - `Flow` collection for reactive UI state updates
   - Coroutine cancellation / structured concurrency behavior
   - Calling suspend functions from Swift (via generated Obj-C interop)
3. **Run on both platforms** -- Android emulator + iOS simulator

**Pass criteria:**
- All tests green on both platforms
- No deadlocks, memory leaks, or threading crashes on iOS
- SQLDelight query latency on iOS main thread < 16ms for typical reads
- Flow updates arrive reliably on iOS UI thread

**If validation fails:** pivot to pure native implementations (Room + coroutines on Android, Core Data + Swift concurrency on iOS) with a shared spec / test-vector contract instead of shared code.

### Phase 1+: Build Out (After Phase 0 Passes)

Proceed with full KMP shared module as specified in this document.

---

## 10. Resolved Decisions

1. **Image compression format** -- **HEIF**. Smaller file size, native support on both platforms. Export to JPEG/PNG when sharing outside the app.
2. **KMP coroutine threading model** -- validate via Phase 0 TDD before committing. See Section 9.

## 11. Open Decisions (Resolve During Implementation)

1. **Exchange rate API provider** -- evaluate frankfurter.app (free, no key) vs. exchangerate.host (free tier with key) vs. bundling a static fallback table
2. **Accessibility audit** -- schedule after first UI milestone on each platform
