package com.example.alldownloader

import android.app.Application
import com.example.alldownloader.utils.PreferencesManager
import com.example.alldownloader.utils.ThemeUtils

class AllDownApp : Application() {

    override fun onCreate() {
        super.onCreate()
        val prefs = PreferencesManager.getInstance(this)
        ThemeUtils.applyTheme(prefs.getSettings().themeMode)
    }
}
