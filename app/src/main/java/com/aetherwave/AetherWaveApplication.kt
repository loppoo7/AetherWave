package com.aetherwave

import android.app.Application
import com.aetherwave.core.crash.CrashReporter
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class AetherWaveApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Install first, so crashes during Hilt setup are captured too.
        CrashReporter.install(this)
    }
}
