package com.example.alldownloader.data.model

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val downloadFolder: String = "Downloads/AllDown",
    val wifiOnly: Boolean = false,
    val autoStart: Boolean = true,
    val maxConcurrentDownloads: Int = 3,
    val autoDetectClipboard: Boolean = true,
    val notificationsEnabled: Boolean = true
)
