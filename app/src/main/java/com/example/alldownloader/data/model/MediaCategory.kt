package com.example.alldownloader.data.model

import com.example.alldownloader.R

enum class MediaCategory(val displayNameRes: Int, val iconRes: Int, val colorRes: Int, val bgRes: Int) {
    ALL(R.string.cat_all, R.drawable.ic_folder, R.color.primary, R.drawable.bg_badge_other),
    VIDEO(R.string.cat_video_title, R.drawable.ic_video, R.color.cat_video, R.drawable.bg_badge_video),
    AUDIO(R.string.cat_audio_title, R.drawable.ic_audio, R.color.cat_audio, R.drawable.bg_badge_audio),
    IMAGE(R.string.cat_image_title, R.drawable.ic_image, R.color.cat_image, R.drawable.bg_badge_image),
    DOCUMENT(R.string.cat_documents_title, R.drawable.ic_document, R.color.cat_doc, R.drawable.bg_badge_doc),
    OTHER(R.string.cat_other_title, R.drawable.ic_apk, R.color.cat_other, R.drawable.bg_badge_other);

    companion object {
        fun fromMimeType(mimeType: String?, fileName: String? = null): MediaCategory {
            val mime = mimeType?.lowercase() ?: ""
            val ext = fileName?.substringAfterLast('.', "")?.lowercase() ?: ""

            return when {
                mime.startsWith("video/") || ext in listOf("mp4", "mkv", "webm", "avi", "mov", "flv", "3gp", "ts", "m4v") -> VIDEO
                mime.startsWith("audio/") || ext in listOf("mp3", "m4a", "aac", "wav", "flac", "ogg", "opus", "wma") -> AUDIO
                mime.startsWith("image/") || ext in listOf("jpg", "jpeg", "png", "webp", "gif", "svg", "bmp", "ico", "tiff") -> IMAGE
                mime.contains("pdf") || mime.contains("document") || mime.contains("sheet") || mime.contains("presentation") ||
                        mime.contains("text") || mime.contains("zip") || mime.contains("rar") || mime.contains("tar") || mime.contains("7z") ||
                        ext in listOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv", "zip", "rar", "7z", "tar", "gz") -> DOCUMENT
                ext == "apk" || mime.contains("android.package-archive") -> OTHER
                else -> OTHER
            }
        }
    }
}
