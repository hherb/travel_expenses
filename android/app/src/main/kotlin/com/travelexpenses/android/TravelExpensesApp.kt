package com.travelexpenses.android

import android.app.Application
import com.travelexpenses.android.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class TravelExpensesApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@TravelExpensesApp)
            modules(appModule)
        }
    }
}
