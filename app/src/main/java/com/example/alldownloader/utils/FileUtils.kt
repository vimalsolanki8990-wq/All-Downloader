package com.example.alldownloader.utils

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.alldownloader.data.model.MediaCategory
import java.io.File
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FileUtils {

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        val format = DecimalFormat("#,##0.#")
        val index = digitGroups.coerceIn(0, units.size - 1)
        return "${format.format(bytes / Math.pow(1024.0, index.toDouble()))} ${units[index]}"
    }

    fun formatSpeed(bytesPerSec: Long): String {
        if (bytesPerSec <= 0) return "0 KB/s"
        return "${formatFileSize(bytesPerSec)}/s"
    }

    fun formatDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    fun sanitizeFileName(name: String): String {
        var clean = name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
        if (clean.isBlank()) clean = "download_${System.currentTimeMillis()}"
        return clean
    }

    fun getMimeTypeFromExtension(extension: String): String {
        val ext = extension.lowercase().removePrefix(".")
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: when (ext) {
            "mp4" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "mov" -> "video/quicktime"
            "m4v" -> "video/x-m4v"
            "avi" -> "video/x-msvideo"
            "flv" -> "video/x-flv"
            "3gp" -> "video/3gpp"
            "ts" -> "video/mp2t"
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            "aac" -> "audio/aac"
            "wav" -> "audio/wav"
            "flac" -> "audio/flac"
            "ogg", "oga" -> "audio/ogg"
            "opus" -> "audio/opus"
            "wma" -> "audio/x-ms-wma"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "svg" -> "image/svg+xml"
            "bmp" -> "image/bmp"
            "ico" -> "image/x-icon"
            "pdf" -> "application/pdf"
            "apk" -> "application/vnd.android.package-archive"
            "zip" -> "application/zip"
            "rar" -> "application/x-rar-compressed"
            "7z" -> "application/x-7z-compressed"
            "tar" -> "application/x-tar"
            "gz" -> "application/gzip"
            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "xls" -> "application/vnd.ms-excel"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "ppt" -> "application/vnd.ms-powerpoint"
            "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            "txt" -> "text/plain"
            "csv" -> "text/csv"
            else -> "application/octet-stream"
        }
    }

    fun getExtensionFromMimeType(mimeType: String): String {
        val mime = mimeType.lowercase().substringBefore(";").trim()
        val extFromMap = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
        if (!extFromMap.isNullOrBlank()) return extFromMap

        return when {
            mime == "video/mp4" -> "mp4"
            mime == "video/webm" -> "webm"
            mime == "video/quicktime" -> "mov"
            mime == "video/x-matroska" -> "mkv"
            mime == "video/x-m4v" -> "m4v"
            mime == "audio/mpeg" -> "mp3"
            mime == "audio/mp4" -> "m4a"
            mime == "audio/aac" -> "aac"
            mime == "audio/wav" || mime == "audio/x-wav" -> "wav"
            mime == "audio/flac" || mime == "audio/x-flac" -> "flac"
            mime == "audio/ogg" || mime == "audio/opus" -> "ogg"
            mime == "image/jpeg" -> "jpg"
            mime == "image/png" -> "png"
            mime == "image/webp" -> "webp"
            mime == "image/gif" -> "gif"
            mime == "image/svg+xml" -> "svg"
            mime == "image/bmp" -> "bmp"
            mime == "application/pdf" -> "pdf"
            mime == "application/vnd.android.package-archive" -> "apk"
            mime == "application/zip" -> "zip"
            mime == "application/x-rar-compressed" -> "rar"
            mime == "text/plain" -> "txt"
            mime == "text/csv" -> "csv"
            else -> "bin"
        }
    }

    fun getDownloadDirectory(context: Context, category: MediaCategory = MediaCategory.ALL): File {
        val targetEnvironmentDir = when (category) {
            MediaCategory.VIDEO -> Environment.DIRECTORY_MOVIES
            MediaCategory.AUDIO -> Environment.DIRECTORY_MUSIC
            MediaCategory.IMAGE -> Environment.DIRECTORY_PICTURES
            else -> Environment.DIRECTORY_DOWNLOADS
        }

        val baseDir: File = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.getExternalFilesDir(targetEnvironmentDir) ?: context.filesDir
            } else {
                Environment.getExternalStoragePublicDirectory(targetEnvironmentDir)
                    ?: context.getExternalFilesDir(targetEnvironmentDir)
                    ?: context.filesDir
            }
        } catch (e: Exception) {
            context.filesDir
        }

        val allDownDir = File(baseDir, "AllDown")
        if (!allDownDir.exists()) {
            allDownDir.mkdirs()
        }
        return allDownDir
    }

    fun getUniqueFile(directory: File, baseName: String, extension: String): File {
        if (!directory.exists()) {
            directory.mkdirs()
        }
        var cleanBase = sanitizeFileName(baseName)
        val cleanExt = extension.removePrefix(".")
        var target = File(directory, if (cleanExt.isNotBlank()) "$cleanBase.$cleanExt" else cleanBase)
        var count = 1
        while (target.exists()) {
            target = File(directory, if (cleanExt.isNotBlank()) "${cleanBase}_($count).$cleanExt" else "${cleanBase}_($count)")
            count++
        }
        return target
    }

    fun scanFileIntoMediaStore(context: Context, file: File, mimeType: String) {
        if (!file.exists()) return
        try {
            MediaScannerConnection.scanFile(
                context.applicationContext,
                arrayOf(file.absolutePath),
                arrayOf(mimeType.ifBlank { "*/*" }),
                null
            )
        } catch (e: Exception) {
            // Safe fallback if MediaScannerConnection encounters an error
        }
    }

    fun openFile(context: Context, filePath: String, mimeType: String) {
        val file = File(filePath)
        if (!file.exists()) {
            Toast.makeText(context, "File not found on storage: ${file.name}", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType.ifBlank { "*/*" })
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Open with"))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "No app found to open this file type.", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Unable to open file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareFile(context: Context, filePath: String, mimeType: String) {
        val file = File(filePath)
        if (!file.exists()) {
            Toast.makeText(context, "File does not exist: ${file.name}", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType.ifBlank { "*/*" }
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, file.name)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share file via"))
        } catch (e: Exception) {
            Toast.makeText(context, "Unable to share file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun deleteFileFromStorage(filePath: String): Boolean {
        val file = File(filePath)
        return if (file.exists()) file.delete() else true
    }

    fun renameFile(filePath: String, newNameWithoutExt: String): File? {
        val oldFile = File(filePath)
        if (!oldFile.exists()) return null
        val ext = oldFile.extension
        val parent = oldFile.parentFile ?: return null
        val cleanName = sanitizeFileName(newNameWithoutExt)
        val newFile = File(parent, if (ext.isNotBlank()) "$cleanName.$ext" else cleanName)
        return if (oldFile.renameTo(newFile)) newFile else null
    }
}
