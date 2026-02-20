package com.travelexpenses

import com.travelexpenses.db.TravelExpensesDb
import com.travelexpenses.event.EventReplayEngine
import com.travelexpenses.model.*
import com.travelexpenses.repository.*
import com.travelexpenses.validation.*
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlin.test.*

class ExpenseValidatorTest {

    @Test
    fun validExpensePassesValidation() {
        val expense = TestHelpers.makeExpense()
        assertTrue(ExpenseValidator.isValid(expense))
        assertTrue(ExpenseValidator.validate(expense).isEmpty())
    }

    @Test
    fun invalidAmountFails() {
        val expense = TestHelpers.makeExpense().copy(amount = "not-a-number")
        val errors = ExpenseValidator.validate(expense)
        assertTrue(errors.contains(ValidationError.INVALID_AMOUNT))
    }

    @Test
    fun zeroAmountFails() {
        val expense = TestHelpers.makeExpense().copy(amount = "0")
        val errors = ExpenseValidator.validate(expense)
        assertTrue(errors.contains(ValidationError.AMOUNT_NOT_POSITIVE))
    }

    @Test
    fun negativeAmountFails() {
        val expense = TestHelpers.makeExpense().copy(amount = "-5.00")
        val errors = ExpenseValidator.validate(expense)
        assertTrue(errors.contains(ValidationError.AMOUNT_NOT_POSITIVE))
    }

    @Test
    fun blankCurrencyFails() {
        val expense = TestHelpers.makeExpense().copy(currency = "")
        val errors = ExpenseValidator.validate(expense)
        assertTrue(errors.contains(ValidationError.MISSING_CURRENCY))
    }

    @Test
    fun invalidCurrencyCodeFails() {
        val expense = TestHelpers.makeExpense().copy(currency = "XYZ")
        val errors = ExpenseValidator.validate(expense)
        assertTrue(errors.contains(ValidationError.INVALID_CURRENCY_CODE))
    }

    @Test
    fun validCurrencyCodesPass() {
        for (code in listOf("USD", "EUR", "GBP", "JPY", "AUD", "CAD")) {
            val expense = TestHelpers.makeExpense().copy(currency = code)
            val errors = ExpenseValidator.validate(expense)
            assertFalse(errors.any {
                it == ValidationError.MISSING_CURRENCY || it == ValidationError.INVALID_CURRENCY_CODE
            }, "Currency $code should be valid")
        }
    }

    @Test
    fun blankTripIdFails() {
        val expense = TestHelpers.makeExpense().copy(tripId = "")
        val errors = ExpenseValidator.validate(expense)
        assertTrue(errors.contains(ValidationError.MISSING_TRIP_ID))
    }

    @Test
    fun blankCategoryIdFails() {
        val expense = TestHelpers.makeExpense().copy(categoryId = "")
        val errors = ExpenseValidator.validate(expense)
        assertTrue(errors.contains(ValidationError.MISSING_CATEGORY))
    }

    @Test
    fun invalidTaxAmountFails() {
        val expense = TestHelpers.makeExpense().copy(taxAmount = "not-a-number")
        val errors = ExpenseValidator.validate(expense)
        assertTrue(errors.contains(ValidationError.INVALID_TAX_AMOUNT))
    }

    @Test
    fun negativeTaxAmountFails() {
        val expense = TestHelpers.makeExpense().copy(taxAmount = "-1.00")
        val errors = ExpenseValidator.validate(expense)
        assertTrue(errors.contains(ValidationError.TAX_AMOUNT_NEGATIVE))
    }

    @Test
    fun validTaxAmountPasses() {
        val expense = TestHelpers.makeExpense().copy(taxAmount = "2.50")
        val errors = ExpenseValidator.validate(expense)
        assertFalse(errors.any {
            it == ValidationError.INVALID_TAX_AMOUNT || it == ValidationError.TAX_AMOUNT_NEGATIVE
        })
    }

    @Test
    fun nullTaxAmountPasses() {
        val expense = TestHelpers.makeExpense().copy(taxAmount = null)
        val errors = ExpenseValidator.validate(expense)
        assertFalse(errors.any {
            it == ValidationError.INVALID_TAX_AMOUNT || it == ValidationError.TAX_AMOUNT_NEGATIVE
        })
    }

    @Test
    fun multipleErrorsReturned() {
        val expense = TestHelpers.makeExpense().copy(
            amount = "bad",
            currency = "",
            tripId = "",
            categoryId = "",
        )
        val errors = ExpenseValidator.validate(expense)
        assertTrue(errors.size >= 4, "Should return multiple validation errors")
    }
}

class DuplicateDetectorTest {

    @Test
    fun noDuplicatesInDistinctExpenses() {
        val expenses = listOf(
            TestHelpers.makeExpense(id = "exp-1", amount = "10.00", vendor = "A"),
            TestHelpers.makeExpense(id = "exp-2", amount = "20.00", vendor = "B"),
            TestHelpers.makeExpense(id = "exp-3", amount = "30.00", vendor = "C"),
        )
        val groups = DuplicateDetector.findDuplicates(expenses)
        assertTrue(groups.isEmpty())
    }

    @Test
    fun detectsDuplicatesBySameAmountVendorDate() {
        val date = LocalDate(2026, 1, 15)
        val expenses = listOf(
            TestHelpers.makeExpense(id = "exp-1", amount = "22.50", vendor = "Cafe X", date = date),
            TestHelpers.makeExpense(id = "exp-2", amount = "22.50", vendor = "Cafe X", date = date),
            TestHelpers.makeExpense(id = "exp-3", amount = "15.00", vendor = "Cafe X", date = date),
        )
        val groups = DuplicateDetector.findDuplicates(expenses)
        assertEquals(1, groups.size)
        assertEquals(2, groups[0].expenses.size)
        assertEquals("22.50", groups[0].amount)
    }

