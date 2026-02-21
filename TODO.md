# TODO -- Travel Expenses v1.0

Status: **Phase 0 + Phase 1 + Phase 2 + Phase 3 + Phase 4 (Android Polish) + Phase 5 (iOS App) complete.**

Full spec: `SPEC.md`. Build/test commands: `CLAUDE.md`. Machine paths: `LOCAL_BUILD_ENV.md`.

---

## Completed

- [x] **Phase 0** -- KMP coroutine + SQLDelight validation (25 tests)
- [x] **Phase 1** -- Event types (11), materialized state (6 tables), replay engine, read-side repos, exchange rate table
- [x] **Phase 2** -- KMP shared logic: OCR parser, currency converter, CSV export, business rules, sync foundation
- [x] **Phase 3** -- Android app: Jetpack Compose UI, DI (Koin), navigation, ML Kit OCR, BiometricPrompt + PIN security, Ktor exchange rate client
- [x] **Phase 4** -- Android polish: date picker, vendor autocomplete, tag picker, pie charts, image preprocessing, app switcher security, default categories

### Phase 2 Details

#### OCR Result Parser (`shared/.../ocr/`)
- [x] `OcrResult` data class (vendor, date, currency, total, tax, per-field confidence)
- [x] `OcrParser` with regex + heuristic extraction for each field
- [x] Total amount: pattern match "total", "amount due", "grand total", largest number near bottom
- [x] Date: multi-format recognition (US, EU, ISO, written month names)
- [x] Vendor: top-of-receipt text heuristic (skip numbers, addresses, receipt headers)
- [x] Currency: symbol detection ($, €, £, ¥, etc.) + ISO code detection + trip locale fallback
- [x] Tax: pattern match "tax", "VAT", "GST", "HST", "sales tax" + adjacent number
- [x] 30+ OCR parser unit tests (OcrParserTest)

#### Currency Conversion (`shared/.../currency/`)
- [x] `CurrencyConverter` -- convert amount between currencies using `ExchangeRateRepository`
- [x] Trip summary aggregation: `aggregateTripTotal()` with unconverted expense tracking
- [x] Fallback: `StaticRates` table with ~40 common currency pairs
- [x] Inverse rate calculation when only reverse pair available
- [x] 15+ currency converter unit tests (CurrencyConverterTest)
- [x] Ktor HTTP client for fetching rates from frankfurter.app (`ExchangeRateService`)

#### CSV Export (`shared/.../export/`)
- [x] `CsvExporter` -- one row per expense, all fields, UTF-8 with BOM for Excel
- [x] Export scopes: single trip (`exportTrip`), date range (`exportDateRange`), all data (`exportAll`)
- [x] Include converted amounts in trip base currency (optional display column)
- [x] Category name resolution, tag label joining, RFC 4180 CSV escaping
- [x] 10+ CSV export unit tests (CsvExporterTest)

#### Business Rules & Validation (`shared/.../validation/`)
- [x] `ExpenseValidator` -- amount > 0, valid ISO 4217 currency, required fields, tax/OCR confidence range
- [x] `FieldValidator` -- individual field validation for input forms
- [x] `DuplicateDetector` -- heuristic matching (amount + currency + vendor + date ± threshold)
- [x] `DefaultCategories` -- 9 default categories from SPEC.md Section 3.2
- [x] 30+ validation/business rules unit tests (ValidationTest)

#### Sync Foundation (`shared/.../sync/`)
- [x] `EventArchive` data class with version, metadata, events, image references
- [x] `EventArchiveSerializer` -- JSON serialization/deserialization of full archives
- [x] `SyncManager` -- export archive, import archive (full restore via `rebuildAll()`)
- [x] Event log merge logic with vector clocks (prep for v2 multi-device sync)
- [x] `buildVectorClock` / `isSuperseded` for deduplication during merge
- [x] 12+ sync/archive unit tests (SyncTest)

### Phase 3 Details

#### Shared Module Additions
- [x] `ExchangeRateService` -- Ktor HTTP client fetching rates from frankfurter.app
- [x] `OcrEngine` interface -- platform-agnostic OCR abstraction (`OcrTextResult`, `TextBlock`)
- [x] Ktor dependencies added to shared module (OkHttp engine for Android, Darwin for iOS)

#### Android App Module (`android/app/`)
- [x] Gradle build with AGP, Compose compiler, Material 3, Navigation, Lifecycle, Koin
- [x] `TravelExpensesApp` Application class with Koin DI initialization
- [x] `MainActivity` with edge-to-edge Compose content

#### DI & Navigation
- [x] Koin `appModule` -- database, repositories, business logic, ViewModels
- [x] Compose Navigation with bottom nav bar (Trips, Reports, Settings)
- [x] Screen routes: TripList, TripDetail, ExpenseEntry, ExpenseEdit, OcrCapture, OcrReview, Reports, Settings

#### UI Screens (Jetpack Compose + Material 3)
- [x] Material 3 theme with dynamic color (Material You) + dark mode support
- [x] **Trip list screen** -- active/archived trips, create trip dialog, empty state
- [x] **Trip detail screen** -- summary card with total, expense list, add expense FAB (manual/scan choice)
- [x] **Expense entry screen** -- amount, currency, category dropdown, vendor, date, tax, notes, validation errors
- [x] **OCR capture screen** -- camera/gallery picker with permission handling
- [x] **OCR review screen** -- ML Kit processing, editable fields with confidence indicators
- [x] **Reports screen** -- trip selector, total/daily average, category breakdown with progress bars, CSV export via share sheet
- [x] **Settings screen** -- security toggles, auto-lock timeout, data export/import, PIN setup dialog

