package com.travelexpenses

import com.travelexpenses.ocr.OcrParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OcrParserTest {

    // -- Total extraction --

    @Test
    fun parsesTotalFromLabeledLine() {
        val result = OcrParser.parse("Subtotal \$20.50\nTax \$1.74\nTotal \$22.24")
        assertEquals("22.24", result.total)
        assertNotNull(result.confidence.total)
        assertTrue(result.confidence.total!! >= 0.8f)
    }

    @Test
    fun parsesTotalAmountDue() {
        val result = OcrParser.parse("Amount Due: \$55.00")
        assertEquals("55.00", result.total)
    }

    @Test
    fun parsesGrandTotal() {
        val result = OcrParser.parse("Subtotal: \$100.00\nGrand Total: \$110.00")
        assertEquals("110.00", result.total)
    }

    @Test
    fun parsesTotalWithEuroSymbol() {
        val result = OcrParser.parse("Total \u20AC7.50")
        assertEquals("7.50", result.total)
    }

    @Test
    fun parsesTotalWithCommaDecimal() {
        val result = OcrParser.parse("Total EUR 7,50")
        assertEquals("7.50", result.total)
    }

    @Test
    fun fallsBackToLargestAmountNearBottom() {
        val text = "Item A \$3.00\nItem B \$5.00\nItem C \$12.00\n\$20.00"
        val result = OcrParser.parse(text)
        assertEquals("20.00", result.total)
        assertNotNull(result.confidence.total)
        assertTrue(result.confidence.total!! <= 0.6f, "Fallback should have lower confidence")
    }

    @Test
    fun returnsNullTotalForEmptyInput() {
        val result = OcrParser.parse("")
        assertNull(result.total)
        assertNull(result.confidence.total)
    }

    // -- Tax extraction --

    @Test
    fun parsesTaxAmount() {
        val result = OcrParser.parse("Tax \$1.74\nTotal \$22.24")
        assertEquals("1.74", result.tax)
        assertNotNull(result.confidence.tax)
    }

    @Test
    fun parsesVatAmount() {
        val result = OcrParser.parse("VAT \u00A32.74\nTotal \u00A316.44")
        assertEquals("2.74", result.tax)
    }

    @Test
    fun parsesGstAmount() {
        val result = OcrParser.parse("Subtotal \$100.00\nGST \$10.00\nTotal \$110.00")
        assertEquals("10.00", result.tax)
    }

    @Test
    fun returnsNullTaxWhenNotPresent() {
        val result = OcrParser.parse("Total \$22.24")
        assertNull(result.tax)
        assertNull(result.confidence.tax)
    }

    // -- Date extraction --

    @Test
    fun parsesIsoDate() {
        val result = OcrParser.parse("2026-01-20\nTotal \$5.00")
        assertEquals("2026-01-20", result.date)
        assertNotNull(result.confidence.date)
        assertTrue(result.confidence.date!! >= 0.9f)
    }

    @Test
    fun parsesUsDateFormat() {
        val result = OcrParser.parse("Date: 03/15/2026\nTotal \$22.24")
        assertEquals("2026-03-15", result.date)
    }

    @Test
    fun parsesEuDateFormat() {
        val result = OcrParser.parse("15.03.2026\nTotal EUR 7.50")
        assertEquals("2026-03-15", result.date)
    }

    @Test
    fun parsesWrittenMonthFormat() {
        val result = OcrParser.parse("Date: 15 March 2026\nTotal \$10.00")
        assertEquals("2026-03-15", result.date)
    }

    @Test
    fun parsesAbbreviatedMonthFormat() {
        val result = OcrParser.parse("Jan 5, 2026\nTotal \$10.00")
        assertEquals("2026-01-05", result.date)
    }

    @Test
    fun parsesTwoDigitYear() {
        val result = OcrParser.parse("Date: 03/15/26\nTotal \$5.00")
        assertEquals("2026-03-15", result.date)
    }

    @Test
    fun returnsNullDateWhenNotPresent() {
        val result = OcrParser.parse("QUICK MART\nTotal \$5.99")
        assertNull(result.date)
    }

    @Test
    fun rejectsInvalidDate() {
        val result = OcrParser.parse("Date: 13/32/2026\nTotal \$5.00")
        // Month 13 or day 32 should not produce a valid date
        assertNull(result.date)
    }

    // -- Currency extraction --

    @Test
    fun extractsUsdFromDollarSign() {
        val result = OcrParser.parse("Total \$22.24")
        assertEquals("USD", result.currency)
    }

    @Test
    fun extractsEurFromSymbol() {
        val result = OcrParser.parse("Total \u20AC7.50")
        assertEquals("EUR", result.currency)
    }

    @Test
    fun extractsGbpFromSymbol() {
        val result = OcrParser.parse("Total \u00A316.44")
        assertEquals("GBP", result.currency)
    }

    @Test
    fun extractsCurrencyFromExplicitCode() {
        val result = OcrParser.parse("Total EUR 7.50")
        assertEquals("EUR", result.currency)
        assertNotNull(result.confidence.currency)
        assertTrue(result.confidence.currency!! >= 0.8f, "Explicit code should have high confidence")
    }

    @Test
    fun returnsNullCurrencyWhenNotPresent() {
        val result = OcrParser.parse("Total 22.24")
        assertNull(result.currency)
    }

    // -- Vendor extraction --

    @Test
    fun extractsVendorFromFirstLine() {
        val result = OcrParser.parse("DOWNTOWN GRILL\n123 Main St\nTotal \$22.24")
        assertEquals("DOWNTOWN GRILL", result.vendor)
        assertNotNull(result.confidence.vendor)
    }

    @Test
    fun skipsAddressLinesForVendor() {
        val result = OcrParser.parse("THE KING'S HEAD\n12 High Street\nLondon\nTotal \$10.00")
        assertEquals("THE KING'S HEAD", result.vendor)
    }

    @Test
    fun returnsNullVendorForBlankInput() {
        val result = OcrParser.parse("")
        assertNull(result.vendor)
    }

    @Test
    fun extractsVendorIgnoringPhoneNumbers() {
        val result = OcrParser.parse("555-1234\nBOB'S BURGERS\nTotal \$10.00")
        assertEquals("BOB'S BURGERS", result.vendor)
    }

    // -- Test vector: US restaurant --

    @Test
    fun testVectorUsRestaurant() {
        val text = "DOWNTOWN GRILL\n123 Main St\nDate: 03/15/2026\n\nBurger  \$12.50\nBeer     \$8.00\nSubtotal \$20.50\nTax       \$1.74\nTotal    \$22.24\nThank you!"
        val result = OcrParser.parse(text)
        assertEquals("DOWNTOWN GRILL", result.vendor)
        assertEquals("2026-03-15", result.date)
        assertEquals("USD", result.currency)
        assertEquals("22.24", result.total)
        assertEquals("1.74", result.tax)
    }

    // -- Test vector: EU cafe --

    @Test
    fun testVectorEuCafe() {
        val text = "CAFE EUROPA\nHauptstr. 42\nBerlin\n15.03.2026\n\nKaffee       2,50\nKuchen       3,80\nZwischensumme 6,30\nMwSt          1,20\nGesamt EUR    7,50\nDanke!"
        val result = OcrParser.parse(text)
        assertEquals("CAFE EUROPA", result.vendor)
        assertEquals("2026-03-15", result.date)
        assertEquals("EUR", result.currency)
        // "Gesamt" is German for total but not in our English patterns -- this tests fallback
        assertNotNull(result.total)
    }

    // -- Test vector: UK pub --

    @Test
    fun testVectorUkPub() {
        val text = "THE KING'S HEAD\n12 High Street\nLondon\nDate: 15 March 2026\n\nFish & Chips   \u00A38.50\nPint Ale       \u00A35.20\nSubtotal      \u00A313.70\nVAT            \u00A32.74\nTotal         \u00A316.44"
        val result = OcrParser.parse(text)
        assertEquals("THE KING'S HEAD", result.vendor)
        assertEquals("2026-03-15", result.date)
        assertEquals("GBP", result.currency)
        assertEquals("16.44", result.total)
        assertEquals("2.74", result.tax)
    }

    // -- Test vector: ISO date --

    @Test
    fun testVectorIsoDate() {
        val text = "AIRPORT DUTY FREE\nTerminal 2\n2026-01-20\n\nChocolate    \$15.99\nPerfume      \$45.00\nTotal        \$60.99"
        val result = OcrParser.parse(text)
        assertEquals("AIRPORT DUTY FREE", result.vendor)
        assertEquals("2026-01-20", result.date)
        assertEquals("USD", result.currency)
        assertEquals("60.99", result.total)
        assertNull(result.tax)
    }

    // -- Test vector: minimal --

    @Test
    fun testVectorMinimal() {
        val text = "QUICK MART\nTotal \$5.99"
        val result = OcrParser.parse(text)
        assertEquals("QUICK MART", result.vendor)
        assertNull(result.date)
        assertEquals("USD", result.currency)
        assertEquals("5.99", result.total)
        assertNull(result.tax)
    }

    // -- Edge cases --

    @Test
    fun handlesBlankInput() {
        val result = OcrParser.parse("   \n  \n  ")
        assertNull(result.vendor)
        assertNull(result.date)
        assertNull(result.currency)
        assertNull(result.total)
        assertNull(result.tax)
    }

    @Test
    fun handlesMultipleTotalLines() {
        // The last "Total" (bottom of receipt) should win
        val text = "Total Items: 3\nSubtotal \$30.00\nTax \$2.40\nTotal \$32.40"
        val result = OcrParser.parse(text)
        assertEquals("32.40", result.total)
    }

    @Test
    fun parsesBalanceDue() {
        val result = OcrParser.parse("Subtotal \$50.00\nBalance Due: \$55.00")
        assertEquals("55.00", result.total)
    }
}
