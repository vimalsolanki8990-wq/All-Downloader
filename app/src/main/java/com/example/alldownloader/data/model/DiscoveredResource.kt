package com.example.alldownloader.data.model

data class DiscoveredResource(
    val id: String,
    val directUrl: String,
    val pageUrl: String,
    val title: String,
    val fileName: String,
    val mimeType: String,
    val category: MediaCategory,
    val sizeBytes: Long = 0L,
    val sourceType: String, // e.g., "Direct Video Stream", "Direct Image Stream", "OpenGraph Video", "Webpage Thumbnail", etc.
    val resolution: String? = null,
    val previewUrl: String? = null,
    val isDirect: Boolean = false,
    val isSecurityRisky: Boolean = false,
    val isThumbnailFallback: Boolean = false,
    val notice: String? = null
)
