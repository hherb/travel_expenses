package com.travelexpenses.ocr

/**
 * Extracts structured expense fields from raw OCR text using regex + heuristics.
 * Pure Kotlin, no platform dependencies.
 *
 * Strategy per field (see SPEC.md Section 4.2):
 * - Total: pattern match "total", "amount due", or largest number near bottom
 * - Date: multi-format recognition (US, EU, ISO), locale-aware
 * - Vendor: top-of-receipt text heuristic
 * - Currency: symbol detection ($, EUR, etc.)
 * - Tax: pattern match "tax", "VAT", "GST" + adjacent number
 */
object OcrParser {

    // -- Confidence constants --

    private const val CONFIDENCE_TOTAL_LABELED = 0.9f
    private const val CONFIDENCE_TOTAL_FALLBACK = 0.5f
    private const val CONFIDENCE_TAX = 0.8f
    private const val CONFIDENCE_DATE_ISO = 0.95f
    private const val CONFIDENCE_DATE_WRITTEN = 0.9f
    private const val CONFIDENCE_DATE_LABELED = 0.85f
    private const val CONFIDENCE_DATE_UNLABELED = 0.6f
    private const val CONFIDENCE_CURRENCY_CODE = 0.9f
    private const val CONFIDENCE_CURRENCY_SYMBOL = 0.8f
    private const val CONFIDENCE_CURRENCY_DOLLAR = 0.6f
    private const val CONFIDENCE_VENDOR_FIRST_LINE = 0.7f
    private const val CONFIDENCE_VENDOR_SUBSEQUENT = 0.5f
    private const val CONFIDENCE_VENDOR_FALLBACK = 0.3f

    /** Number of lines from the top to inspect for vendor name. */
    private const val VENDOR_SCAN_LINES = 5

    /** Minimum word length to be considered meaningful for vendor detection. */
    private const val MIN_MEANINGFUL_WORD_LENGTH = 2

    /**
     * Parse raw OCR text and extract structured expense fields.
     *
     * @param rawText The raw text output from an OCR engine.
     * @return An [OcrResult] with extracted fields and per-field confidence scores.
     */
    fun parse(rawText: String): OcrResult {
        if (rawText.isBlank()) return OcrResult()

        val lines = rawText.lines()

        val totalResult = extractTotal(lines)
        val taxResult = extractTax(lines)
        val dateResult = extractDate(rawText)
        val currencyResult = extractCurrency(rawText)
        val vendorResult = extractVendor(lines)

        return OcrResult(
            vendor = vendorResult.value,
            date = dateResult.value,
            currency = currencyResult.value,
            total = totalResult.value,
            tax = taxResult.value,
            confidence = FieldConfidence(
                vendor = vendorResult.confidence,
                date = dateResult.confidence,
                currency = currencyResult.confidence,
                total = totalResult.confidence,
                tax = taxResult.confidence,
            ),
        )
    }

    // -- Total extraction --

    private val totalPatterns = listOf(
        Regex("""(?i)\b(?:total|total\s+due|amount\s+due|grand\s+total|balance\s+due)\s*[:\s]?\s*[$\u20AC\u00A3]?\s*(\d{1,9}[.,]\d{2})\b"""),
        Regex("""(?i)\b(?:total|total\s+due|amount\s+due|grand\s+total|balance\s+due)\s*[:\s]?\s*[$\u20AC\u00A3]?\s*(\d{1,9})\b"""),
    )

