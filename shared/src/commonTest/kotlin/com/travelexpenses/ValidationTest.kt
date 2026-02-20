package com.travelexpenses

import com.travelexpenses.validation.*
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExpenseValidatorTest {

    @Test
    fun validExpensePassesValidation() {
        val expense = TestHelpers.makeExpense(amount = "25.50", currency = "USD")
        val errors = ExpenseValidator.validate(expense)
        assertTrue(errors.isEmpty(), "Valid expense should have no errors, got: $errors")
    }

    @Test
    fun zeroAmountFails() {
        val expense = TestHelpers.makeExpense(amount = "0.00")
        val errors = ExpenseValidator.validate(expense)
        assertTrue(errors.contains(ValidationError.NON_POSITIVE_AMOUNT))
    }

    @Test
    fun negativeAmountFails() {
        val expense = TestHelpers.makeExpense(amount = "-10.00")
        val errors = ExpenseValidator.validate(expense)
        assertTrue(errors.contains(ValidationError.NON_POSITIVE_AMOUNT))
    }

    @Test
    fun nonNumericAmountFails() {
        val expense = TestHelpers.makeExpense(amount = "abc")
        val errors = ExpenseValidator.validate(expense)
        assertTrue(errors.contains(ValidationError.INVALID_AMOUNT))
    }

    @Test
    fun invalidCurrencyCodeFails() {
        val expense = TestHelpers.makeExpense(currency = "us")
        val errors = ExpenseValidator.validate(expense)
        assertTrue(errors.contains(ValidationError.INVALID_CURRENCY))
    }

    @Test
    fun lowercaseCurrencyFails() {
        val expense = TestHelpers.makeExpense(currency = "usd")
        val errors = ExpenseValidator.validate(expense)
        assertTrue(errors.contains(ValidationError.INVALID_CURRENCY))
    }

    @Test
    fun tooLongCurrencyFails() {
        val expense = TestHelpers.makeExpense(currency = "USDD")
        val errors = ExpenseValidator.validate(expense)
        assertTrue(errors.contains(ValidationError.INVALID_CURRENCY))
    }

    @Test
    fun blankCategoryFails() {
        val expense = TestHelpers.makeExpense().copy(categoryId = "")
        val errors = ExpenseValidator.validate(expense)
        assertTrue(errors.contains(ValidationError.MISSING_CATEGORY))
    }

    @Test
    fun blankTripIdFails() {
        val expense = TestHelpers.makeExpense().copy(tripId = "")
        val errors = ExpenseValidator.validate(expense)
        assertTrue(errors.contains(ValidationError.MISSING_TRIP))
    }

    @Test
    fun negativeTaxAmountFails() {
        val expense = TestHelpers.makeExpense().copy(taxAmount = "-1.00")
        val errors = ExpenseValidator.validate(expense)
        assertTrue(errors.contains(ValidationError.NEGATIVE_TAX_AMOUNT))
    }

    @Test
    fun invalidTaxAmountFails() {
        val expense = TestHelpers.makeExpense().copy(taxAmount = "not-a-number")
        val errors = ExpenseValidator.validate(expense)
        assertTrue(errors.contains(ValidationError.INVALID_TAX_AMOUNT))
    }

    @Test
    fun zeroTaxAmountIsValid() {
        val expense = TestHelpers.makeExpense().copy(taxAmount = "0.00")
        val errors = ExpenseValidator.validate(expense)
        assertFalse(errors.contains(ValidationError.NEGATIVE_TAX_AMOUNT))
        assertFalse(errors.contains(ValidationError.INVALID_TAX_AMOUNT))
    }

    @Test
    fun ocrConfidenceOutOfRangeFails() {
        val expense = TestHelpers.makeExpense().copy(ocrConfidence = 1.5f)
        val errors = ExpenseValidator.validate(expense)
        assertTrue(errors.contains(ValidationError.INVALID_OCR_CONFIDENCE))
    }

    @Test
    fun ocrConfidenceNegativeFails() {
        val expense = TestHelpers.makeExpense().copy(ocrConfidence = -0.1f)
        val errors = ExpenseValidator.validate(expense)
        assertTrue(errors.contains(ValidationError.INVALID_OCR_CONFIDENCE))
    }

    @Test
    fun multipleErrorsReported() {
        val expense = TestHelpers.makeExpense(amount = "abc", currency = "xx").copy(categoryId = "")
        val errors = ExpenseValidator.validate(expense)
        assertTrue(errors.size >= 3, "Should report multiple errors, got: $errors")
    }

    @Test
    fun isValidReturnsTrueForValidExpense() {
        val expense = TestHelpers.makeExpense()
        assertTrue(ExpenseValidator.isValid(expense))
    }

    @Test
    fun isValidReturnsFalseForInvalidExpense() {
        val expense = TestHelpers.makeExpense(amount = "0")
        assertFalse(ExpenseValidator.isValid(expense))
    }
}

