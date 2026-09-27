package com.example.alldownloader.utils

import android.content.Context
import android.os.Environment
import android.os.StatFs
import com.example.alldownloader.data.db.DownloadDatabaseHelper
import com.example.alldownloader.data.model.StorageInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object StorageUtils {

    suspend fun getStorageInfo(context: Context): StorageInfo = withContext(Dispatchers.IO) {
        val path = Environment.getDataDirectory()
        val stat = StatFs(path.path)
        val blockSize = stat.blockSizeLong
        val totalBlocks = stat.blockCountLong
        val availableBlocks = stat.availableBlocksLong

        val totalBytes = totalBlocks * blockSize
        val freeBytes = availableBlocks * blockSize
        val usedBytes = totalBytes - freeBytes

        // Calculate app download folder usage
        val downloadDir = FileUtils.getDownloadDirectory(context)
        var appBytes = 0L
        var count = 0
        if (downloadDir.exists() && downloadDir.isDirectory) {
            downloadDir.walkTopDown().filter { it.isFile }.forEach {
                appBytes += it.length()
                count++
            }
        }

        StorageInfo(
            totalBytes = totalBytes,
            freeBytes = freeBytes,
            usedBytes = usedBytes,
            appDownloadsBytes = appBytes,
            downloadsCount = count
        )
    }
}
