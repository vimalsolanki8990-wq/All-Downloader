package com.example.alldownloader.data.model

data class DownloadItem(
    val id: String,
    val url: String,
    val title: String,
    val fileName: String,
    val filePath: String,
    val mimeType: String,
    val category: MediaCategory,
    val totalBytes: Long,
    val downloadedBytes: Long = 0L,
    val speedBytesPerSec: Long = 0L,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val resolution: String? = null,
    val bitrate: String? = null,
    val format: String? = null,
    val thumbnailUri: String? = null
) {
    val progressPercent: Int
        get() = if (totalBytes > 0) ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100) else 0

    val isIndeterminate: Boolean
        get() = totalBytes <= 0L && status == DownloadStatus.DOWNLOADING
}
