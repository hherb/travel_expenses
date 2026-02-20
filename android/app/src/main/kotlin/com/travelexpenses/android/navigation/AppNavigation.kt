package com.travelexpenses.android.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Luggage
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.travelexpenses.android.ui.capture.OcrCaptureScreen
import com.travelexpenses.android.ui.capture.OcrReviewScreen
import com.travelexpenses.android.ui.expenses.ExpenseEntryScreen
import com.travelexpenses.android.ui.reports.ReportsScreen
import com.travelexpenses.android.ui.settings.SettingsScreen
import com.travelexpenses.android.ui.trips.TripDetailScreen
import com.travelexpenses.android.ui.trips.TripListScreen
import java.net.URLDecoder

data class BottomNavItem(
    val label: String,
    val icon: ImageVector,
    val route: String,
)

val bottomNavItems = listOf(
    BottomNavItem("Trips", Icons.Default.Luggage, Screen.TripList.route),
    BottomNavItem("Reports", Icons.Default.BarChart, Screen.Reports.route),
    BottomNavItem("Settings", Icons.Default.Settings, Screen.Settings.route),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val showBottomBar = bottomNavItems.any { item ->
        currentDestination?.hierarchy?.any { it.route == item.route } == true
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { item ->
                        NavigationBarItem(
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) },
                            selected = currentDestination?.hierarchy?.any { it.route == item.route } == true,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.TripList.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Screen.TripList.route) {
                TripListScreen(
                    onTripClick = { tripId ->
                        navController.navigate(Screen.TripDetail.createRoute(tripId))
                    },
                    onNavigateToSettings = {
                        navController.navigate(Screen.Settings.route)
                    }
                )
            }

            composable(
                Screen.TripDetail.route,
                arguments = listOf(navArgument("tripId") { type = NavType.StringType })
            ) { backStackEntry ->
                val tripId = backStackEntry.arguments?.getString("tripId") ?: return@composable
                TripDetailScreen(
                    tripId = tripId,
                    onBack = { navController.popBackStack() },
                    onAddExpense = {
                        navController.navigate(Screen.ExpenseEntry.createRoute(tripId))
                    },
                    onScanReceipt = {
                        navController.navigate(Screen.OcrCapture.createRoute(tripId))
                    },
                    onExpenseClick = { expenseId ->
                        navController.navigate(Screen.ExpenseEdit.createRoute(expenseId))
                    }
                )
            }

            composable(
                Screen.ExpenseEntry.route,
                arguments = listOf(navArgument("tripId") { type = NavType.StringType })
            ) { backStackEntry ->
                val tripId = backStackEntry.arguments?.getString("tripId") ?: return@composable
                ExpenseEntryScreen(
                    tripId = tripId,
                    expenseId = null,
                    onSaved = { navController.popBackStack() },
                    onCancel = { navController.popBackStack() },
                )
            }

            composable(
                Screen.ExpenseEdit.route,
                arguments = listOf(navArgument("expenseId") { type = NavType.StringType })
            ) { backStackEntry ->
                val expenseId = backStackEntry.arguments?.getString("expenseId") ?: return@composable
                ExpenseEntryScreen(
                    tripId = null,
                    expenseId = expenseId,
                    onSaved = { navController.popBackStack() },
                    onCancel = { navController.popBackStack() },
                )
            }

            // OCR: expense entry pre-filled from OCR review
            composable(
                Screen.OcrExpenseEntry.route,
                arguments = listOf(
                    navArgument("tripId") { type = NavType.StringType },
                    navArgument("vendor") { type = NavType.StringType; defaultValue = "" },
                    navArgument("amount") { type = NavType.StringType; defaultValue = "" },
                    navArgument("currency") { type = NavType.StringType; defaultValue = "" },
                    navArgument("date") { type = NavType.StringType; defaultValue = "" },
                    navArgument("taxAmount") { type = NavType.StringType; defaultValue = "" },
                )
            ) { backStackEntry ->
                val args = backStackEntry.arguments ?: return@composable
                val tripId = args.getString("tripId") ?: return@composable
                val vendor = URLDecoder.decode(args.getString("vendor") ?: "", "UTF-8")
                val amount = URLDecoder.decode(args.getString("amount") ?: "", "UTF-8")
                val currency = URLDecoder.decode(args.getString("currency") ?: "", "UTF-8")
                val date = URLDecoder.decode(args.getString("date") ?: "", "UTF-8")
                val taxAmount = URLDecoder.decode(args.getString("taxAmount") ?: "", "UTF-8")
                ExpenseEntryScreen(
                    tripId = tripId,
                    expenseId = null,
                    ocrVendor = vendor,
                    ocrAmount = amount,
                    ocrCurrency = currency,
                    ocrDate = date,
                    ocrTaxAmount = taxAmount,
                    onSaved = {
                        // Pop back to trip detail (past both OCR screens)
                        navController.popBackStack(Screen.TripDetail.route, inclusive = false)
                    },
                    onCancel = { navController.popBackStack() },
                )
            }

            composable(
                Screen.OcrCapture.route,
                arguments = listOf(navArgument("tripId") { type = NavType.StringType })
            ) { backStackEntry ->
                val tripId = backStackEntry.arguments?.getString("tripId") ?: return@composable
                OcrCaptureScreen(
                    tripId = tripId,
                    onImageCaptured = { imagePath ->
                        navController.navigate(Screen.OcrReview.createRoute(tripId, imagePath)) {
                            popUpTo(Screen.OcrCapture.route) { inclusive = true }
                        }
                    },
                    onCancel = { navController.popBackStack() },
                )
            }

            composable(
                Screen.OcrReview.route,
                arguments = listOf(
                    navArgument("tripId") { type = NavType.StringType },
                    navArgument("imagePath") { type = NavType.StringType },
                )
            ) { backStackEntry ->
                val tripId = backStackEntry.arguments?.getString("tripId") ?: return@composable
                val encodedPath = backStackEntry.arguments?.getString("imagePath") ?: return@composable
                val imagePath = Screen.OcrReview.decodePath(encodedPath)
                OcrReviewScreen(
                    tripId = tripId,
                    imagePath = imagePath,
                    onSaveExpense = { vendor, amount, currency, date, taxAmount ->
                        navController.navigate(
                            Screen.OcrExpenseEntry.createRoute(
                                tripId = tripId,
                                vendor = vendor,
                                amount = amount,
                                currency = currency,
                                date = date,
                                taxAmount = taxAmount,
                            )
                        ) {
                            popUpTo(Screen.OcrReview.route) { inclusive = true }
                        }
                    },
                    onCancel = { navController.popBackStack() },
                )
            }

            composable(Screen.Reports.route) {
                ReportsScreen()
            }

            composable(Screen.Settings.route) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}