    /**
     * Extract the total amount from receipt lines.
     * Searches from bottom up since the grand total typically appears near the bottom.
     */
    internal fun extractTotal(lines: List<String>): ExtractionResult {
        // Search from bottom up -- "total" near the bottom is more likely to be the grand total
        for (line in lines.reversed()) {
            for (pattern in totalPatterns) {
                val match = pattern.find(line)
                if (match != null) {
                    val amount = normalizeAmount(match.groupValues[1])
                    return ExtractionResult(amount, CONFIDENCE_TOTAL_LABELED)
                }
            }
        }

        // Fallback: find the largest monetary amount near the bottom half
        val bottomHalf = lines.drop(lines.size / 2)
        val amounts = mutableListOf<Pair<String, Int>>() // amount to line index
        val amountPattern = Regex("""\$?\s*(\d{1,9}[.,]\d{2})\b""")
        for ((idx, line) in bottomHalf.withIndex()) {
            for (match in amountPattern.findAll(line)) {
                amounts.add(normalizeAmount(match.groupValues[1]) to idx)
            }
        }

        if (amounts.isNotEmpty()) {
            val largest = amounts.maxByOrNull { it.first.toDoubleOrNull() ?: 0.0 }
            if (largest != null) {
                return ExtractionResult(largest.first, CONFIDENCE_TOTAL_FALLBACK)
            }
        }

        return ExtractionResult.EMPTY
    }

    // -- Tax extraction --

    private val taxPatterns = listOf(
        Regex("""(?i)\b(?:tax|vat|gst|hst|sales\s+tax)\s*[:\s]?\s*[$\u20AC\u00A3]?\s*(\d{1,9}[.,]\d{2})\b"""),
        Regex("""(?i)[$\u20AC\u00A3]?\s*(\d{1,9}[.,]\d{2})\s*(?:tax|vat|gst|hst)"""),
    )

    /**
     * Extract tax amount from receipt lines.
     * Matches "tax", "VAT", "GST", "HST", "sales tax" followed by a monetary amount.
     */
    internal fun extractTax(lines: List<String>): ExtractionResult {
        for (line in lines) {
            for (pattern in taxPatterns) {
                val match = pattern.find(line)
                if (match != null) {
                    val amount = normalizeAmount(match.groupValues[1])
                    return ExtractionResult(amount, CONFIDENCE_TAX)
                }
            }
        }
        return ExtractionResult.EMPTY
    }

    // -- Date extraction --

    /** US format: MM/DD/YYYY or MM-DD-YYYY. */
    private val usDatePattern = Regex("""(\d{1,2})[/\-](\d{1,2})[/\-](20\d{2}|\d{2})""")
    /** EU format: DD.MM.YYYY. */
    private val euDatePattern = Regex("""(\d{1,2})\.(\d{1,2})\.(20\d{2}|\d{2})""")
    /** ISO 8601 format: YYYY-MM-DD. */
    private val isoDatePattern = Regex("""(20\d{2})-(\d{1,2})-(\d{1,2})""")

    /** Mapping of English month names/abbreviations to month numbers (1-12). */
    private val monthNames = mapOf(
        "jan" to 1, "january" to 1, "feb" to 2, "february" to 2,
        "mar" to 3, "march" to 3, "apr" to 4, "april" to 4,
        "may" to 5, "jun" to 6, "june" to 6,
        "jul" to 7, "july" to 7, "aug" to 8, "august" to 8,
        "sep" to 9, "september" to 9, "oct" to 10, "october" to 10,
        "nov" to 11, "november" to 11, "dec" to 12, "december" to 12,
    )

    /** Written month format: "Jan 15, 2026" or "January 15, 2026". */
    private val writtenDatePattern1 = Regex(
        """(?i)(${monthNames.keys.joinToString("|")})\s+(\d{1,2}),?\s+(20\d{2}|\d{2})"""
    )
    /** Written month format: "15 Jan 2026". */
    private val writtenDatePattern2 = Regex(
        """(?i)(\d{1,2})\s+(${monthNames.keys.joinToString("|")})\s+(20\d{2}|\d{2})"""
    )

