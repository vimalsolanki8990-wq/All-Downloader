package com.example.alldownloader.data.model

import com.example.alldownloader.R

enum class DownloadStatus(val titleRes: Int, val isTerminal: Boolean) {
    QUEUED(R.string.status_queued, false),
    DOWNLOADING(R.string.status_downloading, false),
    PAUSED(R.string.status_paused, false),
    COMPLETED(R.string.status_completed, true),
    FAILED(R.string.status_failed, true),
    CANCELLED(R.string.status_cancelled, true);

    val isActive: Boolean
        get() = this == QUEUED || this == DOWNLOADING

    val isPaused: Boolean
        get() = this == PAUSED
}
