package com.travelexpenses

import com.travelexpenses.ocr.OcrParser
import com.travelexpenses.ocr.OcrResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OcrParserTest {

    private val parser = OcrParser()

    // -- Total extraction tests -------------------------------------------

    @Test
    fun totalExtractedFromLabeledLine() {
        val text = """
            COFFEE SHOP
            Latte         4.50
            Muffin        3.25
            Subtotal      7.75
            Tax           0.62
            Total         8.37
        """.trimIndent()
        val result = parser.parse(text)
        assertEquals("8.37", result.total)
        assertTrue(result.totalConfidence >= 0.8f)
    }

    @Test
    fun totalExtractedFromAmountDue() {
        val text = """
            RESTAURANT
            Food items    25.00
            Amount Due    $30.50
        """.trimIndent()
        val result = parser.parse(text)
        assertEquals("30.50", result.total)
        assertTrue(result.totalConfidence >= 0.8f)
    }

    @Test
    fun totalExtractedFromGrandTotal() {
        val text = """
            STORE
            Item 1        10.00
            Item 2        15.00
            Subtotal      25.00
            Tax            2.00
            Grand Total   27.00
        """.trimIndent()
        val result = parser.parse(text)
        assertEquals("27.00", result.total)
        assertTrue(result.totalConfidence >= 0.8f)
    }

    @Test
    fun totalFallbackToLargestAmountInBottomThird() {
        val text = """
            SHOP
            Apple    1.50
            Bread    3.00
            Milk     2.50
            ---
            7.00
        """.trimIndent()
        val result = parser.parse(text)
        assertEquals("7.00", result.total)
        assertTrue(result.totalConfidence > 0f)
    }

    @Test
    fun totalWithCommaThousandsSeparator() {
        val text = """
            HOTEL
            Room charge
            Total  1,234.56
        """.trimIndent()
        val result = parser.parse(text)
        assertEquals("1234.56", result.total)
    }

    // -- Tax extraction tests ---------------------------------------------

    @Test
    fun taxExtractedFromLine() {
        val text = """
            STORE
            Subtotal   10.00
            Tax         0.80
            Total      10.80
        """.trimIndent()
        val result = parser.parse(text)
        assertEquals("0.80", result.tax)
        assertTrue(result.taxConfidence >= 0.7f)
    }

    @Test
    fun vatExtracted() {
        val text = """
            RESTAURANT
            Food       20.00
            VAT         3.00
            Total      23.00
        """.trimIndent()
        val result = parser.parse(text)
        assertEquals("3.00", result.tax)
        assertTrue(result.taxConfidence >= 0.7f)
    }

    @Test
    fun gstExtracted() {
        val text = """
            CAFE
            Coffee      5.00
            GST         0.25
            Total       5.25
        """.trimIndent()
        val result = parser.parse(text)
        assertEquals("0.25", result.tax)
    }

    // -- Date extraction tests --------------------------------------------

    @Test
    fun isoDateExtracted() {
        val text = """
            STORE
            Date: 2024-03-15
            Total: 25.00
        """.trimIndent()
        val result = parser.parse(text)
        assertEquals("2024-03-15", result.date)
        assertTrue(result.dateConfidence >= 0.9f)
    }

    @Test
    fun usDateExtracted() {
        val text = """
            STORE
            03/15/2024
            Total: 25.00
        """.trimIndent()
        val result = parser.parse(text)
        assertEquals("2024-03-15", result.date)
        assertTrue(result.dateConfidence >= 0.5f)
    }

    @Test
    fun euDateWithDotExtracted() {
        val text = """
            LADEN
            15.03.2024
            Gesamt: 25,00
        """.trimIndent()
        val result = parser.parse(text)
        assertEquals("2024-03-15", result.date)
        assertTrue(result.dateConfidence >= 0.5f)
    }

    @Test
    fun writtenDateMonthFirst() {
        val text = """
            SHOP
            January 15, 2024
            Total: 50.00
        """.trimIndent()
        val result = parser.parse(text)
        assertEquals("2024-01-15", result.date)
        assertTrue(result.dateConfidence >= 0.8f)
    }

    @Test
    fun writtenDateDayFirst() {
        val text = """
            SHOP
            15 Mar 2024
            Total: 50.00
        """.trimIndent()
        val result = parser.parse(text)
        assertEquals("2024-03-15", result.date)
        assertTrue(result.dateConfidence >= 0.8f)
    }

    @Test
    fun abbreviatedMonthDate() {
        val text = """
            CAFE
            Sep 3, 2025
            Total: 12.50
        """.trimIndent()
        val result = parser.parse(text)
        assertEquals("2025-09-03", result.date)
    }

    // -- Vendor extraction tests ------------------------------------------

    @Test
    fun vendorFromFirstLine() {
        val text = """
            STARBUCKS COFFEE
            123 Main Street
            Date: 2024-01-15
            Total: 5.75
        """.trimIndent()
        val result = parser.parse(text)
        assertEquals("STARBUCKS COFFEE", result.vendor)
        assertTrue(result.vendorConfidence > 0f)
    }

    @Test
    fun vendorSkipsReceiptHeader() {
        val text = """
            Receipt #12345
            WHOLE FOODS MARKET
            456 Oak Ave
            Total: 85.23
        """.trimIndent()
        val result = parser.parse(text)
        assertEquals("WHOLE FOODS MARKET", result.vendor)
    }

    @Test
    fun vendorSkipsPureNumbers() {
        val text = """
            12345
            THE CORNER BAKERY
            Total: 15.00
        """.trimIndent()
        val result = parser.parse(text)
        assertEquals("THE CORNER BAKERY", result.vendor)
    }

    // -- Currency extraction tests ----------------------------------------

    @Test
    fun currencyFromExplicitCode() {
        val text = """
            SHOP
            Total: EUR 25.00
        """.trimIndent()
        val result = parser.parse(text)
        assertEquals("EUR", result.currency)
        assertTrue(result.currencyConfidence >= 0.8f)
    }

    @Test
    fun currencyFromEuroSymbol() {
        val text = """
            RESTAURANT
            Total: €25.00
        """.trimIndent()
        val result = parser.parse(text)
        assertEquals("EUR", result.currency)
        assertTrue(result.currencyConfidence >= 0.8f)
    }

    @Test
    fun currencyFromPoundSymbol() {
        val text = """
            PUB
            Total: £18.50
        """.trimIndent()
        val result = parser.parse(text)
        assertEquals("GBP", result.currency)
    }

    @Test
    fun currencyFromDollarSign() {
        val text = """
            STORE
            Total: $25.00
        """.trimIndent()
        val result = parser.parse(text)
        assertEquals("USD", result.currency)
        // Lower confidence since $ is ambiguous
        assertTrue(result.currencyConfidence <= 0.7f)
    }

    @Test
    fun currencyFromTripLocale() {
        val parserWithLocale = OcrParser(tripCurrency = "JPY")
        val text = """
            SHOP
            Total: 2500
        """.trimIndent()
        val result = parserWithLocale.parse(text)
        assertEquals("JPY", result.currency)
        assertTrue(result.currencyConfidence <= 0.5f)
    }

    // -- Edge cases -------------------------------------------------------

    @Test
    fun emptyTextReturnsEmptyResult() {
        val result = parser.parse("")
        assertNull(result.total)
        assertNull(result.tax)
        assertNull(result.date)
        assertNull(result.vendor)
        assertNull(result.currency)
    }

    @Test
    fun blankTextReturnsEmptyResult() {
        val result = parser.parse("   \n  \n  ")
        assertNull(result.total)
    }

    @Test
    fun fullReceiptParsing() {
        val text = """
            WHOLE FOODS MARKET
            123 Main St, Austin TX 78701
            Tel: (512) 555-0123

            Date: 2024-06-20

            Organic Apples    4.99
            Sourdough Bread   5.49
            Almond Milk       3.99

            Subtotal         14.47
            Sales Tax         1.19
            Total            15.66

            VISA ****1234
            Thank you for shopping!
        """.trimIndent()
        val result = parser.parse(text)

        assertEquals("WHOLE FOODS MARKET", result.vendor)
        assertEquals("2024-06-20", result.date)
        assertEquals("15.66", result.total)
        assertEquals("1.19", result.tax)
        assertTrue(result.totalConfidence >= 0.8f)
        assertTrue(result.dateConfidence >= 0.9f)
    }

    @Test
    fun europeanReceipt() {
        val text = """
            BOULANGERIE PAUL
            15 Rue de Rivoli, Paris

            15.03.2024

            Croissant         1,50
            Cafe              2,80

            TVA               0,65
            Total EUR         5,45
        """.trimIndent()
        val result = parser.parse(text)

        assertEquals("BOULANGERIE PAUL", result.vendor)
        assertEquals("EUR", result.currency)
        assertNotNull(result.date)
        assertNotNull(result.total)
    }

    @Test
    fun rawTextPreserved() {
        val text = "Simple receipt\nTotal: 10.00"
        val result = parser.parse(text)
        assertEquals(text, result.rawText)
    }
}
