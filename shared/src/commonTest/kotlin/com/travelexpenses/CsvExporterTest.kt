package com.travelexpenses

import com.travelexpenses.export.CsvExporter
import com.travelexpenses.export.escapeCsv
import com.travelexpenses.model.*
import com.travelexpenses.repository.CategoryRepository
import com.travelexpenses.repository.ExpenseRepository
import com.travelexpenses.repository.TripRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertContains

/**
 * In-memory repositories for testing CsvExporter without a database.
 */
private class FakeExpenseRepository(
    private val expenses: List<Expense> = emptyList(),
    private val tags: Map<ExpenseId, List<Tag>> = emptyMap(),
    private val receipts: Map<ExpenseId, List<ReceiptImage>> = emptyMap(),
) : ExpenseRepository {
    override suspend fun getExpense(expenseId: ExpenseId) = expenses.find { it.id == expenseId }
    override suspend fun getExpensesForTrip(tripId: TripId) = expenses.filter { it.tripId == tripId }
    override suspend fun getAllActiveExpenses() = expenses
    override suspend fun getTagsForExpense(expenseId: ExpenseId) = tags[expenseId] ?: emptyList()
    override suspend fun getReceiptsForExpense(expenseId: ExpenseId) = receipts[expenseId] ?: emptyList()
    override fun observeExpensesForTrip(tripId: TripId): Flow<List<Expense>> = flowOf(expenses.filter { it.tripId == tripId })
}

private class FakeTripRepository(
    private val trips: List<Trip> = emptyList(),
) : TripRepository {
    override suspend fun getTrip(tripId: TripId) = trips.find { it.id == tripId }
    override suspend fun getActiveTrips() = trips
    override suspend fun getArchivedTrips() = emptyList<Trip>()
    override fun observeActiveTrips(): Flow<List<Trip>> = flowOf(trips)
}

private class FakeCategoryRepository(
    private val categories: List<Category> = emptyList(),
) : CategoryRepository {
    override suspend fun getAllCategories() = categories
    override suspend fun getCategory(categoryId: CategoryId) = categories.find { it.id == categoryId }
    override fun observeAllCategories(): Flow<List<Category>> = flowOf(categories)
}

class CsvExporterTest {

    private val now = Clock.System.now()

    private val testCategories = listOf(
        Category(id = "food", name = "Food & Drink", icon = "fork", isDefault = true),
        Category(id = "transport", name = "Transport", icon = "plane", isDefault = true),
    )

    private val testTrip = Trip(
        id = "trip-1",
        name = "Paris Trip",
        baseCurrency = "EUR",
        destination = "Paris",
        createdAt = now,
    )

    private fun makeTestExpense(
        id: String,
        tripId: String = "trip-1",
        amount: String = "25.50",
        currency: String = "EUR",
        categoryId: String = "food",
        vendor: String? = "Cafe Paris",
        date: LocalDate = LocalDate(2024, 6, 15),
        notes: String? = null,
        taxAmount: String? = null,
    ): Expense {
        return Expense(
            id = id,
            tripId = tripId,
            amount = amount,
            currency = currency,
            categoryId = categoryId,
            vendor = vendor,
            date = date,
            notes = notes,
            taxAmount = taxAmount,
            createdAt = now,
            lastModifiedAt = now,
        )
    }

    @Test
    fun exportContainsBomForExcel() = runTest {
        val exporter = CsvExporter(
            FakeExpenseRepository(), FakeTripRepository(listOf(testTrip)), FakeCategoryRepository(testCategories)
        )
        val csv = exporter.exportTrip("trip-1")
        assertTrue(csv.startsWith('\uFEFF'.toString()), "CSV should start with UTF-8 BOM")
    }

    @Test
    fun exportContainsHeaderRow() = runTest {
        val exporter = CsvExporter(
            FakeExpenseRepository(), FakeTripRepository(listOf(testTrip)), FakeCategoryRepository(testCategories)
        )
        val csv = exporter.exportTrip("trip-1")
        val lines = csv.lines()
        // First line after BOM is the header
        val header = lines[0].removePrefix('\uFEFF'.toString())
        assertContains(header, "Expense ID")
        assertContains(header, "Amount")
        assertContains(header, "Currency")
        assertContains(header, "Vendor")
        assertContains(header, "Date")
        assertContains(header, "Category")
    }