class FieldValidatorTest {

    @Test
    fun validAmount() {
        assertTrue(FieldValidator.isValidAmount("25.50"))
        assertTrue(FieldValidator.isValidAmount("0.01"))
        assertTrue(FieldValidator.isValidAmount("1000"))
    }

    @Test
    fun invalidAmount() {
        assertFalse(FieldValidator.isValidAmount("0"))
        assertFalse(FieldValidator.isValidAmount("-5"))
        assertFalse(FieldValidator.isValidAmount("abc"))
        assertFalse(FieldValidator.isValidAmount(""))
    }

    @Test
    fun validCurrencyCode() {
        assertTrue(FieldValidator.isValidCurrencyCode("USD"))
        assertTrue(FieldValidator.isValidCurrencyCode("EUR"))
        assertTrue(FieldValidator.isValidCurrencyCode("JPY"))
    }

    @Test
    fun invalidCurrencyCode() {
        assertFalse(FieldValidator.isValidCurrencyCode("usd"))
        assertFalse(FieldValidator.isValidCurrencyCode("US"))
        assertFalse(FieldValidator.isValidCurrencyCode("USDD"))
        assertFalse(FieldValidator.isValidCurrencyCode("123"))
    }

    @Test
    fun validTaxAmount() {
        assertTrue(FieldValidator.isValidTaxAmount("5.00"))
        assertTrue(FieldValidator.isValidTaxAmount("0"))
        assertTrue(FieldValidator.isValidTaxAmount(""))
    }

    @Test
    fun invalidTaxAmount() {
        assertFalse(FieldValidator.isValidTaxAmount("-1"))
        assertFalse(FieldValidator.isValidTaxAmount("abc"))
    }
}

class DuplicateDetectorTest {

    private val detector = DuplicateDetector(dayThreshold = 1)

    @Test
    fun exactDuplicate() {
        val a = TestHelpers.makeExpense(id = "e1", amount = "25.50", vendor = "Starbucks", date = LocalDate(2024, 6, 15))
        val b = TestHelpers.makeExpense(id = "e2", amount = "25.50", vendor = "Starbucks", date = LocalDate(2024, 6, 15))
        assertTrue(detector.isDuplicate(a, b))
    }

    @Test
    fun sameAmountDifferentVendorNotDuplicate() {
        val a = TestHelpers.makeExpense(id = "e1", amount = "25.50", vendor = "Starbucks", date = LocalDate(2024, 6, 15))
        val b = TestHelpers.makeExpense(id = "e2", amount = "25.50", vendor = "Peet's", date = LocalDate(2024, 6, 15))
        assertFalse(detector.isDuplicate(a, b))
    }

    @Test
    fun sameVendorDifferentAmountNotDuplicate() {
        val a = TestHelpers.makeExpense(id = "e1", amount = "25.50", vendor = "Starbucks", date = LocalDate(2024, 6, 15))
        val b = TestHelpers.makeExpense(id = "e2", amount = "30.00", vendor = "Starbucks", date = LocalDate(2024, 6, 15))
        assertFalse(detector.isDuplicate(a, b))
    }

    @Test
    fun dateWithinThresholdIsDuplicate() {
        val a = TestHelpers.makeExpense(id = "e1", amount = "25.50", vendor = "Starbucks", date = LocalDate(2024, 6, 15))
        val b = TestHelpers.makeExpense(id = "e2", amount = "25.50", vendor = "Starbucks", date = LocalDate(2024, 6, 16))
        assertTrue(detector.isDuplicate(a, b))
    }