    @Test
    fun caseInsensitiveVendorComparison() {
        val date = LocalDate(2026, 1, 15)
        val expenses = listOf(
            TestHelpers.makeExpense(id = "exp-1", amount = "10.00", vendor = "CAFE EUROPA", date = date),
            TestHelpers.makeExpense(id = "exp-2", amount = "10.00", vendor = "cafe europa", date = date),
        )
        val groups = DuplicateDetector.findDuplicates(expenses)
        assertEquals(1, groups.size)
    }

    @Test
    fun trimsVendorWhitespace() {
        val date = LocalDate(2026, 1, 15)
        val expenses = listOf(
            TestHelpers.makeExpense(id = "exp-1", amount = "10.00", vendor = " Cafe ", date = date),
            TestHelpers.makeExpense(id = "exp-2", amount = "10.00", vendor = "Cafe", date = date),
        )
        val groups = DuplicateDetector.findDuplicates(expenses)
        assertEquals(1, groups.size)
    }

    @Test
    fun differentDatesAreNotDuplicates() {
        val expenses = listOf(
            TestHelpers.makeExpense(id = "exp-1", amount = "10.00", vendor = "Cafe", date = LocalDate(2026, 1, 15)),
            TestHelpers.makeExpense(id = "exp-2", amount = "10.00", vendor = "Cafe", date = LocalDate(2026, 1, 16)),
        )
        val groups = DuplicateDetector.findDuplicates(expenses)
        assertTrue(groups.isEmpty())
    }

    @Test
    fun nullVendorsMatchEachOther() {
        val date = LocalDate(2026, 1, 15)
        val expenses = listOf(
            TestHelpers.makeExpense(id = "exp-1", amount = "10.00", vendor = null, date = date),
            TestHelpers.makeExpense(id = "exp-2", amount = "10.00", vendor = null, date = date),
        )
        val groups = DuplicateDetector.findDuplicates(expenses)
        assertEquals(1, groups.size)
    }

    @Test
    fun isDuplicateDetectsSingleDuplicate() {
        val date = LocalDate(2026, 1, 15)
        val existing = listOf(
            TestHelpers.makeExpense(id = "exp-1", amount = "22.50", vendor = "Cafe X", date = date),
        )
        val newExpense = TestHelpers.makeExpense(id = "exp-2", amount = "22.50", vendor = "Cafe X", date = date)

        assertTrue(DuplicateDetector.isDuplicate(newExpense, existing))
    }

    @Test
    fun isDuplicateDoesNotMatchSelf() {
        val date = LocalDate(2026, 1, 15)
        val expense = TestHelpers.makeExpense(id = "exp-1", amount = "22.50", vendor = "Cafe X", date = date)
        val existing = listOf(expense)

        assertFalse(DuplicateDetector.isDuplicate(expense, existing))
    }

    @Test
    fun isDuplicateReturnsFalseForUniqueExpense() {
        val existing = listOf(
            TestHelpers.makeExpense(id = "exp-1", amount = "10.00", vendor = "A"),
        )
        val newExpense = TestHelpers.makeExpense(id = "exp-2", amount = "20.00", vendor = "B")

        assertFalse(DuplicateDetector.isDuplicate(newExpense, existing))
    }
}

class DefaultCategoriesTest {

    @Test
    fun hasNineDefaultCategories() {
        assertEquals(9, DefaultCategories.ALL.size)
    }

    @Test
    fun allCategoriesAreDefault() {
        for (cat in DefaultCategories.ALL) {
            assertTrue(cat.isDefault, "${cat.name} should be marked as default")
        }
    }

    @Test
    fun allCategoriesHaveUniqueIds() {
        val ids = DefaultCategories.ALL.map { it.id }
        assertEquals(ids.toSet().size, ids.size, "Category IDs should be unique")
    }

    @Test
    fun containsExpectedCategories() {
        val names = DefaultCategories.ALL.map { it.name }.toSet()
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
    fun generatesCorrectNumberOfEvents() {
        val events = DefaultCategories.asCategoryCreatedEvents(
            deviceId = "device-1",
            sequenceNumberStart = 1,
            eventIdGenerator = { "evt-${kotlin.random.Random.nextInt(100000)}" },
            timestampProvider = { Clock.System.now() },
        )
        assertEquals(9, events.size)
        // Sequence numbers should be sequential
        events.forEachIndexed { index, event ->
            assertEquals(1L + index, event.sequenceNumber)
        }
    }

    @Test
    fun seedsDefaultCategoriesViaReplayEngine() = runTest {
        val driver = createInMemoryDriver()
        val db = TravelExpensesDb(driver)
        val testDispatcher = StandardTestDispatcher()
        val replayEngine = com.travelexpenses.event.EventReplayEngine(db, testDispatcher)
        val categoryRepo = SqlDelightCategoryRepository(db, testDispatcher)

        val events = DefaultCategories.asCategoryCreatedEvents(
            deviceId = "device-1",
            sequenceNumberStart = 1,
            eventIdGenerator = { "evt-${kotlin.random.Random.nextInt(100000)}" },
            timestampProvider = { Clock.System.now() },
        )

        for (event in events) {
            replayEngine.apply(event)
        }

        val categories = categoryRepo.getAllCategories()
        assertEquals(9, categories.size)
        assertTrue(categories.all { it.isDefault })
    }
}
