package com.example.alldownloader.data.db

import android.content.Context
import com.example.alldownloader.data.model.DownloadItem
import com.example.alldownloader.data.model.DownloadStatus
import com.example.alldownloader.data.model.MediaCategory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class DownloadRepository(private val dbHelper: DownloadDatabaseHelper) {

    val dbChanges = dbHelper.dbChanges

    suspend fun saveDownload(item: DownloadItem) = dbHelper.insertOrUpdate(item)

    suspend fun updateProgress(
        id: String,
        downloaded: Long,
        total: Long,
        speed: Long,
        status: DownloadStatus
    ) = dbHelper.updateProgress(id, downloaded, total, speed, status)

    suspend fun updateStatus(id: String, status: DownloadStatus, error: String? = null, completedAt: Long? = null) =
        dbHelper.updateStatus(id, status, error, completedAt)

    suspend fun updateFileName(id: String, newName: String, newPath: String) =
        dbHelper.updateFileDetails(id, newName, newPath)

    suspend fun getDownload(id: String) = dbHelper.getById(id)

    suspend fun getAllDownloads(
        category: MediaCategory? = null,
        searchQuery: String? = null,
        sortBy: String = "${DownloadDatabaseHelper.COL_CREATED_AT} DESC"
    ) = dbHelper.getAllDownloads(category, searchQuery, sortBy)

    suspend fun getActiveDownloads() = dbHelper.getActiveDownloads()

    suspend fun getRecentDownloads(limit: Int = 5) = dbHelper.getRecentDownloads(limit)

    suspend fun deleteDownload(id: String) = dbHelper.deleteById(id)

    suspend fun clearAllHistory() = dbHelper.clearHistory()

    companion object {
        @Volatile
        private var INSTANCE: DownloadRepository? = null

        fun getInstance(context: Context): DownloadRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DownloadRepository(DownloadDatabaseHelper.getInstance(context)).also {
                    INSTANCE = it
                }
            }
        }
    }
}
