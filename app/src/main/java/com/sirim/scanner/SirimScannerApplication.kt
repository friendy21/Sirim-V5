package com.sirim.scanner

import android.app.Application
import com.sirim.scanner.analytics.ScanAnalytics
import com.sirim.scanner.data.AppContainer
import com.sirim.scanner.data.DefaultAppContainer

class SirimScannerApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)
        ScanAnalytics.installCrashlyticsIfAvailable()
    }
}
