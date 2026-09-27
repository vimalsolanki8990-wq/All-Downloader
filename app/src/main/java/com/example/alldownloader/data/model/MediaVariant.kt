package com.example.alldownloader.data.model

data class MediaVariant(
    val qualityLabel: String,      // e.g. "1080p", "720p", "480p", "360p", "Original"
    val format: String,            // e.g. "MP4", "WEBM"
    val sizeBytes: Long?,          // e.g. 15482910
    val directUrl: String,
    val resolutionWidth: Int? = null,
    val resolutionHeight: Int? = null,
    val fps: Int? = null,
    val isAudioOnly: Boolean = false
)
