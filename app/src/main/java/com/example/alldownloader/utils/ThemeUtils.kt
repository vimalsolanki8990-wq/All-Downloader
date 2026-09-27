package com.example.alldownloader.utils

import androidx.appcompat.app.AppCompatDelegate
import com.example.alldownloader.data.model.ThemeMode

object ThemeUtils {

    fun applyTheme(mode: ThemeMode) {
        when (mode) {
            ThemeMode.SYSTEM -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            ThemeMode.LIGHT -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            ThemeMode.DARK -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        }
    }
}
