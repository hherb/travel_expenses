package com.travelexpenses.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.travelexpenses.db.TravelExpensesDb
import com.travelexpenses.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlin.coroutines.CoroutineContext

class SqlDelightExpenseRepository(
    private val db: TravelExpensesDb,
    private val queryContext: CoroutineContext,
) : ExpenseRepository {

    override suspend fun getExpense(expenseId: ExpenseId): Expense? = withContext(queryContext) {
        db.materializedStateQueries.selectExpenseById(expenseId).executeAsOneOrNull()?.toExpense()
    }

    override suspend fun getExpensesForTrip(tripId: TripId): List<Expense> = withContext(queryContext) {
        db.materializedStateQueries.selectExpensesForTrip(tripId).executeAsList().map { it.toExpense() }
    }

    override suspend fun getAllActiveExpenses(): List<Expense> = withContext(queryContext) {
        db.materializedStateQueries.selectAllActiveExpenses().executeAsList().map { it.toExpense() }
    }

    override suspend fun getTagsForExpense(expenseId: ExpenseId): List<Tag> = withContext(queryContext) {
        db.materializedStateQueries.selectTagsForExpense(expenseId).executeAsList().map { row ->
            Tag(id = row.id, label = row.label)
        }
    }

    override suspend fun getReceiptsForExpense(expenseId: ExpenseId): List<ReceiptImage> = withContext(queryContext) {
        db.materializedStateQueries.selectReceiptsForExpense(expenseId).executeAsList().map { row ->
            ReceiptImage(
                id = row.id,
                expenseId = row.expense_id,
                filePath = row.file_path,
                thumbnailPath = row.thumbnail_path,
                width = row.width?.toInt(),
                height = row.height?.toInt(),
                createdAt = Instant.parse(row.created_at),
            )
        }
    }

    override fun observeExpensesForTrip(tripId: TripId): Flow<List<Expense>> {
        return db.materializedStateQueries.selectExpensesForTrip(tripId)
            .asFlow()
            .mapToList(queryContext)
            .map { rows -> rows.map { it.toExpense() } }
    }

    private fun com.travelexpenses.db.Expense.toExpense(): Expense {
        return Expense(
            id = id,
            tripId = trip_id,
            amount = amount,
            currency = currency,
            categoryId = category_id,
            vendor = vendor,
            date = LocalDate.parse(date),
            notes = notes,
            ocrConfidence = ocr_confidence?.toFloat(),
            taxAmount = tax_amount,
            createdAt = Instant.parse(created_at),
            lastModifiedAt = Instant.parse(last_modified_at),
        )
    }
}
