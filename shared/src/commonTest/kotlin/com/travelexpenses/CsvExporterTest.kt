package com.travelexpenses

import com.travelexpenses.currency.CurrencyConverter
import com.travelexpenses.db.TravelExpensesDb
import com.travelexpenses.event.EventReplayEngine
import com.travelexpenses.event.ExpenseEvent
import com.travelexpenses.export.CsvExporter
import com.travelexpenses.model.*
import com.travelexpenses.repository.*
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlin.test.*

class CsvExporterTest {

    private lateinit var db: TravelExpensesDb
    private lateinit var expenseRepo: SqlDelightExpenseRepository
    private lateinit var tripRepo: SqlDelightTripRepository
    private lateinit var categoryRepo: SqlDelightCategoryRepository
    private lateinit var exchangeRateRepo: SqlDelightExchangeRateRepository
    private lateinit var replayEngine: EventReplayEngine
    private lateinit var exporter: CsvExporter

    @BeforeTest
    fun setUp() {
        TestHelpers.resetSequence()
        val driver = createInMemoryDriver()
        db = TravelExpensesDb(driver)
        val testDispatcher = StandardTestDispatcher()
        expenseRepo = SqlDelightExpenseRepository(db, testDispatcher)
        tripRepo = SqlDelightTripRepository(db, testDispatcher)
        categoryRepo = SqlDelightCategoryRepository(db, testDispatcher)
        exchangeRateRepo = SqlDelightExchangeRateRepository(db, testDispatcher)
        replayEngine = EventReplayEngine(db, testDispatcher)
        val converter = CurrencyConverter(exchangeRateRepo)
        exporter = CsvExporter(expenseRepo, tripRepo, categoryRepo, converter)
    }

    private suspend fun seedTripAndCategory() {
        val trip = TestHelpers.makeTrip(id = "trip-1", name = "Europe 2026", baseCurrency = "USD")
        replayEngine.apply(TestHelpers.makeTripCreatedEvent(trip = trip))

        val category = TestHelpers.makeCategory(id = "food", name = "Food & Drink", icon = "fork")
        replayEngine.apply(TestHelpers.makeCategoryCreatedEvent(category = category))
    }

    // -- Header --

    @Test
    fun csvStartsWithBomAndHeader() = runTest {
        val csv = exporter.exportAll()
        assertTrue(csv.startsWith(CsvExporter.UTF8_BOM), "CSV should start with UTF-8 BOM")
        val firstLine = csv.lines()[0].removePrefix(CsvExporter.UTF8_BOM)
        assertTrue(firstLine.contains("Expense ID"))
        assertTrue(firstLine.contains("Trip"))
        assertTrue(firstLine.contains("Date"))
        assertTrue(firstLine.contains("Amount"))
        assertTrue(firstLine.contains("Currency"))
    }

    // -- Empty export --

    @Test
    fun emptyExportHasOnlyHeader() = runTest {
        val csv = exporter.exportAll()
        val lines = csv.lines().filter { it.isNotBlank() }
        assertEquals(1, lines.size, "Empty export should have header only")
    }

    // -- Single trip export --

    @Test
    fun exportTripIncludesExpenses() = runTest {
        seedTripAndCategory()

        val expense = TestHelpers.makeExpense(
            id = "exp-1",
            tripId = "trip-1",
            amount = "22.24",
            currency = "USD",
            vendor = "Downtown Grill",
        )
        replayEngine.apply(TestHelpers.makeExpenseCreatedEvent(expense = expense))

        val csv = exporter.exportTrip("trip-1")
        val lines = csv.lines().filter { it.isNotBlank() }
        assertEquals(2, lines.size, "Should have header + 1 expense row")
        assertTrue(lines[1].contains("exp-1"))
        assertTrue(lines[1].contains("22.24"))
        assertTrue(lines[1].contains("Downtown Grill"))
        assertTrue(lines[1].contains("Europe 2026"))
    }

    @Test
    fun exportTripShowsCategoryName() = runTest {
        seedTripAndCategory()

        val expense = TestHelpers.makeExpense(id = "exp-1", tripId = "trip-1", categoryId = "food")
        replayEngine.apply(TestHelpers.makeExpenseCreatedEvent(expense = expense))

        val csv = exporter.exportTrip("trip-1")
        assertTrue(csv.contains("Food & Drink"))
    }

    @Test
    fun exportNonexistentTripReturnsEmptyCsv() = runTest {
        val csv = exporter.exportTrip("no-such-trip")
        val lines = csv.lines().filter { it.isNotBlank() }
        assertEquals(1, lines.size, "Nonexistent trip should return header only")
    }