    /**
     * Extract a date from OCR text, trying multiple formats in priority order:
     * ISO > written month > labeled ("Date: ...") > unlabeled US/EU.
     */
    internal fun extractDate(text: String): ExtractionResult {
        // ISO format -- highest confidence
        isoDatePattern.find(text)?.let { match ->
            val year = match.groupValues[1].toInt()
            val month = match.groupValues[2].toInt()
            val day = match.groupValues[3].toInt()
            if (isValidDate(year, month, day)) {
                return ExtractionResult(formatDate(year, month, day), CONFIDENCE_DATE_ISO)
            }
        }

        // Written month format: "Jan 15, 2026" or "January 15, 2026"
        writtenDatePattern1.find(text)?.let { match ->
            val monthStr = match.groupValues[1].lowercase()
            val month = monthNames[monthStr]
            val day = match.groupValues[2].toInt()
            val year = normalizeYear(match.groupValues[3].toInt())
            if (month != null && isValidDate(year, month, day)) {
                return ExtractionResult(formatDate(year, month, day), CONFIDENCE_DATE_WRITTEN)
            }
        }

        // Written month format: "15 Jan 2026"
        writtenDatePattern2.find(text)?.let { match ->
            val day = match.groupValues[1].toInt()
            val monthStr = match.groupValues[2].lowercase()
            val month = monthNames[monthStr]
            val year = normalizeYear(match.groupValues[3].toInt())
            if (month != null && isValidDate(year, month, day)) {
                return ExtractionResult(formatDate(year, month, day), CONFIDENCE_DATE_WRITTEN)
            }
        }

        // Try labeled date first ("Date: ...")
        val labeledDatePattern = Regex("""(?i)date\s*[:\s]\s*(.+)""")
        for (line in text.lines()) {
            val labelMatch = labeledDatePattern.find(line)
            if (labelMatch != null) {
                val datePart = labelMatch.groupValues[1].trim()
                val parsed = parseDateFragment(datePart)
                if (parsed != null) return ExtractionResult(parsed, CONFIDENCE_DATE_LABELED)
            }
        }

        // Unlabeled date patterns
        val parsed = parseDateFragment(text)
        if (parsed != null) return ExtractionResult(parsed, CONFIDENCE_DATE_UNLABELED)

        return ExtractionResult.EMPTY
    }

    /**
     * Attempt to parse a date from a text fragment using US then EU format.
     * @return ISO 8601 date string, or null if no valid date found.
     */
    private fun parseDateFragment(text: String): String? {
        // US format: MM/DD/YYYY
        usDatePattern.find(text)?.let { match ->
            val month = match.groupValues[1].toInt()
            val day = match.groupValues[2].toInt()
            val year = normalizeYear(match.groupValues[3].toInt())
            if (isValidDate(year, month, day)) {
                return formatDate(year, month, day)
            }
        }

        // EU format: DD.MM.YYYY
        euDatePattern.find(text)?.let { match ->
            val day = match.groupValues[1].toInt()
            val month = match.groupValues[2].toInt()
            val year = normalizeYear(match.groupValues[3].toInt())
            if (isValidDate(year, month, day)) {
                return formatDate(year, month, day)
            }
        }

        return null
    }

    // -- Currency extraction --

    /** Mapping of currency symbols to ISO 4217 codes. */
    private val currencySymbols = mapOf(
        "$" to "USD",
        "\u20AC" to "EUR",   // Euro sign €
        "\u00A3" to "GBP",   // Pound sign £
        "\u00A5" to "JPY",   // Yen sign ¥
        "CHF" to "CHF",
    )

    /** Pattern for explicit ISO 4217 currency codes in text. */
    private val currencyCodePattern = Regex("""(?i)\b(USD|EUR|GBP|JPY|CHF|CAD|AUD|NZD|SEK|NOK|DKK|CNY|HKD|SGD|KRW|INR|BRL|MXN|ZAR|THB)\b""")

