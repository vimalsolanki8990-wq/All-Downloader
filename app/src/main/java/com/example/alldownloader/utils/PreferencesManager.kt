package com.example.alldownloader.utils

import android.content.Context
import android.content.SharedPreferences
import com.example.alldownloader.data.model.AppSettings
import com.example.alldownloader.data.model.ThemeMode

class PreferencesManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "alldown_preferences"
        private const val KEY_THEME = "pref_theme"
        private const val KEY_DOWNLOAD_FOLDER = "pref_download_folder"
        private const val KEY_WIFI_ONLY = "pref_wifi_only"
        private const val KEY_AUTO_START = "pref_auto_start"
        private const val KEY_MAX_CONCURRENT = "pref_max_concurrent"
        private const val KEY_AUTO_CLIPBOARD = "pref_auto_clipboard"
        private const val KEY_NOTIFICATIONS = "pref_notifications"

        @Volatile
        private var INSTANCE: PreferencesManager? = null

        fun getInstance(context: Context): PreferencesManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PreferencesManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    fun getSettings(): AppSettings {
        val themeStr = prefs.getString(KEY_THEME, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name
        val theme = runCatching { ThemeMode.valueOf(themeStr) }.getOrDefault(ThemeMode.SYSTEM)
        val folder = prefs.getString(KEY_DOWNLOAD_FOLDER, "Downloads/AllDown") ?: "Downloads/AllDown"
        val wifiOnly = prefs.getBoolean(KEY_WIFI_ONLY, false)
        val autoStart = prefs.getBoolean(KEY_AUTO_START, true)
        val maxConcurrent = prefs.getInt(KEY_MAX_CONCURRENT, 3)
        val autoClipboard = prefs.getBoolean(KEY_AUTO_CLIPBOARD, true)
        val notifs = prefs.getBoolean(KEY_NOTIFICATIONS, true)

        return AppSettings(
            themeMode = theme,
            downloadFolder = folder,
            wifiOnly = wifiOnly,
            autoStart = autoStart,
            maxConcurrentDownloads = maxConcurrent,
            autoDetectClipboard = autoClipboard,
            notificationsEnabled = notifs
        )
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME, mode.name).apply()
        ThemeUtils.applyTheme(mode)
    }

    fun setWifiOnly(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WIFI_ONLY, enabled).apply()
    }

    fun setAutoStart(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_START, enabled).apply()
    }

    fun setMaxConcurrent(count: Int) {
        prefs.edit().putInt(KEY_MAX_CONCURRENT, count).apply()
    }

    fun setAutoDetectClipboard(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_CLIPBOARD, enabled).apply()
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NOTIFICATIONS, enabled).apply()
    }
}
