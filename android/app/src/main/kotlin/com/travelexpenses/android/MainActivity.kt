package com.travelexpenses.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.travelexpenses.android.navigation.AppNavigation
import com.travelexpenses.android.ui.theme.TravelExpensesTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TravelExpensesTheme {
                AppNavigation()
            }
        }
    }
}
