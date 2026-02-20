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

    fun parse(rawText: String): OcrResult {
        if (rawText.isBlank()) return OcrResult()

        val lines = rawText.lines()

        val totalResult = extractTotal(lines)
        val taxResult = extractTax(lines)
        val dateResult = extractDate(rawText)
        val currencyResult = extractCurrency(rawText)
        val vendorResult = extractVendor(lines)

        return OcrResult(
            vendor = vendorResult.first,
            date = dateResult.first,
            currency = currencyResult.first,
            total = totalResult.first,
            tax = taxResult.first,
            confidence = FieldConfidence(
                vendor = vendorResult.second,
                date = dateResult.second,
                currency = currencyResult.second,
                total = totalResult.second,
                tax = taxResult.second,
            ),
        )
    }

    // -- Total extraction --

    private val totalPatterns = listOf(
        Regex("""(?i)\b(?:total|total\s+due|amount\s+due|grand\s+total|balance\s+due)\s*[:\s]?\s*[$\u20AC\u00A3]?\s*(\d{1,9}[.,]\d{2})\b"""),
        Regex("""(?i)\b(?:total|total\s+due|amount\s+due|grand\s+total|balance\s+due)\s*[:\s]?\s*[$\u20AC\u00A3]?\s*(\d{1,9})\b"""),
    )

    internal fun extractTotal(lines: List<String>): Pair<String?, Float?> {
        // Search from bottom up -- "total" near the bottom is more likely to be the grand total
        for (line in lines.reversed()) {
            for (pattern in totalPatterns) {
                val match = pattern.find(line)
                if (match != null) {
                    val amount = normalizeAmount(match.groupValues[1])
                    return amount to 0.9f
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
                return largest.first to 0.5f
            }
        }

        return null to null
    }

    // -- Tax extraction --

    private val taxPatterns = listOf(
        Regex("""(?i)\b(?:tax|vat|gst|hst|sales\s+tax)\s*[:\s]?\s*[$\u20AC\u00A3]?\s*(\d{1,9}[.,]\d{2})\b"""),
        Regex("""(?i)[$\u20AC\u00A3]?\s*(\d{1,9}[.,]\d{2})\s*(?:tax|vat|gst|hst)"""),
    )

    internal fun extractTax(lines: List<String>): Pair<String?, Float?> {
        for (line in lines) {
            for (pattern in taxPatterns) {
                val match = pattern.find(line)
                if (match != null) {
                    val amount = normalizeAmount(match.groupValues[1])
                    return amount to 0.8f
                }
            }
        }
        return null to null
    }

    // -- Date extraction --

    // US format: MM/DD/YYYY or MM-DD-YYYY
    private val usDatePattern = Regex("""(\d{1,2})[/\-](\d{1,2})[/\-](20\d{2}|\d{2})""")
    // EU format: DD.MM.YYYY
    private val euDatePattern = Regex("""(\d{1,2})\.(\d{1,2})\.(20\d{2}|\d{2})""")
    // ISO format: YYYY-MM-DD
    private val isoDatePattern = Regex("""(20\d{2})-(\d{1,2})-(\d{1,2})""")
    // Written month: Jan 15, 2026 / 15 Jan 2026 / January 15, 2026
    private val monthNames = mapOf(
        "jan" to 1, "january" to 1, "feb" to 2, "february" to 2,
        "mar" to 3, "march" to 3, "apr" to 4, "april" to 4,
        "may" to 5, "jun" to 6, "june" to 6,
        "jul" to 7, "july" to 7, "aug" to 8, "august" to 8,
        "sep" to 9, "september" to 9, "oct" to 10, "october" to 10,
        "nov" to 11, "november" to 11, "dec" to 12, "december" to 12,
    )
    private val writtenDatePattern1 = Regex(
        """(?i)(${monthNames.keys.joinToString("|")})\s+(\d{1,2}),?\s+(20\d{2}|\d{2})"""
    )
    private val writtenDatePattern2 = Regex(
        """(?i)(\d{1,2})\s+(${monthNames.keys.joinToString("|")})\s+(20\d{2}|\d{2})"""
    )

    internal fun extractDate(text: String): Pair<String?, Float?> {
        // ISO format -- highest confidence
        isoDatePattern.find(text)?.let { match ->
            val year = match.groupValues[1].toInt()
            val month = match.groupValues[2].toInt()
            val day = match.groupValues[3].toInt()
            if (isValidDate(year, month, day)) {
                return formatDate(year, month, day) to 0.95f
            }
        }

        // Written month format: "Jan 15, 2026" or "January 15, 2026"
        writtenDatePattern1.find(text)?.let { match ->
            val monthStr = match.groupValues[1].lowercase()
            val month = monthNames[monthStr]
            val day = match.groupValues[2].toInt()
            val year = normalizeYear(match.groupValues[3].toInt())
            if (month != null && isValidDate(year, month, day)) {
                return formatDate(year, month, day) to 0.9f
            }
        }

        // Written month format: "15 Jan 2026"
        writtenDatePattern2.find(text)?.let { match ->
            val day = match.groupValues[1].toInt()
            val monthStr = match.groupValues[2].lowercase()
            val month = monthNames[monthStr]
            val year = normalizeYear(match.groupValues[3].toInt())
            if (month != null && isValidDate(year, month, day)) {
                return formatDate(year, month, day) to 0.9f
            }
        }

        // Try labeled date first ("Date: ...")
        val labeledDatePattern = Regex("""(?i)date\s*[:\s]\s*(.+)""")
        for (line in text.lines()) {
            val labelMatch = labeledDatePattern.find(line)
            if (labelMatch != null) {
                val datePart = labelMatch.groupValues[1].trim()
                val parsed = parseDateFragment(datePart)
                if (parsed != null) return parsed to 0.85f
            }
        }

        // Unlabeled date patterns
        val parsed = parseDateFragment(text)
        if (parsed != null) return parsed to 0.6f

        return null to null
    }

    private fun parseDateFragment(text: String): String? {
        // US format
        usDatePattern.find(text)?.let { match ->
            val a = match.groupValues[1].toInt()
            val b = match.groupValues[2].toInt()
            val year = normalizeYear(match.groupValues[3].toInt())
            // US: MM/DD/YYYY
            if (isValidDate(year, a, b)) {
                return formatDate(year, a, b)
            }
        }

        // EU format
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

    private data class CurrencyMatch(val code: String, val confidence: Float)

    private val currencySymbols = mapOf(
        "$" to "USD",
        "\u20AC" to "EUR",   // Euro sign
        "\u00A3" to "GBP",   // Pound sign
        "\u00A5" to "JPY",   // Yen sign
        "CHF" to "CHF",
    )

    private val currencyCodePattern = Regex("""(?i)\b(USD|EUR|GBP|JPY|CHF|CAD|AUD|NZD|SEK|NOK|DKK|CNY|HKD|SGD|KRW|INR|BRL|MXN|ZAR|THB)\b""")

    internal fun extractCurrency(text: String): Pair<String?, Float?> {
        // Explicit currency code -- highest confidence
        currencyCodePattern.find(text)?.let { match ->
            return match.groupValues[1].uppercase() to 0.9f
        }

        // Symbol detection
        for ((symbol, code) in currencySymbols) {
            if (text.contains(symbol)) {
                // $ is ambiguous -- could be USD, CAD, AUD, etc.
                val confidence = if (symbol == "$") 0.6f else 0.8f
                return code to confidence
            }
        }

        return null to null
    }

    // -- Vendor extraction --

    /** Words commonly found on receipts that are not vendor names. */
    private val vendorStopwords = setOf(
        "receipt", "invoice", "date", "total", "subtotal", "tax", "change",
        "cash", "credit", "debit", "visa", "mastercard", "amex", "thank",
        "thanks", "you", "welcome", "order", "sale", "register", "store",
        "tel", "phone", "fax", "www", "http", "email",
    )

    internal fun extractVendor(lines: List<String>): Pair<String?, Float?> {
        // Heuristic: the vendor name is typically in the first few non-empty lines,
        // often the first line or the largest text block near the top.
        val candidates = lines
            .take(5)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .filter { line ->
                // Filter out lines that are clearly addresses, phone numbers, dates, etc.
                !line.matches(Regex("""^\d[\d\s\-().]+$""")) && // phone numbers
                !line.matches(Regex("""^\d+\s+\w+\s+(St|Ave|Rd|Blvd|Dr|Ln|Ct|Way|Pl|Pkwy|Hwy)\.?.*""", RegexOption.IGNORE_CASE)) && // addresses
                !usDatePattern.containsMatchIn(line) &&
                !isoDatePattern.containsMatchIn(line) &&
                !line.matches(Regex("""^[#\-=*]+$""")) // decorative lines
            }

        if (candidates.isEmpty()) return null to null

        // Pick the first candidate that has at least one word not in stopwords
        for (candidate in candidates) {
            val words = candidate.lowercase().split(Regex("""\s+"""))
            val meaningful = words.any { it !in vendorStopwords && it.length > 1 }
            if (meaningful) {
                // Confidence: higher if it's the first line
                val confidence = if (candidate == candidates.first()) 0.7f else 0.5f
                return candidate to confidence
            }
        }

        return candidates.first() to 0.3f
    }

    // -- Utilities --

    private fun normalizeAmount(raw: String): String {
        // Replace comma decimal separator with dot
        return raw.replace(',', '.')
    }

    private fun normalizeYear(year: Int): Int {
        return if (year < 100) 2000 + year else year
    }

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

    private fun formatDate(year: Int, month: Int, day: Int): String {
        return "$year-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"
    }
}
