package com.simplemobiletools.musicplayer

import android.app.Application
import com.facebook.stetho.Stetho
import com.simplemobiletools.commons.extensions.checkUseEnglish
import com.squareup.leakcanary.LeakCanary

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            if (LeakCanary.isInAnalyzerProcess(this)) {
                return
            }
            LeakCanary.install(this)
            Stetho.initializeWithDefaults(this)
        }
        checkUseEnglish()
    }
}