#### OCR Integration
- [x] `MlKitOcrEngine` -- Google ML Kit text recognition (on-device, no network)
- [x] Camera capture via `TakePicture` contract + gallery via `GetContent` contract
- [x] FileProvider for secure image URI sharing
- [x] OCR result → `OcrParser` → editable review form → expense creation

#### Security
- [x] `BiometricHelper` -- BiometricPrompt wrapper (fingerprint/face, fallback to PIN)
- [x] `PinManager` -- 6-digit PIN with SHA-256 hashed storage
- [x] `LockScreen` composable -- PIN entry with auto-verify, biometric button
- [x] Auto-lock timeout settings (immediate, 1 min, 5 min, 15 min)

### Phase 4 Details

#### Android Polish & Enhancements
- [x] **Default categories initialization** -- `DefaultCategoryInitializer` seeds 9 default categories on first launch via `CategoryCreated` events
- [x] **Material 3 date picker** -- `DatePickerDialog` in expense entry screen replaces read-only text field
- [x] **Vendor autocomplete** -- `ExposedDropdownMenuBox` with distinct vendor names from expense history
- [x] **Tag multi-select picker** -- tag selection dialog with checkboxes, inline tag creation via `TagCreated` events, chip display with removal
- [x] **Pie chart for category distribution** -- Canvas-based pie chart with color-coded legend in reports screen
- [x] **Category name resolution** -- reports now show human-readable category names instead of raw IDs
- [x] **App switcher security** -- `FLAG_SECURE` set/cleared dynamically based on PIN/biometric state
- [x] **Image preprocessing for OCR** -- `ImagePreprocessor` applies grayscale + contrast enhancement before ML Kit text recognition

#### Shared Module Additions
- [x] `TagRepository` interface + `SqlDelightTagRepository` implementation (observe/query tags)
- [x] `selectDistinctVendors` SQL query for vendor autocomplete
- [x] `getDistinctVendors()` added to `ExpenseRepository` interface + implementation

---

### Phase 5 Details

#### iOS App (`ios/`)

##### KMP shared additions
- [x] `FlowBridge.kt` in `iosMain/` -- `TripFlowBridge`, `ExpenseFlowBridge`, `CategoryFlowBridge`, `TagFlowBridge` callback wrappers that collect Kotlin Flows on the Main dispatcher and notify Swift observers

##### OCR Integration
- [x] `VisionOcrEngine.swift` -- Apple Vision `VNRecognizeTextRequest` wrapper implementing the KMP `OcrEngine` interface (on-device, no network)
- [x] `CaptureView.swift` -- camera capture via `UIImagePickerController` + photo library via `PhotosPicker`
- [x] `ImagePreprocessor.swift` -- grayscale + contrast enhancement via CoreImage before Vision OCR
- [x] `OcrReviewView.swift` -- editable review form with per-field confidence indicator

##### UI (SwiftUI)
- [x] `TripListView.swift` -- active/archived trip list, create trip sheet, swipe-to-archive
- [x] `TripDetailView.swift` -- summary card with total, expense list with swipe-to-delete, add expense FAB
- [x] `ExpenseEntryView.swift` -- amount/currency/category/date/vendor/tax/notes form, OCR pre-fill
- [x] `OcrReviewView.swift` / `CaptureView.swift` -- receipt camera capture + review flow
- [x] Category dropdown + tag multi-select picker with inline tag creation (`TagPickerSheet`)
- [x] `ReportsView.swift` -- trip selector, summary cards (total/count/daily avg), pie chart, category breakdown progress bars
- [x] `PieChartView.swift` -- Canvas-based pie chart with colour-coded legend
- [x] CSV export via system share sheet (`ShareSheet`)
- [x] `SettingsView.swift` -- biometric/PIN toggles, auto-lock picker, archive export/import, CSV export

##### Security
- [x] `AuthManager.swift` -- Face ID / Touch ID via `LocalAuthentication`, 6-digit PIN with PBKDF2-SHA256 stored in Keychain
- [x] `LockView.swift` -- PIN entry with auto-verify + biometric prompt on appear
- [x] Auto-lock timeout (immediate / 1 min / 5 min / 15 min) with foreground/background tracking
- [x] `ContentView.swift` -- `.privacySensitive()` hides content in app switcher when security is enabled

##### Project structure
- [x] `TravelExpenses.xcodeproj/project.pbxproj` -- Xcode project linking `Shared.xcframework` from KMP build
- [x] `Info.plist` -- camera/photo/Face-ID usage descriptions, no ITSAppUsesNonExemptEncryption
- [x] `Assets.xcassets` -- AccentColor + AppIcon catalogues

##### DI
- [x] `ServiceContainer.swift` -- `@MainActor ObservableObject` initialising all KMP repos and business-logic singletons; propagated via `@EnvironmentObject`
- [x] `DefaultCategoryInitializer.swift` -- seeds 9 default categories on first launch via `CategoryCreated` events

---

## Remaining

*(none -- all v1.0 features are implemented)*

---

## Open Decisions (SPEC.md Section 11)

1. ~~**Exchange rate API provider**~~ -- **Resolved**: frankfurter.app (free, no key required)
2. **Accessibility audit** -- schedule after first UI milestone on each platform

---

## Suggested Next Steps

1. **Accessibility** -- audit + fix labels, contrast, dynamic type scaling on both platforms
2. **End-to-end testing** -- full capture flow on Android device + iOS simulator
3. **Performance** -- SQLite query benchmarks, lazy loading for large trip histories
4. **v1.x** -- macOS companion app (SwiftUI, same KMP module), widgets, recurring expenses
