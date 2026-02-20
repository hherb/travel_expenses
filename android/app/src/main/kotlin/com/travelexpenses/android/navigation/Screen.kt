package com.travelexpenses.android.navigation

import java.net.URLDecoder
import java.net.URLEncoder

sealed class Screen(val route: String) {
    data object TripList : Screen("trips")
    data object TripDetail : Screen("trips/{tripId}") {
        fun createRoute(tripId: String) = "trips/$tripId"
    }
    data object ExpenseEntry : Screen("expense/new?tripId={tripId}") {
        fun createRoute(tripId: String) = "expense/new?tripId=$tripId"
    }
    data object ExpenseEdit : Screen("expense/edit/{expenseId}") {
        fun createRoute(expenseId: String) = "expense/edit/$expenseId"
    }
    data object OcrExpenseEntry : Screen("expense/ocr?tripId={tripId}&vendor={vendor}&amount={amount}&currency={currency}&date={date}&taxAmount={taxAmount}") {
        fun createRoute(
            tripId: String,
            vendor: String = "",
            amount: String = "",
            currency: String = "",
            date: String = "",
            taxAmount: String = "",
        ): String {
            val enc = { s: String -> URLEncoder.encode(s, "UTF-8") }
            return "expense/ocr?tripId=${enc(tripId)}&vendor=${enc(vendor)}&amount=${enc(amount)}&currency=${enc(currency)}&date=${enc(date)}&taxAmount=${enc(taxAmount)}"
        }
    }
    data object OcrCapture : Screen("ocr/capture?tripId={tripId}") {
        fun createRoute(tripId: String) = "ocr/capture?tripId=$tripId"
    }
    data object OcrReview : Screen("ocr/review?tripId={tripId}&imagePath={imagePath}") {
        fun createRoute(tripId: String, imagePath: String): String {
            val encodedPath = URLEncoder.encode(imagePath, "UTF-8")
            return "ocr/review?tripId=$tripId&imagePath=$encodedPath"
        }
        fun decodePath(encoded: String): String = URLDecoder.decode(encoded, "UTF-8")
    }
    data object Reports : Screen("reports")
    data object Settings : Screen("settings")
}
