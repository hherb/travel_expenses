# TODO -- Travel Expenses v1.0

Status: **Phase 0 + Phase 1 + Phase 2 (KMP Shared Logic) complete.**

Full spec: `SPEC.md`. Build/test commands: `CLAUDE.md`. Machine paths: `LOCAL_BUILD_ENV.md`.

---

## Completed

- [x] **Phase 0** -- KMP coroutine + SQLDelight validation (25 tests)
- [x] **Phase 1** -- Event types (11), materialized state (6 tables), replay engine, read-side repos, exchange rate table
- [x] **Phase 2** -- KMP shared logic: OCR parser, currency converter, CSV export, business rules, sync foundation

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
- [ ] Optional Ktor HTTP client for fetching rates from frankfurter.app or exchangerate.host

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

---

## Remaining

### Platform: Android (`android/`)

#### OCR Integration
- [ ] ML Kit text recognition wrapper behind `expect/actual` interface
- [ ] Camera capture / gallery picker for receipt images
- [ ] Image preprocessing (deskew, contrast) before OCR

#### UI (Jetpack Compose)
- [ ] Trip list screen
- [ ] Expense entry screen (manual + OCR review)
- [ ] Receipt camera capture flow
- [ ] Category/tag pickers
- [ ] Trip summary / reports with charts
- [ ] CSV export share sheet
- [ ] Settings screen

#### Security
- [ ] BiometricPrompt integration
- [ ] PIN fallback (6-digit)
- [ ] Auto-lock timeout
- [ ] Hide content in app switcher

**Context:** SPEC.md Sections 4.1, 4.6, 5.1-5.3, 6.1 (project structure)

---

### Platform: iOS (`ios/`)

#### OCR Integration
- [ ] Apple Vision text recognition wrapper behind `expect/actual` interface
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

## Open Decisions (SPEC.md Section 11)

1. **Exchange rate API provider** -- evaluate frankfurter.app (free, no key) vs exchangerate.host (free tier) vs static fallback
2. **Accessibility audit** -- schedule after first UI milestone on each platform

---

## Suggested Next Steps

1. **Android app skeleton** -- DI, navigation, basic screens
2. **iOS app skeleton** -- DI, navigation, basic screens
3. **Platform OCR wiring** -- connect ML Kit / Vision to KMP parser
4. **Security** -- biometric/PIN on each platform
5. **Ktor HTTP client** -- optional exchange rate fetching
