package com.example.alldownloader

import com.example.alldownloader.data.model.MediaCategory
import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test
import java.net.URI

class UrlAnalyzerUnitTest {

    @Test
    fun testOpenGraphImageDiscovery() {
        val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <title>Beautiful Mountain Landscape</title>
                <meta property="og:title" content="Mountain Sunset" />
                <meta property="og:image" content="https://example.com/images/sunset_hd.jpg" />
                <meta property="og:image:secure_url" content="https://example.com/images/sunset_4k.jpg" />
                <meta name="twitter:image" content="https://example.com/images/sunset_card.jpg" />
            </head>
            <body>
                <h1>Welcome to Mountain Views</h1>
            </body>
            </html>
        """.trimIndent()

        val doc = Jsoup.parse(html, "https://example.com/page/mountain")
        val ogImageSecure = doc.select("meta[property=og:image:secure_url]").attr("content")
        val ogImage = doc.select("meta[property=og:image]").attr("content")
        val twitterImage = doc.select("meta[name=twitter:image]").attr("content")

        assertEquals("https://example.com/images/sunset_4k.jpg", ogImageSecure)
        assertEquals("https://example.com/images/sunset_hd.jpg", ogImage)
        assertEquals("https://example.com/images/sunset_card.jpg", twitterImage)
    }

    @Test
    fun testHtml5VideoDiscovery() {
        val html = """
            <!DOCTYPE html>
            <html>
            <head><title>Video Demo</title></head>
            <body>
                <video controls poster="/posters/intro.jpg">
                    <source src="/videos/intro_1080p.mp4" type="video/mp4">
                    <source src="/videos/intro_720p.webm" type="video/webm">
                </video>
            </body>
            </html>
        """.trimIndent()

        val doc = Jsoup.parse(html, "https://mywebsite.com/videos/watch")
        val baseUri = URI("https://mywebsite.com/videos/watch")
        val sources = doc.select("video source")

        assertEquals(2, sources.size)
        val source1 = baseUri.resolve(sources[0].attr("src")).toString()
        val source2 = baseUri.resolve(sources[1].attr("src")).toString()

        assertEquals("https://mywebsite.com/videos/intro_1080p.mp4", source1)
        assertEquals("https://mywebsite.com/videos/intro_720p.webm", source2)
    }

    @Test
    fun testSrcsetHighestResolutionParser() {
        val srcset = "image-320w.jpg 320w, image-480w.jpg 480w, image-1920w.jpg 1920w, image-800w.jpg 800w"
        val entries = srcset.split(",").map { it.trim() }.filter { it.isNotBlank() }
        var bestUrl: String? = null
        var bestWidth = 0

        for (entry in entries) {
            val parts = entry.split("\\s+".toRegex())
            val url = parts[0]
            val descriptor = parts.getOrNull(1) ?: "1x"
            val width = if (descriptor.endsWith("w")) descriptor.removeSuffix("w").toIntOrNull() ?: 1 else 1
            if (width >= bestWidth) {
                bestWidth = width
                bestUrl = url
            }
        }

        assertEquals("image-1920w.jpg", bestUrl)
    }

    @Test
    fun testDirectFileCategoryMapping() {
        assertEquals(MediaCategory.VIDEO, MediaCategory.fromMimeType("video/mp4", "sample.mp4"))
        assertEquals(MediaCategory.AUDIO, MediaCategory.fromMimeType("audio/mpeg", "track.mp3"))
        assertEquals(MediaCategory.IMAGE, MediaCategory.fromMimeType("image/jpeg", "photo.jpg"))
        assertEquals(MediaCategory.DOCUMENT, MediaCategory.fromMimeType("application/pdf", "manual.pdf"))
        assertEquals(MediaCategory.DOCUMENT, MediaCategory.fromMimeType("application/zip", "archive.zip"))
        assertEquals(MediaCategory.OTHER, MediaCategory.fromMimeType("application/vnd.android.package-archive", "app.apk"))
    }

    @Test
    fun testJsonLdVideoObjectDiscovery() {
        val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <script type="application/ld+json">
                {
                    "@context": "https://schema.org",
                    "@type": "VideoObject",
                    "name": "How to Build an App",
                    "thumbnailUrl": "https://example.com/thumb.jpg",
                    "contentUrl": "https://example.com/video/app_tutorial.mp4"
                }
                </script>
            </head>
            <body></body>
            </html>
        """.trimIndent()

        val doc = Jsoup.parse(html, "https://example.com")
        val json = doc.select("script[type=application/ld+json]").first()?.html() ?: ""
        assertTrue(json.contains("VideoObject"))
        assertTrue(json.contains("https://example.com/video/app_tutorial.mp4"))
        assertTrue(json.contains("https://example.com/thumb.jpg"))
    }

