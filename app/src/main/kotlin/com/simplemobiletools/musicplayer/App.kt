package com.simplemobiletools.musicplayer

import android.app.Application
import com.facebook.stetho.Stetho
import com.simplemobiletools.musicplayer.BuildConfig.USE_LEAK_CANARY
import com.squareup.leakcanary.LeakCanary

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        if (USE_LEAK_CANARY) {
            if (LeakCanary.isInAnalyzerProcess(this)) {
                return
            }
            LeakCanary.install(this)
        }

        Stetho.initializeWithDefaults(this)
    }
}
