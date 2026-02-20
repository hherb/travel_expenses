package com.travelexpenses.android.di

import com.travelexpenses.android.auth.BiometricHelper
import com.travelexpenses.android.auth.PinManager
import com.travelexpenses.android.ocr.MlKitOcrEngine
import com.travelexpenses.android.ui.expenses.ExpenseViewModel
import com.travelexpenses.android.ui.reports.ReportsViewModel
import com.travelexpenses.android.ui.settings.SettingsViewModel
import com.travelexpenses.android.ui.trips.TripViewModel
import com.travelexpenses.currency.CurrencyConverter
import com.travelexpenses.currency.ExchangeRateService
import com.travelexpenses.db.TravelExpensesDb
import com.travelexpenses.event.EventReplayEngine
import com.travelexpenses.export.CsvExporter
import com.travelexpenses.ocr.OcrEngine
import com.travelexpenses.ocr.OcrParser
import com.travelexpenses.repository.*
import com.travelexpenses.sync.SyncManager
import com.travelexpenses.validation.DefaultCategories
import com.travelexpenses.validation.ExpenseValidator
import kotlinx.coroutines.Dispatchers
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module
import java.util.UUID

val appModule = module {
    // Database
    single {
        val driverFactory = DatabaseDriverFactory(androidContext())
        TravelExpensesDb(driverFactory.createDriver())
    }

    // Device ID (stable across app restarts, used for event sourcing)
    single(named("deviceId")) {
        androidContext()
            .getSharedPreferences("travel_expenses", 0)
            .let { prefs ->
                prefs.getString("device_id", null) ?: UUID.randomUUID().toString().also {
                    prefs.edit().putString("device_id", it).apply()
                }
            }
    }

    // Replay engine
    single { EventReplayEngine(get(), Dispatchers.IO) }

    // Repositories
    single<EventLogRepository> {
        SqlDelightEventLogRepository(get(), Dispatchers.IO, get())
    }
    single<ExpenseRepository> { SqlDelightExpenseRepository(get(), Dispatchers.IO) }
    single<TripRepository> { SqlDelightTripRepository(get(), Dispatchers.IO) }
    single<CategoryRepository> { SqlDelightCategoryRepository(get(), Dispatchers.IO) }
    single<ExchangeRateRepository> { SqlDelightExchangeRateRepository(get(), Dispatchers.IO) }

    // Business logic
    single { CurrencyConverter(get()) }
    single { ExchangeRateService(get()) }
    single { CsvExporter(get(), get(), get(), get()) }
    single { OcrParser() }
    single<OcrEngine> { MlKitOcrEngine() }
    single { SyncManager(get(), get(), get(named("deviceId"))) }
    single { ExpenseValidator() }

    // Default categories initializer
    single { DefaultCategories }

    // Security
    single { PinManager(androidContext()) }
    single { BiometricHelper() }

    // ViewModels
    viewModel { TripViewModel(get(), get(), get(), get(), get(), get(named("deviceId"))) }
    viewModel { ExpenseViewModel(get(), get(), get(), get(), get(), get(named("deviceId"))) }
    viewModel { ReportsViewModel(get(), get(), get(), get()) }
    viewModel { SettingsViewModel(get(), get(), get(), androidContext()) }
}
