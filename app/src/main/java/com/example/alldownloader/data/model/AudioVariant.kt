package com.example.alldownloader.data.model

data class AudioVariant(
    val format: String,            // e.g. "MP3", "M4A", "AAC", "WAV", "FLAC", "WEBM"
    val bitrateKbps: Int?,         // e.g. 128, 192, 256, 320
    val sizeBytes: Long?,          // e.g. 4500000
    val directUrl: String,
    val durationSeconds: Long? = null
)
