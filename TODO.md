# TODO -- Travel Expenses v1.0

Status: **Phase 0 + Phase 1 + Phase 2 + Phase 3 (Android App) complete.**

Full spec: `SPEC.md`. Build/test commands: `CLAUDE.md`. Machine paths: `LOCAL_BUILD_ENV.md`.

---

## Completed

- [x] **Phase 0** -- KMP coroutine + SQLDelight validation (25 tests)
- [x] **Phase 1** -- Event types (11), materialized state (6 tables), replay engine, read-side repos, exchange rate table
- [x] **Phase 2** -- KMP shared logic: OCR parser, currency converter, CSV export, business rules, sync foundation
- [x] **Phase 3** -- Android app: Jetpack Compose UI, DI (Koin), navigation, ML Kit OCR, BiometricPrompt + PIN security, Ktor exchange rate client

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

---

## Remaining

### Platform: iOS (`ios/`)

#### OCR Integration
- [ ] Apple Vision text recognition wrapper implementing `OcrEngine` interface
- [ ] Camera capture / photo picker for receipt images
- [ ] Image preprocessing before OCR

#### UI (SwiftUI)
- [ ] Trip list screen
- [ ] Expense entry screen (manual + OCR review)
- [ ] Receipt camera capture flow
- [ ] Category/tag pickers
- [ ] Trip summary / reports with charts
- [ ] CSV export share sheet
- [ ] Settings screen

#### Security
- [ ] Face ID / Touch ID via LocalAuthentication
- [ ] PIN fallback
- [ ] Auto-lock timeout
- [ ] Hide content in app switcher

**Context:** SPEC.md Sections 4.1, 4.6, 5.1-5.3, 6.1 (project structure)

---

### Android: Polish & Enhancements
- [ ] Image preprocessing (deskew, contrast) before OCR
- [ ] Hide content in app switcher when locked
- [ ] Vendor name autocomplete from history
- [ ] Tag multi-select picker with inline creation
- [ ] Date picker (calendar) integration
- [ ] Charts in Reports (pie/bar for category distribution)
- [ ] Default categories initialization on first launch

---

## Open Decisions (SPEC.md Section 11)

1. ~~**Exchange rate API provider**~~ -- **Resolved**: frankfurter.app (free, no key required)
2. **Accessibility audit** -- schedule after first UI milestone on each platform

---

## Suggested Next Steps

1. **iOS app skeleton** -- DI, navigation, basic SwiftUI screens
2. **Android polish** -- date picker, tag picker, charts, vendor autocomplete
3. **Image preprocessing** -- deskew/contrast before OCR on both platforms
4. **Accessibility** -- audit + fix labels, contrast, scaling
5. **End-to-end testing** -- full capture flow on Android device
