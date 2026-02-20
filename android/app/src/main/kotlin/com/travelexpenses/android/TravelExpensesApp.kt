package com.travelexpenses.android

import android.app.Application
import com.travelexpenses.android.di.appModule
import com.travelexpenses.android.init.DefaultCategoryInitializer
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class TravelExpensesApp : Application() {

    private val defaultCategoryInitializer: DefaultCategoryInitializer by inject()

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@TravelExpensesApp)
            modules(appModule)
        }
        defaultCategoryInitializer.initializeIfNeeded()
    }
}
