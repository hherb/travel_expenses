package com.travelexpenses.android.navigation

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
    data object OcrCapture : Screen("ocr/capture?tripId={tripId}") {
        fun createRoute(tripId: String) = "ocr/capture?tripId=$tripId"
    }
    data object OcrReview : Screen("ocr/review?tripId={tripId}") {
        fun createRoute(tripId: String) = "ocr/review?tripId=$tripId"
    }
    data object Reports : Screen("reports")
    data object Settings : Screen("settings")
}
