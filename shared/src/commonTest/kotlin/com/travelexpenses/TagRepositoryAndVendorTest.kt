package com.travelexpenses

import com.travelexpenses.db.TravelExpensesDb
import com.travelexpenses.event.EventReplayEngine
import com.travelexpenses.repository.SqlDelightExpenseRepository
import com.travelexpenses.repository.SqlDelightTagRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for SqlDelightTagRepository and ExpenseRepository.getDistinctVendors().
 */
class TagRepositoryAndVendorTest {

    private lateinit var db: TravelExpensesDb
    private lateinit var engine: EventReplayEngine
    private lateinit var tagRepo: SqlDelightTagRepository
    private lateinit var expenseRepo: SqlDelightExpenseRepository
    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        val driver = createInMemoryDriver()
        db = TravelExpensesDb(driver)
        engine = EventReplayEngine(db, testDispatcher)
        tagRepo = SqlDelightTagRepository(db, testDispatcher)
        expenseRepo = SqlDelightExpenseRepository(db, testDispatcher)
        TestHelpers.resetSequence()
    }

    // -- TagRepository tests --

    @Test
    fun getAllTags_returnsEmpty_whenNoTags() = runTest(testDispatcher) {
        val tags = tagRepo.getAllTags()
        assertTrue(tags.isEmpty())
    }

    @Test
    fun getAllTags_returnsAllCreatedTags() = runTest(testDispatcher) {
        engine.apply(TestHelpers.makeTagCreatedEvent(tag = TestHelpers.makeTag(id = "tag-1", label = "business")))
        engine.apply(TestHelpers.makeTagCreatedEvent(tag = TestHelpers.makeTag(id = "tag-2", label = "personal")))
        engine.apply(TestHelpers.makeTagCreatedEvent(tag = TestHelpers.makeTag(id = "tag-3", label = "reimbursable")))

        val tags = tagRepo.getAllTags()
        assertEquals(3, tags.size)
    }

    @Test
    fun getAllTags_orderedByLabel() = runTest(testDispatcher) {
        engine.apply(TestHelpers.makeTagCreatedEvent(tag = TestHelpers.makeTag(id = "tag-z", label = "zebra")))
        engine.apply(TestHelpers.makeTagCreatedEvent(tag = TestHelpers.makeTag(id = "tag-a", label = "alpha")))
        engine.apply(TestHelpers.makeTagCreatedEvent(tag = TestHelpers.makeTag(id = "tag-m", label = "middle")))

        val tags = tagRepo.getAllTags()
        assertEquals("alpha", tags[0].label)
        assertEquals("middle", tags[1].label)
        assertEquals("zebra", tags[2].label)
    }

    @Test
    fun getTag_returnsCorrectTag() = runTest(testDispatcher) {
        engine.apply(TestHelpers.makeTagCreatedEvent(tag = TestHelpers.makeTag(id = "tag-1", label = "business")))

        val tag = tagRepo.getTag("tag-1")
        assertNotNull(tag)
        assertEquals("tag-1", tag.id)
        assertEquals("business", tag.label)
    }

    @Test
    fun getTag_returnsNullForNonExistent() = runTest(testDispatcher) {
        val tag = tagRepo.getTag("non-existent")
        assertNull(tag)
    }

    @Test
    fun observeAllTags_emitsCurrentState() = runTest(testDispatcher) {
        engine.apply(TestHelpers.makeTagCreatedEvent(tag = TestHelpers.makeTag(id = "tag-1", label = "business")))
        engine.apply(TestHelpers.makeTagCreatedEvent(tag = TestHelpers.makeTag(id = "tag-2", label = "personal")))

        val tags = tagRepo.observeAllTags().first()
        assertEquals(2, tags.size)
    }

    @Test
    fun duplicateTagInsert_isIgnored() = runTest(testDispatcher) {
        engine.apply(TestHelpers.makeTagCreatedEvent(tag = TestHelpers.makeTag(id = "tag-1", label = "business")))
        engine.apply(TestHelpers.makeTagCreatedEvent(tag = TestHelpers.makeTag(id = "tag-1", label = "business-duplicate")))

        val tags = tagRepo.getAllTags()
        assertEquals(1, tags.size)
        // INSERT OR IGNORE keeps the original label
        assertEquals("business", tags[0].label)
    }

    // -- getDistinctVendors tests --

    @Test
    fun getDistinctVendors_returnsEmpty_whenNoExpenses() = runTest(testDispatcher) {
        val vendors = expenseRepo.getDistinctVendors()
        assertTrue(vendors.isEmpty())
    }

    @Test
    fun getDistinctVendors_returnsDistinctNonNullVendors() = runTest(testDispatcher) {
        engine.apply(TestHelpers.makeExpenseCreatedEvent(
            expense = TestHelpers.makeExpense(id = "exp-1", vendor = "Starbucks")
        ))
        engine.apply(TestHelpers.makeExpenseCreatedEvent(
            expense = TestHelpers.makeExpense(id = "exp-2", vendor = "Starbucks")
        ))
        engine.apply(TestHelpers.makeExpenseCreatedEvent(
            expense = TestHelpers.makeExpense(id = "exp-3", vendor = "McDonalds")
        ))

        val vendors = expenseRepo.getDistinctVendors()
        assertEquals(2, vendors.size)
        assertTrue(vendors.contains("Starbucks"))
        assertTrue(vendors.contains("McDonalds"))
    }

    @Test
    fun getDistinctVendors_excludesNullAndEmptyVendors() = runTest(testDispatcher) {
        engine.apply(TestHelpers.makeExpenseCreatedEvent(
            expense = TestHelpers.makeExpense(id = "exp-1", vendor = "Starbucks")
        ))
        engine.apply(TestHelpers.makeExpenseCreatedEvent(
            expense = TestHelpers.makeExpense(id = "exp-2", vendor = null)
        ))
        engine.apply(TestHelpers.makeExpenseCreatedEvent(
            expense = TestHelpers.makeExpense(id = "exp-3", vendor = "")
        ))

        val vendors = expenseRepo.getDistinctVendors()
        assertEquals(1, vendors.size)
        assertEquals("Starbucks", vendors[0])
    }

    @Test
    fun getDistinctVendors_excludesDeletedExpenses() = runTest(testDispatcher) {
        engine.apply(TestHelpers.makeExpenseCreatedEvent(
            expense = TestHelpers.makeExpense(id = "exp-1", vendor = "Starbucks")
        ))
        engine.apply(TestHelpers.makeExpenseCreatedEvent(
            expense = TestHelpers.makeExpense(id = "exp-2", vendor = "McDonalds")
        ))
        engine.apply(TestHelpers.makeExpenseDeletedEvent(expenseId = "exp-2"))

        val vendors = expenseRepo.getDistinctVendors()
        assertEquals(1, vendors.size)
        assertEquals("Starbucks", vendors[0])
    }

    @Test
    fun getDistinctVendors_orderedAlphabetically() = runTest(testDispatcher) {
        engine.apply(TestHelpers.makeExpenseCreatedEvent(
            expense = TestHelpers.makeExpense(id = "exp-1", vendor = "Zara")
        ))
        engine.apply(TestHelpers.makeExpenseCreatedEvent(
            expense = TestHelpers.makeExpense(id = "exp-2", vendor = "Apple Store")
        ))
        engine.apply(TestHelpers.makeExpenseCreatedEvent(
            expense = TestHelpers.makeExpense(id = "exp-3", vendor = "McDonalds")
        ))

        val vendors = expenseRepo.getDistinctVendors()
        assertEquals(listOf("Apple Store", "McDonalds", "Zara"), vendors)
    }
}
