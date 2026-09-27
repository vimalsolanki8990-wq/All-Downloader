package com.example.alldownloader.data.model

data class MediaAnalysisResult(
    val originalUrl: String,
    val finalUrl: String,
    val httpStatus: Int,
    val contentType: String,
    val contentLength: Long,
    val isWebPage: Boolean,
    val pageTitle: String?,
    val resources: List<DiscoveredResource>,
    val isSuccess: Boolean,
    val isFallbackThumbnailOnly: Boolean = false,
    val warningNotice: String? = null,
    val errorMessage: String? = null,
    val debugLog: String = ""
) {
    val totalDiscovered: Int
        get() = resources.size

    val isSingleDirectResource: Boolean
        get() = !isWebPage && resources.size == 1 && resources.first().isDirect
}