    @Test
    fun testCategoryPrioritizationOrder() {
        val categories = listOf(
            MediaCategory.IMAGE,
            MediaCategory.DOCUMENT,
            MediaCategory.VIDEO,
            MediaCategory.OTHER,
            MediaCategory.AUDIO
        )

        val sorted = categories.sortedBy { cat ->
            when (cat) {
                MediaCategory.VIDEO -> 1
                MediaCategory.AUDIO -> 2
                MediaCategory.DOCUMENT -> 3
                MediaCategory.OTHER -> 4
                MediaCategory.IMAGE -> 5
                else -> 6
            }
        }

        assertEquals(
            listOf(
                MediaCategory.VIDEO,
                MediaCategory.AUDIO,
                MediaCategory.DOCUMENT,
                MediaCategory.OTHER,
                MediaCategory.IMAGE
            ),
            sorted
        )
    }

    @Test
    fun testFastPathDirectExtensions() {
        val imageExts = listOf("jpg", "jpeg", "png", "webp", "svg", "gif")
        val videoExts = listOf("mp4", "mkv", "webm", "mov")
        val docExts = listOf("pdf", "zip", "apk", "docx")

        for (ext in imageExts) {
            val url = "https://example.com/files/photo.$ext?version=1#top"
            val cleanPath = url.substringBefore("?").substringBefore("#")
            val extractedExt = cleanPath.substringAfterLast(".", "").lowercase()
            assertEquals(ext, extractedExt)
            assertEquals(MediaCategory.IMAGE, MediaCategory.fromMimeType(null, cleanPath))
        }

        for (ext in videoExts) {
            val url = "https://example.com/stream/movie.$ext?token=abc"
            val cleanPath = url.substringBefore("?").substringBefore("#")
            val extractedExt = cleanPath.substringAfterLast(".", "").lowercase()
            assertEquals(ext, extractedExt)
            assertEquals(MediaCategory.VIDEO, MediaCategory.fromMimeType(null, cleanPath))
        }

        for (ext in docExts) {
            val url = "https://example.com/docs/file.$ext"
            val cleanPath = url.substringBefore("?").substringBefore("#")
            val extractedExt = cleanPath.substringAfterLast(".", "").lowercase()
            assertEquals(ext, extractedExt)
        }
    }

    @Test
    fun testFallbackThumbnailOnlyClassification() {
        // When only images are present on a webpage and no video was found
        val discoveredCategories = listOf(MediaCategory.IMAGE, MediaCategory.IMAGE)
        val hasPlayableOrDirectFiles = discoveredCategories.any { it != MediaCategory.IMAGE }
        val isFallbackThumbnailOnly = !hasPlayableOrDirectFiles

        assertTrue(isFallbackThumbnailOnly)

        // When video is present
        val discoveredWithVideo = listOf(MediaCategory.VIDEO, MediaCategory.IMAGE)
        val hasVideo = discoveredWithVideo.any { it != MediaCategory.IMAGE }
        assertFalse(!hasVideo)
    }
}
