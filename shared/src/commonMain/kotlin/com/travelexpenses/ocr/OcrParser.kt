package com.travelexpenses.ocr

/**
 * Parses raw OCR text from a receipt into structured [OcrResult] fields.
 * Uses regex patterns and heuristics — no ML or platform dependencies.
 *
 * Field extraction strategy (from SPEC.md Section 4.2):
 * - Total: pattern match "total", "amount due", largest number near bottom
 * - Date: multi-format recognition (US, EU, ISO), locale-aware
 * - Vendor: top-of-receipt text heuristic
 * - Currency: symbol detection ($, EUR, etc.) or inferred from trip locale
 * - Tax: pattern match "tax", "VAT", "GST" + adjacent number
 */
class OcrParser(
    private val tripCurrency: String? = null,
) {

    fun parse(rawText: String): OcrResult {
        if (rawText.isBlank()) return OcrResult(rawText = rawText)

        val lines = rawText.lines().map { it.trim() }.filter { it.isNotEmpty() }

        val totalResult = extractTotal(lines)
        val taxResult = extractTax(lines)
        val dateResult = extractDate(lines)
        val vendorResult = extractVendor(lines)
        val currencyResult = extractCurrency(rawText, lines)

        return OcrResult(
            vendor = vendorResult.first,
            vendorConfidence = vendorResult.second,
            date = dateResult.first,
            dateConfidence = dateResult.second,
            currency = currencyResult.first,
            currencyConfidence = currencyResult.second,
            total = totalResult.first,
            totalConfidence = totalResult.second,
            tax = taxResult.first,
            taxConfidence = taxResult.second,
            rawText = rawText,
        )
    }

    // -- Total extraction ---------------------------------------------------

    private val totalLabelPattern = Regex(
        """(?i)\b(grand\s*total|total\s*due|amount\s*due|total\s*amount|balance\s*due|total)\b"""
    )

    private val moneyPattern = Regex(
        """[$€£¥]?\s*(\d{1,3}(?:[,.]?\d{3})*[.,]\d{2})\b"""
    )

    internal fun extractTotal(lines: List<String>): Pair<String?, Confidence> {
        // Strategy 1: Look for "total" label + adjacent number (highest confidence)
        for ((index, line) in lines.withIndex()) {
            if (totalLabelPattern.containsMatchIn(line)) {
                // Check same line first
                val amount = extractAmountFromLine(line)
                if (amount != null) return amount to 0.9f

                // Check next line
                if (index + 1 < lines.size) {
                    val nextAmount = extractAmountFromLine(lines[index + 1])
                    if (nextAmount != null) return nextAmount to 0.8f
                }
            }
        }

        // Strategy 2: Largest number in the bottom third of the receipt
        val bottomThird = lines.drop((lines.size * 2) / 3)
        val amounts = bottomThird.flatMap { extractAllAmounts(it) }
        if (amounts.isNotEmpty()) {
            val largest = amounts.maxByOrNull { it.toBigDecimalOrNull() ?: 0.toBigDecimal() }
            if (largest != null) return largest to 0.5f
        }

        return null to 0f
    }

    // -- Tax extraction -----------------------------------------------------

    private val taxLabelPattern = Regex(
        """(?i)\b(sales\s*tax|tax|vat|gst|hst)\b"""
    )

    internal fun extractTax(lines: List<String>): Pair<String?, Confidence> {
        for ((index, line) in lines.withIndex()) {
            if (taxLabelPattern.containsMatchIn(line)) {
                // Avoid matching "total" lines that also contain "tax"
                if (totalLabelPattern.containsMatchIn(line)) continue

                val amount = extractAmountFromLine(line)
                if (amount != null) return amount to 0.85f

                if (index + 1 < lines.size) {
                    val nextAmount = extractAmountFromLine(lines[index + 1])
                    if (nextAmount != null) return nextAmount to 0.7f
                }
            }
        }
        return null to 0f
    }

    // -- Date extraction ----------------------------------------------------

    // ISO: 2024-01-15
    private val isoDatePattern = Regex("""(20\d{2})-(0[1-9]|1[0-2])-(0[1-9]|[12]\d|3[01])""")
    // US: 01/15/2024 or 1/15/2024
    private val usDatePattern = Regex("""(0?[1-9]|1[0-2])/(0?[1-9]|[12]\d|3[01])/(20\d{2})""")
    // EU: 15/01/2024 or 15.01.2024 or 15-01-2024
    private val euDatePattern = Regex("""(0?[1-9]|[12]\d|3[01])[./\-](0?[1-9]|1[0-2])[./\-](20\d{2})""")
    // Written: Jan 15, 2024 or January 15, 2024 or 15 Jan 2024
    private val writtenDatePattern1 = Regex(
        """(?i)(Jan(?:uary)?|Feb(?:ruary)?|Mar(?:ch)?|Apr(?:il)?|May|Jun(?:e)?|Jul(?:y)?|Aug(?:ust)?|Sep(?:t(?:ember)?)?|Oct(?:ober)?|Nov(?:ember)?|Dec(?:ember)?)\s+(0?[1-9]|[12]\d|3[01]),?\s*(20\d{2})"""
    )
    private val writtenDatePattern2 = Regex(
        """(?i)(0?[1-9]|[12]\d|3[01])\s+(Jan(?:uary)?|Feb(?:ruary)?|Mar(?:ch)?|Apr(?:il)?|May|Jun(?:e)?|Jul(?:y)?|Aug(?:ust)?|Sep(?:t(?:ember)?)?|Oct(?:ober)?|Nov(?:ember)?|Dec(?:ember)?),?\s*(20\d{2})"""
    )

    internal fun extractDate(lines: List<String>): Pair<String?, Confidence> {
        val fullText = lines.joinToString(" ")

        // ISO format (highest confidence)
        isoDatePattern.find(fullText)?.let { match ->
            return match.value to 0.95f
        }

        // Written format: "Jan 15, 2024"
        writtenDatePattern1.find(fullText)?.let { match ->
            val month = parseMonthName(match.groupValues[1])
            val day = match.groupValues[2].padStart(2, '0')
            val year = match.groupValues[3]
            if (month != null) return "$year-$month-$day" to 0.9f
        }

        // Written format: "15 Jan 2024"
        writtenDatePattern2.find(fullText)?.let { match ->
            val day = match.groupValues[1].padStart(2, '0')
            val month = parseMonthName(match.groupValues[2])
            val year = match.groupValues[3]
            if (month != null) return "$year-$month-$day" to 0.9f
        }

        // US format: MM/DD/YYYY
        usDatePattern.find(fullText)?.let { match ->
            val month = match.groupValues[1].padStart(2, '0')
            val day = match.groupValues[2].padStart(2, '0')
            val year = match.groupValues[3]
            return "$year-$month-$day" to 0.7f
        }

        // EU format: DD/MM/YYYY (lower confidence since ambiguous with US)
        euDatePattern.find(fullText)?.let { match ->
            val day = match.groupValues[1].padStart(2, '0')
            val month = match.groupValues[2].padStart(2, '0')
            val year = match.groupValues[3]
            // Only treat as EU if day > 12 (unambiguous) or separator is . or -
            val separator = match.value.firstOrNull { it == '.' || it == '-' || it == '/' }
            val confidence = if (day.toInt() > 12 || separator == '.' || separator == '-') 0.75f else 0.5f
            return "$year-$month-$day" to confidence
        }

        return null to 0f
    }

    private fun parseMonthName(name: String): String? {
        val lower = name.lowercase()
        return when {
            lower.startsWith("jan") -> "01"
            lower.startsWith("feb") -> "02"
            lower.startsWith("mar") -> "03"
            lower.startsWith("apr") -> "04"
            lower.startsWith("may") -> "05"
            lower.startsWith("jun") -> "06"
            lower.startsWith("jul") -> "07"
            lower.startsWith("aug") -> "08"
            lower.startsWith("sep") -> "09"
            lower.startsWith("oct") -> "10"
            lower.startsWith("nov") -> "11"
            lower.startsWith("dec") -> "12"
            else -> null
        }
    }

    // -- Vendor extraction --------------------------------------------------

    private val vendorSkipPatterns = listOf(
        Regex("""(?i)^\d+$"""),                          // pure numbers
        Regex("""(?i)^(receipt|invoice|order|date|tel|phone|fax|www\.)"""),
        Regex("""(?i)^(tax|total|subtotal|change|cash|visa|mastercard|amex)"""),
        Regex("""^\W+$"""),                              // pure punctuation
        Regex("""(?i)^(welcome|thank)"""),
    )

    internal fun extractVendor(lines: List<String>): Pair<String?, Confidence> {
        // Heuristic: vendor name is typically in the first few non-trivial lines
        val candidates = lines.take(5)
        for (line in candidates) {
            val cleaned = line.trim()
            if (cleaned.length < 2) continue
            if (vendorSkipPatterns.any { it.containsMatchIn(cleaned) }) continue
            // Skip lines that look like addresses (contain digits + street suffixes)
            if (Regex("""(?i)\d+\s+(st|ave|blvd|rd|dr|ln|way|street|avenue)""").containsMatchIn(cleaned)) continue

            return cleaned to 0.7f
        }
        return null to 0f
    }

    // -- Currency extraction ------------------------------------------------

    private val currencySymbolMap = mapOf(
        "$" to "USD",
        "€" to "EUR",
        "£" to "GBP",
        "¥" to "JPY",
        "₹" to "INR",
        "₩" to "KRW",
        "₽" to "RUB",
        "₣" to "CHF",
        "R$" to "BRL",
        "A$" to "AUD",
        "C$" to "CAD",
        "HK$" to "HKD",
        "S$" to "SGD",
        "NZ$" to "NZD",
    )

    private val currencyCodePattern = Regex("""(?i)\b(USD|EUR|GBP|JPY|CHF|CAD|AUD|NZD|CNY|INR|BRL|KRW|SGD|HKD|SEK|NOK|DKK|MXN|ZAR|THB|PHP|MYR|IDR|TWD|CZK|PLN|HUF|RON|BGN|HRK|TRY|RUB|ILS)\b""")

    internal fun extractCurrency(rawText: String, lines: List<String>): Pair<String?, Confidence> {
        // Strategy 1: Explicit currency code in text
        currencyCodePattern.find(rawText)?.let { match ->
            return match.value.uppercase() to 0.9f
        }

        // Strategy 2: Currency symbol detection (check multi-char symbols first)
        val sortedSymbols = currencySymbolMap.entries.sortedByDescending { it.key.length }
        for ((symbol, code) in sortedSymbols) {
            if (rawText.contains(symbol)) {
                // For bare $, lower confidence since it's ambiguous (USD/CAD/AUD/etc.)
                val confidence = if (symbol == "$") 0.6f else 0.85f
                return code to confidence
            }
        }

        // Strategy 3: Infer from trip locale
        if (tripCurrency != null) {
            return tripCurrency to 0.3f
        }

        return null to 0f
    }

    // -- Utility methods ----------------------------------------------------

    private fun extractAmountFromLine(line: String): String? {
        val match = moneyPattern.find(line) ?: return null
        return normalizeAmount(match.groupValues[1])
    }

    private fun extractAllAmounts(line: String): List<String> {
        return moneyPattern.findAll(line).map { normalizeAmount(it.groupValues[1]) }.toList()
    }

    /**
     * Normalize amount string to standard decimal format.
     * Handles: "1,234.56" -> "1234.56", "1.234,56" -> "1234.56"
     */
    private fun normalizeAmount(raw: String): String {
        // Determine decimal separator: last occurrence of . or ,
        val lastDot = raw.lastIndexOf('.')
        val lastComma = raw.lastIndexOf(',')

        return when {
            // Both present: the later one is the decimal separator
            lastDot > lastComma -> raw.replace(",", "")
            lastComma > lastDot -> raw.replace(".", "").replace(",", ".")
            // Only one type present
            lastDot >= 0 -> raw // Already uses . as decimal
            lastComma >= 0 -> {
                // Single comma: if exactly 2 digits after, treat as decimal
                val afterComma = raw.substring(lastComma + 1)
                if (afterComma.length == 2) raw.replace(",", ".")
                else raw.replace(",", "") // thousands separator
            }
            else -> raw
        }
    }
}