    // -- Date range export --

    @Test
    fun exportDateRangeFiltersExpenses() = runTest {
        seedTripAndCategory()

        val jan15 = TestHelpers.makeExpense(id = "exp-jan", tripId = "trip-1", date = LocalDate(2026, 1, 15))
        val feb20 = TestHelpers.makeExpense(id = "exp-feb", tripId = "trip-1", date = LocalDate(2026, 2, 20))
        val mar10 = TestHelpers.makeExpense(id = "exp-mar", tripId = "trip-1", date = LocalDate(2026, 3, 10))

        replayEngine.apply(TestHelpers.makeExpenseCreatedEvent(expense = jan15))
        replayEngine.apply(TestHelpers.makeExpenseCreatedEvent(expense = feb20))
        replayEngine.apply(TestHelpers.makeExpenseCreatedEvent(expense = mar10))

        val csv = exporter.exportDateRange(LocalDate(2026, 2, 1), LocalDate(2026, 2, 28))
        val lines = csv.lines().filter { it.isNotBlank() }
        assertEquals(2, lines.size, "Should have header + 1 expense in range")
        assertTrue(lines[1].contains("exp-feb"))
    }

    // -- Export all --

    @Test
    fun exportAllIncludesAllExpenses() = runTest {
        seedTripAndCategory()

        val e1 = TestHelpers.makeExpense(id = "exp-1", tripId = "trip-1")
        val e2 = TestHelpers.makeExpense(id = "exp-2", tripId = "trip-1")

        replayEngine.apply(TestHelpers.makeExpenseCreatedEvent(expense = e1))
        replayEngine.apply(TestHelpers.makeExpenseCreatedEvent(expense = e2))

        val csv = exporter.exportAll()
        val lines = csv.lines().filter { it.isNotBlank() }
        assertEquals(3, lines.size, "Should have header + 2 expenses")
    }

    // -- CSV escaping --

    @Test
    fun escapesCommasInFields() {
        val exporterForEscape = CsvExporter(expenseRepo, tripRepo, categoryRepo, null)
        assertEquals("\"hello, world\"", exporterForEscape.escapeCsvField("hello, world"))
    }

    @Test
    fun escapesQuotesInFields() {
        val exporterForEscape = CsvExporter(expenseRepo, tripRepo, categoryRepo, null)
        assertEquals("\"say \"\"hello\"\"\"", exporterForEscape.escapeCsvField("say \"hello\""))
    }

    @Test
    fun doesNotEscapeSimpleFields() {
        val exporterForEscape = CsvExporter(expenseRepo, tripRepo, categoryRepo, null)
        assertEquals("simple", exporterForEscape.escapeCsvField("simple"))
    }

    // -- Currency conversion in export --

    @Test
    fun exportTripIncludesConvertedAmounts() = runTest {
        seedTripAndCategory()

        // USD trip, EUR expense
        exchangeRateRepo.upsertRate(
            ExchangeRate("EUR", "USD", "1.09", LocalDate(2026, 1, 15), "api", Clock.System.now())
        )

        val expense = TestHelpers.makeExpense(
            id = "exp-eur", tripId = "trip-1", amount = "100.00", currency = "EUR",
            date = LocalDate(2026, 1, 15),
        )
        replayEngine.apply(TestHelpers.makeExpenseCreatedEvent(expense = expense))

        val csv = exporter.exportTrip("trip-1")
        // Should include the converted amount (100 EUR * 1.09 = 109 USD)
        assertTrue(csv.contains("109.00"), "CSV should contain converted amount: $csv")
    }

    // -- Deleted expenses excluded --

    @Test
    fun deletedExpensesNotExported() = runTest {
        seedTripAndCategory()

        val e1 = TestHelpers.makeExpense(id = "exp-keep", tripId = "trip-1")
        val e2 = TestHelpers.makeExpense(id = "exp-delete", tripId = "trip-1")

        replayEngine.apply(TestHelpers.makeExpenseCreatedEvent(expense = e1))
        replayEngine.apply(TestHelpers.makeExpenseCreatedEvent(expense = e2))
        replayEngine.apply(TestHelpers.makeExpenseDeletedEvent(expenseId = "exp-delete"))

        val csv = exporter.exportAll()
        val lines = csv.lines().filter { it.isNotBlank() }
        assertEquals(2, lines.size, "Should have header + 1 non-deleted expense")
        assertTrue(lines[1].contains("exp-keep"))
        assertFalse(lines[1].contains("exp-delete"))
    }
}