    /**
     * Extract currency from text by explicit ISO code or symbol detection.
     * Explicit codes have higher confidence than symbol-based inference.
     */
    internal fun extractCurrency(text: String): ExtractionResult {
        // Explicit currency code -- highest confidence
        currencyCodePattern.find(text)?.let { match ->
            return ExtractionResult(match.groupValues[1].uppercase(), CONFIDENCE_CURRENCY_CODE)
        }

        // Symbol detection
        for ((symbol, code) in currencySymbols) {
            if (text.contains(symbol)) {
                // $ is ambiguous -- could be USD, CAD, AUD, etc.
                val confidence = if (symbol == "$") CONFIDENCE_CURRENCY_DOLLAR else CONFIDENCE_CURRENCY_SYMBOL
                return ExtractionResult(code, confidence)
            }
        }

        return ExtractionResult.EMPTY
    }

    // -- Vendor extraction --

    /** Words commonly found on receipts that are not vendor names. */
    private val vendorStopwords = setOf(
        "receipt", "invoice", "date", "total", "subtotal", "tax", "change",
        "cash", "credit", "debit", "visa", "mastercard", "amex", "thank",
        "thanks", "you", "welcome", "order", "sale", "register", "store",
        "tel", "phone", "fax", "www", "http", "email",
    )

    /**
     * Extract vendor name from the first few lines of receipt text.
     * Filters out addresses, phone numbers, dates, and decorative lines.
     */
    internal fun extractVendor(lines: List<String>): ExtractionResult {
        val candidates = lines
            .take(VENDOR_SCAN_LINES)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .filter { line ->
                !line.matches(Regex("""^\d[\d\s\-().]+$""")) && // phone numbers
                !line.matches(Regex("""^\d+\s+\w+\s+(St|Ave|Rd|Blvd|Dr|Ln|Ct|Way|Pl|Pkwy|Hwy)\.?.*""", RegexOption.IGNORE_CASE)) && // addresses
                !usDatePattern.containsMatchIn(line) &&
                !isoDatePattern.containsMatchIn(line) &&
                !line.matches(Regex("""^[#\-=*]+$""")) // decorative lines
            }

        if (candidates.isEmpty()) return ExtractionResult.EMPTY

        // Pick the first candidate that has at least one word not in stopwords
        for (candidate in candidates) {
            val words = candidate.lowercase().split(Regex("""\s+"""))
            val meaningful = words.any { it !in vendorStopwords && it.length >= MIN_MEANINGFUL_WORD_LENGTH }
            if (meaningful) {
                val confidence = if (candidate == candidates.first()) CONFIDENCE_VENDOR_FIRST_LINE else CONFIDENCE_VENDOR_SUBSEQUENT
                return ExtractionResult(candidate, confidence)
            }
        }

        return ExtractionResult(candidates.first(), CONFIDENCE_VENDOR_FALLBACK)
    }

    // -- Utilities --

    /** Replace comma decimal separators with dots for consistent parsing. */
    private fun normalizeAmount(raw: String): String {
        return raw.replace(',', '.')
    }

    /** Convert 2-digit years to 4-digit (assumes 2000s). */
    private fun normalizeYear(year: Int): Int {
        return if (year < 100) 2000 + year else year
    }

    /** Validate a date against calendar rules including leap years. */
    private fun isValidDate(year: Int, month: Int, day: Int): Boolean {
        if (month < 1 || month > 12) return false
        if (day < 1 || day > 31) return false
        if (year < 2000 || year > 2099) return false
        val maxDays = when (month) {
            2 -> if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 29 else 28
            4, 6, 9, 11 -> 30
            else -> 31
        }
        return day <= maxDays
    }

    /** Format a date as ISO 8601 (yyyy-MM-dd). */
    private fun formatDate(year: Int, month: Int, day: Int): String {
        return "$year-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"
    }
}

/**
 * Result of a single field extraction attempt.
 * Contains the extracted value (or null) and its confidence score (or null).
 */
data class ExtractionResult(
    val value: String?,
    val confidence: Float?,
) {
    companion object {
        /** Empty result indicating no extraction was possible. */
        val EMPTY = ExtractionResult(null, null)
    }
}
