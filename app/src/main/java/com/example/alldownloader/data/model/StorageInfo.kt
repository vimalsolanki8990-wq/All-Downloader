package com.example.alldownloader.data.model

data class StorageInfo(
    val totalBytes: Long,
    val freeBytes: Long,
    val usedBytes: Long,
    val appDownloadsBytes: Long,
    val downloadsCount: Int
) {
    val usedPercentage: Int
        get() = if (totalBytes > 0) (((totalBytes - freeBytes) * 100) / totalBytes).toInt().coerceIn(0, 100) else 0
}
