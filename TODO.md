# TODO -- Travel Expenses v1.0

Status: **Phase 0 + Phase 1 + Phase 2 (shared KMP logic) complete.** OCR parser, currency converter, CSV export, and business rules implemented with full test coverage.

Full spec: `SPEC.md`. Build/test commands: `CLAUDE.md`. Machine paths: `LOCAL_BUILD_ENV.md`.

---

## Completed

- [x] **Phase 0** -- KMP coroutine + SQLDelight validation (25 tests)
- [x] **Phase 1** -- Event types (11), materialized state (6 tables), replay engine, read-side repos, exchange rate table
- [x] **Phase 2** -- Shared KMP business logic (OCR parser, currency converter, CSV export, validation, defaults)

### Phase 2 Details

#### OCR Result Parser (`shared/.../ocr/`)
- [x] `OcrResult` data class with per-field confidence scores
- [x] `OcrParser` with regex + heuristic extraction
- [x] Total: pattern match "total", "amount due", "grand total", "balance due", largest number fallback
- [x] Date: ISO, US (MM/DD/YYYY), EU (DD.MM.YYYY), written month ("Jan 15, 2026"), 2-digit year
- [x] Vendor: top-of-receipt heuristic with address/phone filtering and stopword filtering
- [x] Currency: explicit code detection (20 currencies) + symbol detection ($, EUR, GBP, JPY, CHF)
- [x] Tax: "tax", "VAT", "GST", "HST", "sales tax" pattern matching
- [x] 5 test vectors in `test-vectors/ocr/` (US restaurant, EU cafe, UK pub, ISO date, minimal)
- [x] 30+ unit tests in `OcrParserTest`

#### Currency Conversion (`shared/.../currency/`)
- [x] `CurrencyConverter` with 4-tier rate resolution: identity, exact date, latest, inverse, static fallback
- [x] `StaticRates` fallback table for 20 common currency pairs (cross-rate via USD)
- [x] `computeTripTotal()` for trip summary aggregation with unconvertible expense tracking
- [x] 13 unit tests in `CurrencyConverterTest` + 5 in `StaticRatesTest`

#### CSV Export (`shared/.../export/`)
- [x] `CsvExporter` -- one row per expense, 14 columns, UTF-8 with BOM for Excel
- [x] Export scopes: `exportTrip()`, `exportDateRange()`, `exportAll()`
- [x] Includes converted amounts in trip base currency via `CurrencyConverter`
- [x] Proper CSV escaping (commas, quotes, newlines)
- [x] 10 unit tests in `CsvExporterTest`

#### Business Rules & Validation (`shared/.../validation/`)
- [x] `ExpenseValidator` -- amount > 0, required fields, valid ISO 4217 currency (44 codes), tax validation
- [x] `DuplicateDetector` -- heuristic detection by (amount, vendor, date) with case-insensitive vendor matching
- [x] `DefaultCategories` -- 9 default categories per SPEC.md Section 3.2, with event generation for seeding
- [x] 14 `ExpenseValidatorTest` + 8 `DuplicateDetectorTest` + 5 `DefaultCategoriesTest`
- [ ] Optional Ktor HTTP client for fetching rates from frankfurter.app or exchangerate.host

---

## Remaining KMP Shared Logic

### Sync Foundation (`shared/.../sync/`)
Local backup/restore via event log archive (v1 scope, no cloud).

- [ ] Export full event log + image references as archive
- [ ] Import archive on new device (full restore via `rebuildAll()`)
- [ ] Event log merge logic / vector clocks (prep for v2 multi-device sync)

**Context:** SPEC.md Section 4.7

---

## Platform: Android (`android/`)

### OCR Integration
- [ ] ML Kit text recognition wrapper behind `expect/actual` interface
- [ ] Camera capture / gallery picker for receipt images
- [ ] Image preprocessing (deskew, contrast) before OCR

### UI (Jetpack Compose)
- [ ] Trip list screen
- [ ] Expense entry screen (manual + OCR review)
- [ ] Receipt camera capture flow
- [ ] Category/tag pickers
- [ ] Trip summary / reports with charts
- [ ] CSV export share sheet
- [ ] Settings screen

### Security
- [ ] BiometricPrompt integration
- [ ] PIN fallback (6-digit)
- [ ] Auto-lock timeout
- [ ] Hide content in app switcher

**Context:** SPEC.md Sections 4.1, 4.6, 5.1-5.3, 6.1 (project structure)

---

## Platform: iOS (`ios/`)

### OCR Integration
- [ ] Apple Vision text recognition wrapper behind `expect/actual` interface
- [ ] Camera capture / photo picker for receipt images
- [ ] Image preprocessing before OCR

### UI (SwiftUI)
- [ ] Trip list screen
- [ ] Expense entry screen (manual + OCR review)
- [ ] Receipt camera capture flow
- [ ] Category/tag pickers
- [ ] Trip summary / reports with charts
- [ ] CSV export share sheet
- [ ] Settings screen

### Security
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

## Suggested Build Order (remaining)

1. **Sync foundation** -- backup/restore archive
2. **Android app skeleton** -- DI, navigation, basic screens
3. **iOS app skeleton** -- DI, navigation, basic screens
4. **Platform OCR wiring** -- connect ML Kit / Vision to KMP parser
5. **Security** -- biometric/PIN on each platform
