package com.example.alldownloader.network

import android.net.Uri
import com.example.alldownloader.data.model.DiscoveredResource
import com.example.alldownloader.data.model.MediaAnalysisResult
import com.example.alldownloader.data.model.MediaCategory
import com.example.alldownloader.utils.ClipboardUtils
import com.example.alldownloader.utils.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URI
import java.net.URL
import java.net.URLDecoder
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class UrlAnalyzerEngine(private val okHttpClient: OkHttpClient) {

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
        private const val ACCEPT_HEADER = "*/*"
        private const val ACCEPT_HTML =
            "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7"
        private const val ACCEPT_LANGUAGE = "en-US,en;q=0.9"
        private const val SEC_CH_UA =
            "\"Chromium\";v=\"128\", \"Not;A=Brand\";v=\"24\", \"Google Chrome\";v=\"128\""
        private const val SEC_CH_UA_PLATFORM = "\"Windows\""

        private val DIRECT_IMAGE_EXTS = setOf(
            "jpg", "jpeg", "png", "webp", "svg", "gif", "bmp", "ico", "tiff", "avif"
        )
        private val DIRECT_VIDEO_EXTS = setOf(
            "mp4", "mkv", "webm", "mov", "avi", "flv", "m4v", "3gp", "ts", "wmv"
        )
        private val DIRECT_AUDIO_EXTS = setOf(
            "mp3", "m4a", "aac", "wav", "flac", "ogg", "opus", "wma", "oga"
        )
        private val DIRECT_DOC_EXTS = setOf(
            "pdf", "zip", "rar", "7z", "tar", "gz", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv", "apk"
        )

        @Volatile
        private var INSTANCE: UrlAnalyzerEngine? = null

        fun getInstance(): UrlAnalyzerEngine {
            return INSTANCE ?: synchronized(this) {
                val client = OkHttpClient.Builder()
                    .connectTimeout(20, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .followRedirects(true)
                    .followSslRedirects(true)
                    .build()
                INSTANCE ?: UrlAnalyzerEngine(client).also { INSTANCE = it }
            }
        }
    }

    private data class CandidateMedia(
        val rawUrl: String,
        val sourceType: String,
        val categoryHint: MediaCategory,
        val preferredName: String? = null,
        val customPosterUrl: String? = null,
        val resolutionHint: String? = null
    )

    suspend fun analyzeUrl(rawUrl: String): MediaAnalysisResult = withContext(Dispatchers.IO) {
        val debugBuilder = StringBuilder()
        val trimmedUrl = rawUrl.trim()

        debugBuilder.appendLine("=== AllDown Media Discovery Engine ===")
        debugBuilder.appendLine("Target URL: $trimmedUrl")

        // STEP 1: Validate URL format
        if (!ClipboardUtils.isValidUrl(trimmedUrl)) {
            debugBuilder.appendLine("Validation: URL format is invalid.")
            return@withContext MediaAnalysisResult(
                originalUrl = trimmedUrl,
                finalUrl = trimmedUrl,
                httpStatus = 0,
                contentType = "unknown",
                contentLength = 0L,
                isWebPage = false,
                pageTitle = null,
                resources = emptyList(),
                isSuccess = false,
                errorMessage = "The provided URL is invalid or malformed. Please enter a valid HTTP or HTTPS link.",
                debugLog = debugBuilder.toString()
            )
        }

        // Check for DRM / Protected Streaming Services
        val host = runCatching { Uri.parse(trimmedUrl).host?.lowercase() }.getOrNull() ?: ""
        if (isProtectedPlatform(host)) {
            debugBuilder.appendLine("Access Control: Protected DRM streaming service detected ($host).")
            return@withContext MediaAnalysisResult(
                originalUrl = trimmedUrl,
                finalUrl = trimmedUrl,
                httpStatus = 200,
                contentType = "text/html",
                contentLength = 0L,
                isWebPage = true,
                pageTitle = "Protected Streaming Platform",
                resources = emptyList(),
                isSuccess = false,
                errorMessage = "DRM/protected content cannot be downloaded. AllDown strictly adheres to access controls and only supports publicly accessible direct files.",
                debugLog = debugBuilder.toString()
            )
        }

        // STEP 2: FAST PATH — Instant Direct-Link Handling for known media/document extensions
        if (isDirectFileExtension(trimmedUrl)) {
            debugBuilder.appendLine("Fast Path: Direct file extension recognized in URL path.")
            val fastResult = executeFastDirectInspection(trimmedUrl, debugBuilder)
            if (fastResult != null) {
                return@withContext fastResult
            }
            debugBuilder.appendLine("Fast Path probe fallback. Continuing with standard probe...")
        }

        // STEP 3: Data-Saver Probe via lightweight HEAD / Range check
        var finalUrl = trimmedUrl
        var statusCode = 0
        var contentType = ""
        var contentLength = 0L
        var isWebpage = false
        var htmlContent: String? = null

        var headResponse: Response? = null
        try {
            debugBuilder.appendLine("Dispatching lightweight OkHttp HEAD probe with browser headers...")
            val headRequest = Request.Builder()
                .url(trimmedUrl)
                .header("User-Agent", USER_AGENT)
                .header("Accept", ACCEPT_HTML)
                .header("Accept-Language", ACCEPT_LANGUAGE)
                .header("Sec-Ch-Ua", SEC_CH_UA)
                .header("Sec-Ch-Ua-Mobile", "?0")
                .header("Sec-Ch-Ua-Platform", SEC_CH_UA_PLATFORM)
                .head()
                .build()

            headResponse = okHttpClient.newCall(headRequest).execute()
            statusCode = headResponse.code
            finalUrl = headResponse.request.url.toString()
            val rawContentType = headResponse.header("Content-Type") ?: ""
            contentType = rawContentType.lowercase().substringBefore(";").trim()
            contentLength = headResponse.header("Content-Length")?.toLongOrNull() ?: 0L

            val contentRange = headResponse.header("Content-Range")
            if (contentRange != null) {
                val totalStr = contentRange.substringAfterLast("/").trim()
                val totalVal = totalStr.toLongOrNull()
                if (totalVal != null && totalVal > 0L) {
                    contentLength = totalVal
                }
            }

            debugBuilder.appendLine("HEAD Response: HTTP $statusCode | Content-Type: $contentType | Length: $contentLength bytes")

            // If HEAD is successful and NOT a webpage, treat as direct file without downloading body
            if (headResponse.isSuccessful && !isHtmlMimeType(contentType) && isDownloadableMimeType(contentType, finalUrl)) {
                val directResult = buildDirectResourceResult(
                    originalUrl = trimmedUrl,
                    finalUrl = finalUrl,
                    statusCode = statusCode,
                    contentType = contentType,
                    contentLength = contentLength,
                    contentDisposition = headResponse.header("Content-Disposition"),
                    debugBuilder = debugBuilder
                )
                headResponse.close()
                return@withContext directResult
            }

        } catch (e: Exception) {
            debugBuilder.appendLine("HEAD probe notice: ${e.message}")
        } finally {
            runCatching { headResponse?.close() }
        }

        // STEP 4: Fetch Webpage HTML Content if HTML or if HEAD was inconclusive / 403 / 405
        var getResponse: Response? = null
        try {
            debugBuilder.appendLine("Dispatching HTTP GET probe with browser headers...")
            val getRequest = Request.Builder()
                .url(trimmedUrl)
                .header("User-Agent", USER_AGENT)
                .header("Accept", ACCEPT_HTML)
                .header("Accept-Language", ACCEPT_LANGUAGE)
                .header("Sec-Ch-Ua", SEC_CH_UA)
                .header("Sec-Ch-Ua-Mobile", "?0")
                .header("Sec-Ch-Ua-Platform", SEC_CH_UA_PLATFORM)
                .build()

            getResponse = okHttpClient.newCall(getRequest).execute()
            statusCode = getResponse.code
            finalUrl = getResponse.request.url.toString()
            val rawContentType = getResponse.header("Content-Type") ?: ""
            contentType = rawContentType.lowercase().substringBefore(";").trim()
            contentLength = getResponse.header("Content-Length")?.toLongOrNull() ?: 0L

            val contentRange = getResponse.header("Content-Range")
            if (contentRange != null) {
                val totalStr = contentRange.substringAfterLast("/").trim()
                val totalVal = totalStr.toLongOrNull()
                if (totalVal != null && totalVal > 0L) {
                    contentLength = totalVal
                }
            }

            debugBuilder.appendLine("GET Response: HTTP $statusCode ${getResponse.message}")
            debugBuilder.appendLine("Final URL: $finalUrl")
            debugBuilder.appendLine("Content-Type: $contentType | Length: $contentLength bytes")

            if (!getResponse.isSuccessful && getResponse.code != 206) {
                val errorReason = when (statusCode) {
                    401 -> "Server requires authentication (HTTP 401 Unauthorized)."
                    403 -> "Server returned HTTP 403 (Access Forbidden / Bot protection)."
                    404 -> "Resource not found on server (HTTP 404)."
                    500, 502, 503 -> "Server error (HTTP $statusCode: ${getResponse.message})."
                    else -> "Server returned HTTP error $statusCode: ${getResponse.message}"
                }
                debugBuilder.appendLine("Failure: $errorReason")
                return@withContext MediaAnalysisResult(
                    originalUrl = trimmedUrl,
                    finalUrl = finalUrl,
                    httpStatus = statusCode,
                    contentType = contentType,
                    contentLength = contentLength,
                    isWebPage = false,
                    pageTitle = null,
                    resources = emptyList(),
                    isSuccess = false,
                    errorMessage = errorReason,
                    debugLog = debugBuilder.toString()
                )
            }

            isWebpage = isHtmlMimeType(contentType)
            if (isWebpage) {
                val body = getResponse.body
                if (body != null) {
                    htmlContent = body.string()
                    debugBuilder.appendLine("HTML Body fetched (${htmlContent.length} chars).")
                }
            } else if (isDownloadableMimeType(contentType, finalUrl)) {
                // Direct file discovered on GET
                val directResult = buildDirectResourceResult(
                    originalUrl = trimmedUrl,
                    finalUrl = finalUrl,
                    statusCode = statusCode,
                    contentType = contentType,
                    contentLength = contentLength,
                    contentDisposition = getResponse.header("Content-Disposition"),
                    debugBuilder = debugBuilder
                )
                getResponse.close()
                return@withContext directResult
            }

        } catch (e: Exception) {
            debugBuilder.appendLine("Network Exception: ${e.message}")
            return@withContext MediaAnalysisResult(
                originalUrl = trimmedUrl,
                finalUrl = finalUrl,
                httpStatus = statusCode,
                contentType = contentType,
                contentLength = contentLength,
                isWebPage = false,
                pageTitle = null,
                resources = emptyList(),
                isSuccess = false,
                errorMessage = "Network connection failed: ${e.localizedMessage ?: "Unable to connect"}",
                debugLog = debugBuilder.toString()
            )
        } finally {
            runCatching { getResponse?.close() }
        }

        // STEP 5: Deep Webpage Extraction
        if (htmlContent.isNullOrBlank()) {
            debugBuilder.appendLine("Empty response body received from webpage.")
            return@withContext MediaAnalysisResult(
                originalUrl = trimmedUrl,
                finalUrl = finalUrl,
                httpStatus = statusCode,
                contentType = contentType,
                contentLength = contentLength,
                isWebPage = true,
                pageTitle = "Empty Page",
                resources = emptyList(),
                isSuccess = false,
                errorMessage = "The webpage returned an empty response body.",
                debugLog = debugBuilder.toString()
            )
        }

        val document = Jsoup.parse(htmlContent, finalUrl)
        val pageTitle = document.title().ifBlank {
            document.select("meta[property=og:title]").attr("content").ifBlank {
                document.select("meta[name=twitter:title]").attr("content").ifBlank { "Webpage Media" }
            }
        }
        debugBuilder.appendLine("Extracted Page Title: $pageTitle")

        // Discover global poster / thumbnail for this page (from og:image / twitter:image)
        val globalPageThumbnail = extractGlobalPageThumbnail(document, finalUrl)
        if (!globalPageThumbnail.isNullOrBlank()) {
            debugBuilder.appendLine("Page Thumbnail Detected: $globalPageThumbnail")
        }

        val rawCandidates = mutableListOf<CandidateMedia>()
        val seenNormalizedUrls = mutableSetOf<String>()

        fun queueCandidate(
            rawMediaUrl: String?,
            sourceType: String,
            category: MediaCategory,
            prefName: String? = null,
            customPoster: String? = null,
            resolution: String? = null
        ) {
            if (rawMediaUrl.isNullOrBlank()) return
            val resolved = resolveUrl(finalUrl, rawMediaUrl.trim()) ?: return
            val normalized = normalizeUrlForDedup(resolved)

            if (seenNormalizedUrls.contains(normalized)) return
            if (isExcludedUrl(resolved)) return

            seenNormalizedUrls.add(normalized)
            rawCandidates.add(
                CandidateMedia(
                    rawUrl = resolved,
                    sourceType = sourceType,
                    categoryHint = category,
                    preferredName = prefName,
                    customPosterUrl = customPoster ?: globalPageThumbnail,
                    resolutionHint = resolution
                )
            )
        }

        // 1. EXTRACT VIDEOS (Top Priority)
        // A. OpenGraph & Twitter Video tags
        val ogVideoSecure = document.select("meta[property=og:video:secure_url]").attr("content")
        val ogVideoUrl = document.select("meta[property=og:video:url]").attr("content")
        val ogVideo = document.select("meta[property=og:video]").attr("content")
        val twitterStream = document.select("meta[name=twitter:player:stream]").attr("content")
        val twitterPlayer = document.select("meta[name=twitter:player]").attr("content")

        if (ogVideoSecure.isNotBlank()) queueCandidate(ogVideoSecure, "OpenGraph Secure Video (og:video:secure_url)", MediaCategory.VIDEO)
        if (ogVideoUrl.isNotBlank()) queueCandidate(ogVideoUrl, "OpenGraph Video (og:video:url)", MediaCategory.VIDEO)
        if (ogVideo.isNotBlank()) queueCandidate(ogVideo, "OpenGraph Video (og:video)", MediaCategory.VIDEO)
        if (twitterStream.isNotBlank()) queueCandidate(twitterStream, "Twitter Video Stream (twitter:player:stream)", MediaCategory.VIDEO)
        if (twitterPlayer.isNotBlank() && isMediaStreamUrl(twitterPlayer)) queueCandidate(twitterPlayer, "Twitter Video Player", MediaCategory.VIDEO)

        // B. HTML5 <video> tags and child <source> tags
        document.select("video").forEach { videoTag ->
            val poster = videoTag.attr("poster").ifBlank { null }?.let { resolveUrl(finalUrl, it) }
            val videoSrc = videoTag.attr("src").ifBlank {
                videoTag.attr("data-src").ifBlank {
                    videoTag.attr("data-video-url").ifBlank { videoTag.attr("data-url") }
                }
            }
            if (videoSrc.isNotBlank()) {
                queueCandidate(videoSrc, "HTML5 <video> Tag", MediaCategory.VIDEO, customPoster = poster)
            }

            videoTag.select("source").forEach { sourceTag ->
                val src = sourceTag.attr("src").ifBlank { sourceTag.attr("data-src") }
                if (src.isNotBlank()) {
                    val type = sourceTag.attr("type")
                    val label = if (type.isNotBlank()) "HTML5 <source> ($type)" else "HTML5 <source> Video"
                    queueCandidate(src, label, MediaCategory.VIDEO, customPoster = poster)
                }
            }
        }

        // C. JSON-LD Structured Data for VideoObject / MediaObject
        extractJsonLdMedia(document, finalUrl).forEach { jsonLdMedia ->
            queueCandidate(
                jsonLdMedia.rawUrl,
                jsonLdMedia.sourceType,
                jsonLdMedia.categoryHint,
                customPoster = jsonLdMedia.customPosterUrl
            )
        }

        // 2. EXTRACT AUDIOS (2nd Priority)
        val ogAudio = document.select("meta[property=og:audio]").attr("content").ifBlank {
            document.select("meta[property=og:audio:secure_url]").attr("content")
        }
        if (ogAudio.isNotBlank()) queueCandidate(ogAudio, "OpenGraph Audio (og:audio)", MediaCategory.AUDIO)

        document.select("audio").forEach { audioTag ->
            val audioSrc = audioTag.attr("src").ifBlank { audioTag.attr("data-src") }
            if (audioSrc.isNotBlank()) {
                queueCandidate(audioSrc, "HTML5 <audio> Tag", MediaCategory.AUDIO)
            }
            audioTag.select("source").forEach { sourceTag ->
                val src = sourceTag.attr("src").ifBlank { sourceTag.attr("data-src") }
                if (src.isNotBlank()) {
                    val type = sourceTag.attr("type")
                    val label = if (type.isNotBlank()) "HTML5 <source> ($type)" else "HTML5 <source> Audio"
                    queueCandidate(src, label, MediaCategory.AUDIO)
                }
            }
        }

        // 3. EXTRACT DOWNLOADABLE DOCUMENT & ARCHIVE LINKS (3rd Priority)
        document.select("a[href]").forEach { aTag ->
            val href = aTag.attr("href")
            if (isDirectDownloadLink(href)) {
                val linkText = aTag.text().trim()
                val ext = href.substringBefore("?").substringAfterLast(".", "bin")
                val prefName = if (linkText.isNotBlank() && linkText.length <= 60) "$linkText.$ext" else null
                val cat = MediaCategory.fromMimeType(FileUtils.getMimeTypeFromExtension(ext), href)
                queueCandidate(href, "Downloadable Document ($ext)", cat, prefName)
            }
        }

        // 4. EXTRACT IMAGES & POSTERS (4th Priority)
        val ogImageSecure = document.select("meta[property=og:image:secure_url]").attr("content")
        val ogImage = document.select("meta[property=og:image]").attr("content")
        val twitterImage = document.select("meta[name=twitter:image]").attr("content").ifBlank {
            document.select("meta[name=twitter:image:src]").attr("content")
        }
        val linkImageSrc = document.select("link[rel=image_src]").attr("href")

        if (ogImageSecure.isNotBlank()) queueCandidate(ogImageSecure, "OpenGraph Secure Image (og:image:secure_url)", MediaCategory.IMAGE)
        if (ogImage.isNotBlank()) queueCandidate(ogImage, "OpenGraph Image (og:image)", MediaCategory.IMAGE)
        if (twitterImage.isNotBlank()) queueCandidate(twitterImage, "Twitter Card Image", MediaCategory.IMAGE)
        if (linkImageSrc.isNotBlank()) queueCandidate(linkImageSrc, "Link Image (image_src)", MediaCategory.IMAGE)

        // Responsive Images: <img srcset>
        document.select("img[srcset], img[data-srcset]").forEach { imgTag ->
            val srcset = imgTag.attr("srcset").ifBlank { imgTag.attr("data-srcset") }
            val bestSrcsetUrl = parseBestFromSrcset(srcset)
            if (!bestSrcsetUrl.isNullOrBlank()) {
                queueCandidate(bestSrcsetUrl, "Responsive Image (srcset high-res)", MediaCategory.IMAGE)
            }
        }

        // Standard <img> tags
        document.select("img[src], img[data-src]").forEach { imgTag ->
            val src = imgTag.attr("src").ifBlank { imgTag.attr("data-src") }
            val width = imgTag.attr("width").toIntOrNull() ?: 0
            val height = imgTag.attr("height").toIntOrNull() ?: 0
            if ((width == 0 || width >= 64) && (height == 0 || height >= 64)) {
                if (src.isNotBlank() && !src.startsWith("data:") && !src.contains("pixel") && !src.contains("beacon")) {
                    queueCandidate(src, "Webpage Image (<img>)", MediaCategory.IMAGE)
                }
            }
        }

        debugBuilder.appendLine("Total candidate media URLs extracted: ${rawCandidates.size}")

        // STEP 6: Verify all candidate URLs via parallel Asynchronous OkHttp Probes (Data-Saver)
        val verifiedResources = rawCandidates.take(20).map { candidate ->
            async {
                verifyAndBuildResource(candidate, finalUrl, debugBuilder)
            }
        }.awaitAll().filterNotNull()

        if (verifiedResources.isEmpty()) {
            debugBuilder.appendLine("Failure: No public downloadable media elements were confirmed on this webpage.")
            return@withContext MediaAnalysisResult(
                originalUrl = trimmedUrl,
                finalUrl = finalUrl,
                httpStatus = statusCode,
                contentType = contentType,
                contentLength = contentLength,
                isWebPage = true,
                pageTitle = pageTitle,
                resources = emptyList(),
                isSuccess = false,
                errorMessage = "No public downloadable media was exposed by this webpage. (The webpage does not contain accessible video, audio, image, or document files).",
                debugLog = debugBuilder.toString()
            )
        }

        // STEP 7: Structured Classification & Mislabeled Thumbnail Prevention
        val hasPlayableOrDirectFiles = verifiedResources.any { it.category != MediaCategory.IMAGE }
        val isFallbackThumbnailOnly = !hasPlayableOrDirectFiles && isWebpage

        val finalResourceList = if (isFallbackThumbnailOnly) {
            debugBuilder.appendLine("Notice: Only webpage thumbnail/preview images found. Tagging with clear thumbnail notice.")
            verifiedResources.map { res ->
                res.copy(
                    isThumbnailFallback = true,
                    sourceType = "Webpage Thumbnail",
                    notice = "Webpage thumbnail discovered (Video stream is protected/restricted)"
                )
            }.sortedByDescending { it.sizeBytes }
        } else {
            // Prioritize: Video (1st) > Audio (2nd) > Documents/Files (3rd) > Images (4th)
            verifiedResources.sortedWith(
                compareBy<DiscoveredResource> { res ->
                    when (res.category) {
                        MediaCategory.VIDEO -> 1
                        MediaCategory.AUDIO -> 2
                        MediaCategory.DOCUMENT -> 3
                        MediaCategory.OTHER -> 4
                        MediaCategory.IMAGE -> 5
                        else -> 6
                    }
                }.thenByDescending { it.sizeBytes }
            )
        }

        debugBuilder.appendLine("Verification Complete: ${finalResourceList.size} valid resources discovered.")

        return@withContext MediaAnalysisResult(
            originalUrl = trimmedUrl,
            finalUrl = finalUrl,
            httpStatus = statusCode,
            contentType = contentType,
            contentLength = contentLength,
            isWebPage = true,
            pageTitle = pageTitle,
            resources = finalResourceList,
            isSuccess = true,
            isFallbackThumbnailOnly = isFallbackThumbnailOnly,
            warningNotice = if (isFallbackThumbnailOnly) "Webpage thumbnail discovered (Video stream is protected/restricted)" else null,
            errorMessage = null,
            debugLog = debugBuilder.toString()
        )
    }

    private fun executeFastDirectInspection(
        url: String,
        debugBuilder: StringBuilder
    ): MediaAnalysisResult? {
        var resp: Response? = null
        try {
            debugBuilder.appendLine("Dispatching lightweight OkHttp HEAD probe with browser headers...")
            val headRequest = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", ACCEPT_HEADER)
                .header("Accept-Language", ACCEPT_LANGUAGE)
                .header("Sec-Ch-Ua", SEC_CH_UA)
                .header("Sec-Ch-Ua-Mobile", "?0")
                .header("Sec-Ch-Ua-Platform", SEC_CH_UA_PLATFORM)
                .head()
                .build()

            resp = okHttpClient.newCall(headRequest).execute()

            // If 403 Forbidden, 405 Method Not Allowed, or non-success on HEAD (e.g. Google Storage / CDNs), fallback to GET Range 0-1024
            if (!resp.isSuccessful || resp.code == 403 || resp.code == 405) {
                debugBuilder.appendLine("Fast Path: HEAD returned ${resp.code}. Fallback to GET Range 0-1024...")
                resp.close()
                val rangeRequest = Request.Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", ACCEPT_HEADER)
                    .header("Accept-Language", ACCEPT_LANGUAGE)
                    .header("Sec-Ch-Ua", SEC_CH_UA)
                    .header("Sec-Ch-Ua-Mobile", "?0")
                    .header("Sec-Ch-Ua-Platform", SEC_CH_UA_PLATFORM)
                    .header("Range", "bytes=0-1024")
                    .get()
                    .build()
                resp = okHttpClient.newCall(rangeRequest).execute()
            }

            if (!resp.isSuccessful && resp.code != 206) {
                debugBuilder.appendLine("Fast Path response returned HTTP ${resp.code}")
                return null
            }

            val finalUrl = resp.request.url.toString()
            val rawContentType = resp.header("Content-Type") ?: ""
            val rawContentDisp = resp.header("Content-Disposition")
            var contentType = rawContentType.lowercase().substringBefore(";").trim()
            var contentLength = resp.header("Content-Length")?.toLongOrNull() ?: 0L

            val contentRange = resp.header("Content-Range")
            if (contentRange != null) {
                val totalStr = contentRange.substringAfterLast("/").trim()
                val totalVal = totalStr.toLongOrNull()
                if (totalVal != null && totalVal > 0L) {
                    contentLength = totalVal
                }
            }

            val fileName = extractFileName(finalUrl, rawContentDisp, contentType)
            if (contentType.isBlank() || contentType == "application/octet-stream") {
                val ext = fileName.substringAfterLast('.', "")
                contentType = FileUtils.getMimeTypeFromExtension(ext)
            }

            val category = MediaCategory.fromMimeType(contentType, fileName)
            val isApk = fileName.endsWith(".apk", ignoreCase = true) || contentType.contains("android.package-archive")

            val sourceLabel = when (category) {
                MediaCategory.IMAGE -> "Direct Image Endpoint"
                MediaCategory.VIDEO -> "Direct Video Stream"
                MediaCategory.AUDIO -> "Direct Audio Stream"
                MediaCategory.DOCUMENT -> "Direct Document"
                else -> if (isApk) "Direct Android Package (APK)" else "Direct Download File"
            }

            debugBuilder.appendLine("Fast Path Success: $sourceLabel | $fileName ($contentLength bytes)")

            val directResource = DiscoveredResource(
                id = UUID.randomUUID().toString(),
                directUrl = finalUrl,
                pageUrl = url,
                title = fileName,
                fileName = fileName,
                mimeType = contentType,
                category = category,
                sizeBytes = contentLength,
                sourceType = sourceLabel,
                previewUrl = if (category == MediaCategory.IMAGE) finalUrl else null,
                isDirect = true,
                isSecurityRisky = isApk
            )

            return MediaAnalysisResult(
                originalUrl = url,
                finalUrl = finalUrl,
                httpStatus = resp.code,
                contentType = contentType,
                contentLength = contentLength,
                isWebPage = false,
                pageTitle = fileName,
                resources = listOf(directResource),
                isSuccess = true,
                isFallbackThumbnailOnly = false,
                errorMessage = null,
                debugLog = debugBuilder.toString()
            )
        } catch (e: Exception) {
            debugBuilder.appendLine("Fast Path Exception: ${e.message}")
            return null
        } finally {
            runCatching { resp?.close() }
        }
    }

    private fun buildDirectResourceResult(
        originalUrl: String,
        finalUrl: String,
        statusCode: Int,
        contentType: String,
        contentLength: Long,
        contentDisposition: String?,
        debugBuilder: StringBuilder
    ): MediaAnalysisResult {
        debugBuilder.appendLine("Direct Download Stream confirmed for $contentType")
        val fileName = extractFileName(finalUrl, contentDisposition, contentType)
        val category = MediaCategory.fromMimeType(contentType, fileName)
        val isApk = fileName.endsWith(".apk", ignoreCase = true) || contentType.contains("android.package-archive")

        val sourceLabel = when (category) {
            MediaCategory.IMAGE -> "Direct Image Endpoint"
            MediaCategory.VIDEO -> "Direct Video Stream"
            MediaCategory.AUDIO -> "Direct Audio Stream"
            MediaCategory.DOCUMENT -> "Direct Document"
            else -> if (isApk) "Direct Android Package (APK)" else "Direct Download File"
        }

        val directResource = DiscoveredResource(
            id = UUID.randomUUID().toString(),
            directUrl = finalUrl,
            pageUrl = originalUrl,
            title = fileName,
            fileName = fileName,
            mimeType = contentType.ifBlank { "application/octet-stream" },
            category = category,
            sizeBytes = contentLength,
            sourceType = sourceLabel,
            previewUrl = if (category == MediaCategory.IMAGE) finalUrl else null,
            isDirect = true,
            isSecurityRisky = isApk
        )

        return MediaAnalysisResult(
            originalUrl = originalUrl,
            finalUrl = finalUrl,
            httpStatus = statusCode,
            contentType = contentType,
            contentLength = contentLength,
            isWebPage = false,
            pageTitle = fileName,
            resources = listOf(directResource),
            isSuccess = true,
            isFallbackThumbnailOnly = false,
            errorMessage = null,
            debugLog = debugBuilder.toString()
        )
    }

    private fun extractGlobalPageThumbnail(doc: Document, baseUrl: String): String? {
        val og = doc.select("meta[property=og:image:secure_url]").attr("content").ifBlank {
            doc.select("meta[property=og:image]").attr("content").ifBlank {
                doc.select("meta[name=twitter:image]").attr("content").ifBlank {
                    doc.select("link[rel=image_src]").attr("href")
                }
            }
        }
        return if (og.isNotBlank()) resolveUrl(baseUrl, og) else null
    }

    private fun extractJsonLdMedia(doc: Document, baseUrl: String): List<CandidateMedia> {
        val result = mutableListOf<CandidateMedia>()
        doc.select("script[type=application/ld+json]").forEach { scriptTag ->
            val json = scriptTag.html()
            if (json.contains("contentUrl", ignoreCase = true) || json.contains("VideoObject", ignoreCase = true)) {
                val contentUrlMatch = Pattern.compile("\"contentUrl\"\\s*:\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE).matcher(json)
                val thumbMatch = Pattern.compile("\"thumbnailUrl\"\\s*:\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE).matcher(json)
                val thumbStr = if (thumbMatch.find()) thumbMatch.group(1) else null
                val thumb: String? = if (!thumbStr.isNullOrBlank()) resolveUrl(baseUrl, thumbStr) else null

                while (contentUrlMatch.find()) {
                    val raw = contentUrlMatch.group(1)
                    if (!raw.isNullOrBlank()) {
                        val resolved = resolveUrl(baseUrl, raw)
                        if (resolved != null) {
                            result.add(
                                CandidateMedia(
                                    rawUrl = resolved,
                                    sourceType = "JSON-LD Video Object",
                                    categoryHint = MediaCategory.VIDEO,
                                    customPosterUrl = thumb
                                )
                            )
                        }
                    }
                }
            }
        }
        return result
    }

    private fun verifyAndBuildResource(
        candidate: CandidateMedia,
        pageUrl: String,
        debug: StringBuilder
    ): DiscoveredResource? {
        var resp: Response? = null
        var verifiedMime = ""
        var verifiedLength = 0L
        var finalMediaUrl = candidate.rawUrl

        try {
            val headReq = Request.Builder()
                .url(candidate.rawUrl)
                .header("User-Agent", USER_AGENT)
                .header("Accept", ACCEPT_HEADER)
                .header("Accept-Language", ACCEPT_LANGUAGE)
                .header("Sec-Ch-Ua", SEC_CH_UA)
                .header("Sec-Ch-Ua-Mobile", "?0")
                .header("Sec-Ch-Ua-Platform", SEC_CH_UA_PLATFORM)
                .header("Referer", pageUrl)
                .head()
                .build()

            resp = okHttpClient.newCall(headReq).execute()

            // If HEAD is rejected with 403 Forbidden, 405 Method Not Allowed or non-success, fallback to GET Range 0-1024
            if (!resp.isSuccessful || resp.code == 403 || resp.code == 405) {
                resp.close()
                val rangeReq = Request.Builder()
                    .url(candidate.rawUrl)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", ACCEPT_HEADER)
                    .header("Accept-Language", ACCEPT_LANGUAGE)
                    .header("Sec-Ch-Ua", SEC_CH_UA)
                    .header("Sec-Ch-Ua-Mobile", "?0")
                    .header("Sec-Ch-Ua-Platform", SEC_CH_UA_PLATFORM)
                    .header("Referer", pageUrl)
                    .header("Range", "bytes=0-1024")
                    .get()
                    .build()
                resp = okHttpClient.newCall(rangeReq).execute()
            }

            if (!resp.isSuccessful && resp.code != 206) {
                return null
            }

            finalMediaUrl = resp.request.url.toString()
            val rawType = resp.header("Content-Type") ?: ""
            verifiedMime = rawType.lowercase().substringBefore(";").trim()
            verifiedLength = resp.header("Content-Length")?.toLongOrNull() ?: 0L

            // If Content-Range is present, parse total size
            val contentRange = resp.header("Content-Range")
            if (contentRange != null) {
                val totalStr = contentRange.substringAfterLast("/").trim()
                val totalVal = totalStr.toLongOrNull()
                if (totalVal != null && totalVal > 0L) {
                    verifiedLength = totalVal
                }
            }

            // Exclude HTML/text or empty resources masquerading as media
            if (verifiedMime.contains("html") || verifiedMime.contains("javascript") || verifiedMime.contains("css")) {
                return null
            }

        } catch (e: Exception) {
            // If offline/unreachable, keep candidate if extension is definitely media
            val ext = candidate.rawUrl.substringBefore("?").substringAfterLast(".", "").lowercase()
            if (ext !in listOf("mp4", "webm", "mkv", "mp3", "m4a", "jpg", "png", "pdf", "zip", "apk")) {
                return null
            }
            verifiedMime = FileUtils.getMimeTypeFromExtension(ext)
        } finally {
            runCatching { resp?.close() }
        }

        val resolvedFileName = candidate.preferredName ?: extractFileName(finalMediaUrl, null, verifiedMime)
        val finalCategory = if (verifiedMime.isNotBlank() && verifiedMime != "application/octet-stream") {
            MediaCategory.fromMimeType(verifiedMime, resolvedFileName)
        } else {
            candidate.categoryHint
        }

        val isApk = resolvedFileName.endsWith(".apk", ignoreCase = true) || verifiedMime.contains("android.package-archive")

        val previewUrl = when (finalCategory) {
            MediaCategory.IMAGE -> finalMediaUrl
            MediaCategory.VIDEO -> candidate.customPosterUrl
            else -> null
        }

        return DiscoveredResource(
            id = UUID.randomUUID().toString(),
            directUrl = finalMediaUrl,
            pageUrl = pageUrl,
            title = resolvedFileName,
            fileName = resolvedFileName,
            mimeType = verifiedMime.ifBlank { "application/octet-stream" },
            category = finalCategory,
            sizeBytes = verifiedLength,
            sourceType = candidate.sourceType,
            resolution = candidate.resolutionHint,
            previewUrl = previewUrl,
            isDirect = false,
            isSecurityRisky = isApk
        )
    }

    private fun parseBestFromSrcset(srcset: String): String? {
        if (srcset.isBlank()) return null
        return runCatching {
            val entries = srcset.split(",").map { it.trim() }.filter { it.isNotBlank() }
            var bestUrl: String? = null
            var bestWidth = 0

            for (entry in entries) {
                val parts = entry.split("\\s+".toRegex())
                val url = parts.getOrNull(0) ?: continue
                val descriptor = parts.getOrNull(1) ?: "1x"
                val width = if (descriptor.endsWith("w")) {
                    descriptor.removeSuffix("w").toIntOrNull() ?: 1
                } else if (descriptor.endsWith("x")) {
                    ((descriptor.removeSuffix("x").toFloatOrNull() ?: 1f) * 1000).toInt()
                } else 1

                if (width >= bestWidth) {
                    bestWidth = width
                    bestUrl = url
                }
            }
            bestUrl ?: entries.lastOrNull()?.split("\\s+".toRegex())?.getOrNull(0)
        }.getOrNull()
    }

    private fun resolveUrl(baseUrl: String, relativeUrl: String): String? {
        if (relativeUrl.isBlank()) return null
        if (relativeUrl.startsWith("http://", ignoreCase = true) || relativeUrl.startsWith("https://", ignoreCase = true)) {
            return relativeUrl
        }
        if (relativeUrl.startsWith("//")) {
            val scheme = if (baseUrl.startsWith("https://", ignoreCase = true)) "https:" else "http:"
            return "$scheme$relativeUrl"
        }
        return runCatching {
            val baseUri = URI(baseUrl)
            baseUri.resolve(relativeUrl).toString()
        }.getOrNull() ?: runCatching {
            URL(URL(baseUrl), relativeUrl).toString()
        }.getOrNull()
    }

    private fun normalizeUrlForDedup(url: String): String {
        return url.substringBefore("#").trim()
    }

    private fun isExcludedUrl(url: String): Boolean {
        val lower = url.lowercase()
        val excludedDomains = listOf(
            "facebook.com/tr", "google-analytics.com", "googletagmanager.com",
            "doubleclick.net", "gravatar.com/avatar", "twitter.com/intent",
            "facebook.com/sharer", "linkedin.com/share", "pinterest.com/pin"
        )
        if (excludedDomains.any { lower.contains(it) }) return true
        if (lower.contains("favicon.ico") || lower.contains("/avatar/") || lower.contains("1x1.gif") || lower.contains("pixel.gif")) return true
        if (lower.startsWith("javascript:") || lower.startsWith("mailto:") || lower.startsWith("tel:") || lower.startsWith("data:")) return true
        return false
    }

    private fun isMediaStreamUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.endsWith(".mp4") || lower.endsWith(".webm") || lower.endsWith(".mkv") ||
                lower.endsWith(".mov") || lower.endsWith(".m4v") || lower.contains("video") ||
                lower.contains(".m3u8") || lower.contains(".mpd")
    }

    private fun isDirectDownloadLink(href: String): Boolean {
        val ext = href.substringBefore("?").substringAfterLast(".", "").lowercase()
        return ext in DIRECT_DOC_EXTS || ext in DIRECT_VIDEO_EXTS || ext in DIRECT_AUDIO_EXTS || ext in DIRECT_IMAGE_EXTS
    }

    private fun isHtmlMimeType(contentType: String): Boolean {
        val mime = contentType.lowercase()
        return mime.contains("html") || mime.contains("xhtml") || mime.contains("xml")
    }

    private fun isDownloadableMimeType(contentType: String, url: String): Boolean {
        val mime = contentType.lowercase()
        if (mime.startsWith("video/") || mime.startsWith("audio/") || mime.startsWith("image/")) return true
        if (mime == "application/pdf" || mime == "application/zip" || mime == "application/octet-stream" ||
            mime == "application/x-rar-compressed" || mime == "application/vnd.android.package-archive" ||
            mime.contains("document") || mime.contains("sheet") || mime.contains("presentation")) return true
        return isDirectFileExtension(url)
    }

    private fun isProtectedPlatform(host: String): Boolean {
        val protectedHosts = listOf(
            "netflix.com", "spotify.com", "hulu.com", "disneyplus.com",
            "primevideo.com", "appletv.apple.com", "hbomax.com", "max.com"
        )
        return protectedHosts.any { host.contains(it) }
    }

    private fun isDirectFileExtension(url: String): Boolean {
        val ext = url.substringBefore("?").substringBefore("#").substringAfterLast('.', "").lowercase()
        return ext in DIRECT_IMAGE_EXTS || ext in DIRECT_VIDEO_EXTS || ext in DIRECT_AUDIO_EXTS || ext in DIRECT_DOC_EXTS
    }

    private fun extractFileName(url: String, contentDisposition: String?, contentType: String): String {
        if (!contentDisposition.isNullOrBlank()) {
            val cdMatch = Pattern.compile("filename\\*?=['\"]?(?:UTF-8'')?([^;'\"]+)", Pattern.CASE_INSENSITIVE)
                .matcher(contentDisposition)
            if (cdMatch.find()) {
                val extracted = cdMatch.group(1)?.trim()
                if (!extracted.isNullOrBlank()) {
                    return FileUtils.sanitizeFileName(URLDecoder.decode(extracted, "UTF-8"))
                }
            }
        }

        val uri = runCatching { Uri.parse(url) }.getOrNull()
        val lastPathSegment = uri?.lastPathSegment
        if (!lastPathSegment.isNullOrBlank() && lastPathSegment.contains(".")) {
            return FileUtils.sanitizeFileName(URLDecoder.decode(lastPathSegment, "UTF-8"))
        }

        val ext = FileUtils.getExtensionFromMimeType(contentType)
        return "download_${System.currentTimeMillis()}.$ext"
    }
}
