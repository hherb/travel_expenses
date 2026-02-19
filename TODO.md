# TODO -- Travel Expenses v1.0

Status: **Phase 0 + Phase 1 complete.** 68 Android tests, 73 iOS tests, all green.

Full spec: `SPEC.md`. Build/test commands: `CLAUDE.md`. Machine paths: `LOCAL_BUILD_ENV.md`.

---

## Completed

- [x] **Phase 0** -- KMP coroutine + SQLDelight validation (25 tests)
- [x] **Phase 1** -- Event types (11), materialized state (6 tables), replay engine, read-side repos, exchange rate table

---

## Remaining KMP Shared Logic

### OCR Result Parser (`shared/.../ocr/`)
Parse raw OCR text into structured expense fields. Pure Kotlin, no platform dependencies.

- [ ] `OcrResult` data class (vendor, date, currency, total, tax, per-field confidence)
- [ ] `OcrParser` with regex + heuristic extraction for each field
- [ ] Total amount: pattern match "total", "amount due", largest number near bottom
- [ ] Date: multi-format recognition (US, EU, ISO), locale-aware
- [ ] Vendor: top-of-receipt text heuristic
- [ ] Currency: symbol detection ($, EUR, etc.) or inferred from trip locale
- [ ] Tax: pattern match "tax", "VAT", "GST" + adjacent number
- [ ] Test vectors in `test-vectors/ocr/` (JSON fixtures)

**Context:** SPEC.md Section 4.2 (OCR Pipeline), Section 7.1 (test vector format)

### Currency Conversion (`shared/.../currency/`)
Display-only conversion using cached exchange rates.

- [ ] `CurrencyConverter` -- convert amount between currencies using `ExchangeRateRepository`
- [ ] Trip summary aggregation: total spend in base currency
- [ ] Fallback: static rate table for common pairs when no API/manual rate available
- [ ] Optional Ktor HTTP client for fetching rates from frankfurter.app or exchangerate.host

**Context:** SPEC.md Section 4.4, `ExchangeRateRepository` (already implemented), `ExchangeRate.sq`

### CSV Export (`shared/.../export/`)
Generate CSV reports from materialized state.

- [ ] `CsvExporter` -- one row per expense, all fields, UTF-8 with BOM for Excel
- [ ] Export scopes: single trip, date range, all data
- [ ] Include converted amounts in trip base currency (display column)
- [ ] Test vectors in `test-vectors/export/`

**Context:** SPEC.md Section 4.5

### Business Rules & Validation (`shared/.../model/` or `shared/.../validation/`)
- [ ] Expense validation (amount > 0, required fields, valid currency code)
- [ ] Duplicate detection heuristic (same amount + vendor + date within threshold)
- [ ] Default category seeding (see SPEC.md Section 3.2 for the 10 default categories)

**Context:** SPEC.md Section 3.2 (default categories), Section 2.2 (shared module boundary)

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

## Suggested Build Order

1. **OCR parser** -- pure KMP, no platform deps, TDD with test vectors
2. **Currency converter** -- builds on existing `ExchangeRateRepository`
3. **CSV export** -- builds on existing read-side repositories
4. **Business rules** -- validation, defaults, duplicate detection
5. **Android app skeleton** -- DI, navigation, basic screens
6. **iOS app skeleton** -- DI, navigation, basic screens
7. **Platform OCR wiring** -- connect ML Kit / Vision to KMP parser
8. **Security** -- biometric/PIN on each platform
9. **Sync foundation** -- backup/restore archive
