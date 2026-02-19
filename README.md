# Travel Expenses

A privacy-first, offline-first mobile expense tracker for travelers. Capture receipts, extract data via on-device OCR, organize by trip, and export reports -- all without requiring an internet connection or cloud account.

## Architecture

- **Kotlin Multiplatform (KMP)** shared logic: data models, event log, sync protocol, OCR parsing, currency handling, export
- **SwiftUI** native UI for iOS and macOS
- **Jetpack Compose** native UI for Android
- **On-device OCR**: Apple Vision (iOS), ML Kit (Android)
- **SQLDelight** for cross-platform local storage
- **Append-only event log** for reliable sync and full audit trail

## Status

See [SPEC.md](SPEC.md) for the full product specification.
