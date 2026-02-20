package com.travelexpenses.repository

import com.travelexpenses.model.*
import kotlinx.coroutines.flow.Flow

interface ExpenseRepository {
    suspend fun getExpense(expenseId: ExpenseId): Expense?
    suspend fun getExpensesForTrip(tripId: TripId): List<Expense>
    suspend fun getAllActiveExpenses(): List<Expense>
    suspend fun getTagsForExpense(expenseId: ExpenseId): List<Tag>
    suspend fun getReceiptsForExpense(expenseId: ExpenseId): List<ReceiptImage>
    fun observeExpensesForTrip(tripId: TripId): Flow<List<Expense>>
    suspend fun getDistinctVendors(): List<String>
}