    @Test
    fun dateOutsideThresholdNotDuplicate() {
        val a = TestHelpers.makeExpense(id = "e1", amount = "25.50", vendor = "Starbucks", date = LocalDate(2024, 6, 15))
        val b = TestHelpers.makeExpense(id = "e2", amount = "25.50", vendor = "Starbucks", date = LocalDate(2024, 6, 20))
        assertFalse(detector.isDuplicate(a, b))
    }

    @Test
    fun caseInsensitiveVendorMatch() {
        val a = TestHelpers.makeExpense(id = "e1", amount = "25.50", vendor = "STARBUCKS", date = LocalDate(2024, 6, 15))
        val b = TestHelpers.makeExpense(id = "e2", amount = "25.50", vendor = "starbucks", date = LocalDate(2024, 6, 15))
        assertTrue(detector.isDuplicate(a, b))
    }

    @Test
    fun nullVendorNotDuplicate() {
        val a = TestHelpers.makeExpense(id = "e1", amount = "25.50", vendor = null, date = LocalDate(2024, 6, 15))
        val b = TestHelpers.makeExpense(id = "e2", amount = "25.50", vendor = null, date = LocalDate(2024, 6, 15))
        assertFalse(detector.isDuplicate(a, b))
    }

    @Test
    fun differentCurrencyNotDuplicate() {
        val a = TestHelpers.makeExpense(id = "e1", amount = "25.50", currency = "USD", vendor = "Starbucks", date = LocalDate(2024, 6, 15))
        val b = TestHelpers.makeExpense(id = "e2", amount = "25.50", currency = "EUR", vendor = "Starbucks", date = LocalDate(2024, 6, 15))
        assertFalse(detector.isDuplicate(a, b))
    }

    @Test
    fun findDuplicatesExcludesSelf() {
        val a = TestHelpers.makeExpense(id = "e1", amount = "25.50", vendor = "Starbucks", date = LocalDate(2024, 6, 15))
        val existing = listOf(a)
        val duplicates = detector.findDuplicates(a, existing)
        assertTrue(duplicates.isEmpty())
    }

    @Test
    fun findDuplicatesReturnMatches() {
        val candidate = TestHelpers.makeExpense(id = "e-new", amount = "25.50", vendor = "Starbucks", date = LocalDate(2024, 6, 15))
        val existing = listOf(
            TestHelpers.makeExpense(id = "e1", amount = "25.50", vendor = "Starbucks", date = LocalDate(2024, 6, 15)),
            TestHelpers.makeExpense(id = "e2", amount = "30.00", vendor = "Starbucks", date = LocalDate(2024, 6, 15)),
            TestHelpers.makeExpense(id = "e3", amount = "25.50", vendor = "Peet's", date = LocalDate(2024, 6, 15)),
        )
        val duplicates = detector.findDuplicates(candidate, existing)
        assertEquals(1, duplicates.size)
        assertEquals("e1", duplicates[0].id)
    }
}

class DefaultCategoriesTest {

    @Test
    fun nineDefaultCategories() {
        assertEquals(9, com.travelexpenses.validation.DefaultCategories.all.size)
    }

    @Test
    fun allAreMarkedAsDefault() {
        assertTrue(com.travelexpenses.validation.DefaultCategories.all.all { it.isDefault })
    }

    @Test
    fun containsExpectedCategories() {
        val names = com.travelexpenses.validation.DefaultCategories.all.map { it.name }
        assertTrue("Transport" in names)
        assertTrue("Accommodation" in names)
        assertTrue("Food & Drink" in names)
        assertTrue("Activities" in names)
        assertTrue("Shopping" in names)
        assertTrue("Communication" in names)
        assertTrue("Health" in names)
        assertTrue("Fees & Tips" in names)
        assertTrue("Other" in names)
    }

    @Test
    fun getByIdWorks() {
        val category = com.travelexpenses.validation.DefaultCategories.getById("cat-food-drink")
        kotlin.test.assertNotNull(category)
        kotlin.test.assertEquals("Food & Drink", category.name)
    }

    @Test
    fun idsSetContainsAllDefaults() {
        kotlin.test.assertEquals(9, com.travelexpenses.validation.DefaultCategories.ids.size)
    }
}