    @Test
    fun exportSingleExpense() = runTest {
        val expense = makeTestExpense("e1", vendor = "Boulangerie", amount = "12.50", taxAmount = "1.00")
        val exporter = CsvExporter(
            FakeExpenseRepository(listOf(expense)),
            FakeTripRepository(listOf(testTrip)),
            FakeCategoryRepository(testCategories),
        )
        val csv = exporter.exportTrip("trip-1")
        val lines = csv.trimEnd().lines()
        // BOM + header + 1 data row
        assertTrue(lines.size >= 2)
        val dataLine = lines[1]
        assertContains(dataLine, "e1")
        assertContains(dataLine, "12.50")
        assertContains(dataLine, "EUR")
        assertContains(dataLine, "Boulangerie")
        assertContains(dataLine, "Food & Drink") // Category name resolved
    }

    @Test
    fun exportMultipleExpensesSortedByDate() = runTest {
        val e1 = makeTestExpense("e1", date = LocalDate(2024, 6, 20))
        val e2 = makeTestExpense("e2", date = LocalDate(2024, 6, 10))
        val e3 = makeTestExpense("e3", date = LocalDate(2024, 6, 15))
        val exporter = CsvExporter(
            FakeExpenseRepository(listOf(e1, e2, e3)),
            FakeTripRepository(listOf(testTrip)),
            FakeCategoryRepository(testCategories),
        )
        val csv = exporter.exportTrip("trip-1")
        val lines = csv.trimEnd().lines()
        assertTrue(lines.size >= 4) // header + 3 data rows
        // e2 (June 10) should come first
        assertContains(lines[1], "e2")
        assertContains(lines[2], "e3")
        assertContains(lines[3], "e1")
    }

    @Test
    fun exportDateRangeFilter() = runTest {
        val e1 = makeTestExpense("e1", date = LocalDate(2024, 6, 1))
        val e2 = makeTestExpense("e2", date = LocalDate(2024, 6, 15))
        val e3 = makeTestExpense("e3", date = LocalDate(2024, 7, 1))
        val exporter = CsvExporter(
            FakeExpenseRepository(listOf(e1, e2, e3)),
            FakeTripRepository(listOf(testTrip)),
            FakeCategoryRepository(testCategories),
        )
        val csv = exporter.exportDateRange(LocalDate(2024, 6, 10), LocalDate(2024, 6, 20))
        val lines = csv.trimEnd().lines()
        // Only e2 is in range
        assertTrue(lines.size >= 2)
        assertContains(lines[1], "e2")
        assertTrue(lines.size == 2, "Only one expense should be in date range")
    }

    @Test
    fun exportAllReturnsAllExpenses() = runTest {
        val e1 = makeTestExpense("e1", tripId = "trip-1")
        val e2 = makeTestExpense("e2", tripId = "trip-2")
        val exporter = CsvExporter(
            FakeExpenseRepository(listOf(e1, e2)),
            FakeTripRepository(listOf(testTrip)),
            FakeCategoryRepository(testCategories),
        )
        val csv = exporter.exportAll()
        val lines = csv.trimEnd().lines()
        assertTrue(lines.size >= 3) // header + 2 data rows
    }

    @Test
    fun exportEmptyTrip() = runTest {
        val exporter = CsvExporter(
            FakeExpenseRepository(),
            FakeTripRepository(listOf(testTrip)),
            FakeCategoryRepository(testCategories),
        )
        val csv = exporter.exportTrip("trip-1")
        val lines = csv.trimEnd().lines()
        // Just header (with BOM)
        assertTrue(lines.size == 1)
    }

    // -- CSV escaping tests -----------------------------------------------

    @Test
    fun escapeCsvQuotesValues() {
        assertEquals("\"hello, world\"", escapeCsv("hello, world"))
    }

    @Test
    fun escapeCsvDoubleQuotes() {
        assertEquals("\"say \"\"hello\"\"\"", escapeCsv("say \"hello\""))
    }

    @Test
    fun escapeCsvPlainValue() {
        assertEquals("hello", escapeCsv("hello"))
    }

    @Test
    fun exportVendorWithComma() = runTest {
        val expense = makeTestExpense("e1", vendor = "Joe's Diner, Inc.")
        val exporter = CsvExporter(
            FakeExpenseRepository(listOf(expense)),
            FakeTripRepository(listOf(testTrip)),
            FakeCategoryRepository(testCategories),
        )
        val csv = exporter.exportTrip("trip-1")
        // Vendor with comma should be quoted in CSV
        assertContains(csv, "\"Joe's Diner, Inc.\"")
    }

    private fun assertEquals(expected: String, actual: String) {
        kotlin.test.assertEquals(expected, actual)
    }
}
