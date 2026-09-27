package com.example.alldownloader.data.db

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.alldownloader.data.model.DownloadItem
import com.example.alldownloader.data.model.DownloadStatus
import com.example.alldownloader.data.model.MediaCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext

class DownloadDatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    private val _dbChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val dbChanges: SharedFlow<Unit> = _dbChanges.asSharedFlow()

    companion object {
        const val DATABASE_NAME = "alldown_downloads.db"
        const val DATABASE_VERSION = 1

        const val TABLE_DOWNLOADS = "downloads"
        const val COL_ID = "id"
        const val COL_URL = "url"
        const val COL_TITLE = "title"
        const val COL_FILE_NAME = "file_name"
        const val COL_FILE_PATH = "file_path"
        const val COL_MIME_TYPE = "mime_type"
        const val COL_CATEGORY = "category"
        const val COL_TOTAL_BYTES = "total_bytes"
        const val COL_DOWNLOADED_BYTES = "downloaded_bytes"
        const val COL_SPEED = "speed_bytes_sec"
        const val COL_STATUS = "status"
        const val COL_ERROR_MSG = "error_message"
        const val COL_CREATED_AT = "created_at"
        const val COL_COMPLETED_AT = "completed_at"
        const val COL_RESOLUTION = "resolution"
        const val COL_BITRATE = "bitrate"
        const val COL_FORMAT = "format"
        const val COL_THUMBNAIL_URI = "thumbnail_uri"

        @Volatile
        private var INSTANCE: DownloadDatabaseHelper? = null

        fun getInstance(context: Context): DownloadDatabaseHelper {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DownloadDatabaseHelper(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTableQuery = """
            CREATE TABLE $TABLE_DOWNLOADS (
                $COL_ID TEXT PRIMARY KEY,
                $COL_URL TEXT NOT NULL,
                $COL_TITLE TEXT NOT NULL,
                $COL_FILE_NAME TEXT NOT NULL,
                $COL_FILE_PATH TEXT NOT NULL,
                $COL_MIME_TYPE TEXT NOT NULL,
                $COL_CATEGORY TEXT NOT NULL,
                $COL_TOTAL_BYTES INTEGER NOT NULL,
                $COL_DOWNLOADED_BYTES INTEGER NOT NULL DEFAULT 0,
                $COL_SPEED INTEGER NOT NULL DEFAULT 0,
                $COL_STATUS TEXT NOT NULL,
                $COL_ERROR_MSG TEXT,
                $COL_CREATED_AT INTEGER NOT NULL,
                $COL_COMPLETED_AT INTEGER,
                $COL_RESOLUTION TEXT,
                $COL_BITRATE TEXT,
                $COL_FORMAT TEXT,
                $COL_THUMBNAIL_URI TEXT
            )
        """.trimIndent()
        db.execSQL(createTableQuery)
        db.execSQL("CREATE INDEX idx_downloads_status ON $TABLE_DOWNLOADS ($COL_STATUS)")
        db.execSQL("CREATE INDEX idx_downloads_created ON $TABLE_DOWNLOADS ($COL_CREATED_AT DESC)")
        db.execSQL("CREATE INDEX idx_downloads_category ON $TABLE_DOWNLOADS ($COL_CATEGORY)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Migration strategy for future upgrades
    }

    private fun notifyChanged() {
        _dbChanges.tryEmit(Unit)
    }

    suspend fun insertOrUpdate(item: DownloadItem) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put(COL_ID, item.id)
            put(COL_URL, item.url)
            put(COL_TITLE, item.title)
            put(COL_FILE_NAME, item.fileName)
            put(COL_FILE_PATH, item.filePath)
            put(COL_MIME_TYPE, item.mimeType)
            put(COL_CATEGORY, item.category.name)
            put(COL_TOTAL_BYTES, item.totalBytes)
            put(COL_DOWNLOADED_BYTES, item.downloadedBytes)
            put(COL_SPEED, item.speedBytesPerSec)
            put(COL_STATUS, item.status.name)
            put(COL_ERROR_MSG, item.errorMessage)
            put(COL_CREATED_AT, item.createdAt)
            put(COL_COMPLETED_AT, item.completedAt)
            put(COL_RESOLUTION, item.resolution)
            put(COL_BITRATE, item.bitrate)
            put(COL_FORMAT, item.format)
            put(COL_THUMBNAIL_URI, item.thumbnailUri)
        }
        writableDatabase.insertWithOnConflict(
            TABLE_DOWNLOADS,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE
        )
        notifyChanged()
    }

    suspend fun updateProgress(
        id: String,
        downloadedBytes: Long,
        totalBytes: Long,
        speedBytes: Long,
        status: DownloadStatus
    ) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put(COL_DOWNLOADED_BYTES, downloadedBytes)
            if (totalBytes > 0) put(COL_TOTAL_BYTES, totalBytes)
            put(COL_SPEED, speedBytes)
            put(COL_STATUS, status.name)
        }
        writableDatabase.update(TABLE_DOWNLOADS, values, "$COL_ID = ?", arrayOf(id))
        notifyChanged()
    }

    suspend fun updateStatus(
        id: String,
        status: DownloadStatus,
        errorMessage: String? = null,
        completedAt: Long? = null
    ) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put(COL_STATUS, status.name)
            put(COL_SPEED, 0L)
            if (errorMessage != null) put(COL_ERROR_MSG, errorMessage)
            if (completedAt != null) put(COL_COMPLETED_AT, completedAt)
        }
        writableDatabase.update(TABLE_DOWNLOADS, values, "$COL_ID = ?", arrayOf(id))
        notifyChanged()
    }

    suspend fun updateFileDetails(id: String, newName: String, newPath: String) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put(COL_FILE_NAME, newName)
            put(COL_FILE_PATH, newPath)
        }
        writableDatabase.update(TABLE_DOWNLOADS, values, "$COL_ID = ?", arrayOf(id))
        notifyChanged()
    }

    suspend fun getById(id: String): DownloadItem? = withContext(Dispatchers.IO) {
        val cursor = readableDatabase.query(
            TABLE_DOWNLOADS,
            null,
            "$COL_ID = ?",
            arrayOf(id),
            null,
            null,
            null
        )
        cursor.use {
            if (it.moveToFirst()) mapCursorToDownloadItem(it) else null
        }
    }

    suspend fun getAllDownloads(
        category: MediaCategory? = null,
        searchQuery: String? = null,
        sortBy: String = "$COL_CREATED_AT DESC"
    ): List<DownloadItem> = withContext(Dispatchers.IO) {
        val selectionArgs = mutableListOf<String>()
        val selectionClauses = mutableListOf<String>()

        if (category != null && category != MediaCategory.ALL) {
            selectionClauses.add("$COL_CATEGORY = ?")
            selectionArgs.add(category.name)
        }

        if (!searchQuery.isNullOrBlank()) {
            selectionClauses.add("($COL_TITLE LIKE ? OR $COL_FILE_NAME LIKE ?)")
            selectionArgs.add("%$searchQuery%")
            selectionArgs.add("%$searchQuery%")
        }

        val whereClause = if (selectionClauses.isNotEmpty()) selectionClauses.joinToString(" AND ") else null
        val args = if (selectionArgs.isNotEmpty()) selectionArgs.toTypedArray() else null

        val items = mutableListOf<DownloadItem>()
        readableDatabase.query(
            TABLE_DOWNLOADS,
            null,
            whereClause,
            args,
            null,
            null,
            sortBy
        ).use { cursor ->
            while (cursor.moveToNext()) {
                items.add(mapCursorToDownloadItem(cursor))
            }
        }
        items
    }

    suspend fun getActiveDownloads(): List<DownloadItem> = withContext(Dispatchers.IO) {
        val items = mutableListOf<DownloadItem>()
        readableDatabase.query(
            TABLE_DOWNLOADS,
            null,
            "$COL_STATUS IN (?, ?, ?)",
            arrayOf(DownloadStatus.QUEUED.name, DownloadStatus.DOWNLOADING.name, DownloadStatus.PAUSED.name),
            null,
            null,
            "$COL_CREATED_AT ASC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                items.add(mapCursorToDownloadItem(cursor))
            }
        }
        items
    }

    suspend fun getRecentDownloads(limit: Int = 5): List<DownloadItem> = withContext(Dispatchers.IO) {
        val items = mutableListOf<DownloadItem>()
        readableDatabase.query(
            TABLE_DOWNLOADS,
            null,
            null,
            null,
            null,
            null,
            "$COL_CREATED_AT DESC",
            limit.toString()
        ).use { cursor ->
            while (cursor.moveToNext()) {
                items.add(mapCursorToDownloadItem(cursor))
            }
        }
        items
    }

    suspend fun deleteById(id: String): Boolean = withContext(Dispatchers.IO) {
        val count = writableDatabase.delete(TABLE_DOWNLOADS, "$COL_ID = ?", arrayOf(id))
        notifyChanged()
        count > 0
    }

    suspend fun clearHistory(): Boolean = withContext(Dispatchers.IO) {
        val count = writableDatabase.delete(TABLE_DOWNLOADS, null, null)
        notifyChanged()
        count >= 0
    }

    private fun mapCursorToDownloadItem(cursor: Cursor): DownloadItem {
        return DownloadItem(
            id = cursor.getString(cursor.getColumnIndexOrThrow(COL_ID)),
            url = cursor.getString(cursor.getColumnIndexOrThrow(COL_URL)),
            title = cursor.getString(cursor.getColumnIndexOrThrow(COL_TITLE)),
            fileName = cursor.getString(cursor.getColumnIndexOrThrow(COL_FILE_NAME)),
            filePath = cursor.getString(cursor.getColumnIndexOrThrow(COL_FILE_PATH)),
            mimeType = cursor.getString(cursor.getColumnIndexOrThrow(COL_MIME_TYPE)),
            category = runCatching { MediaCategory.valueOf(cursor.getString(cursor.getColumnIndexOrThrow(COL_CATEGORY))) }.getOrDefault(MediaCategory.OTHER),
            totalBytes = cursor.getLong(cursor.getColumnIndexOrThrow(COL_TOTAL_BYTES)),
            downloadedBytes = cursor.getLong(cursor.getColumnIndexOrThrow(COL_DOWNLOADED_BYTES)),
            speedBytesPerSec = cursor.getLong(cursor.getColumnIndexOrThrow(COL_SPEED)),
            status = runCatching { DownloadStatus.valueOf(cursor.getString(cursor.getColumnIndexOrThrow(COL_STATUS))) }.getOrDefault(DownloadStatus.QUEUED),
            errorMessage = cursor.getString(cursor.getColumnIndexOrThrow(COL_ERROR_MSG)),
            createdAt = cursor.getLong(cursor.getColumnIndexOrThrow(COL_CREATED_AT)),
            completedAt = if (!cursor.isNull(cursor.getColumnIndexOrThrow(COL_COMPLETED_AT))) cursor.getLong(cursor.getColumnIndexOrThrow(COL_COMPLETED_AT)) else null,
            resolution = cursor.getString(cursor.getColumnIndexOrThrow(COL_RESOLUTION)),
            bitrate = cursor.getString(cursor.getColumnIndexOrThrow(COL_BITRATE)),
            format = cursor.getString(cursor.getColumnIndexOrThrow(COL_FORMAT)),
            thumbnailUri = cursor.getString(cursor.getColumnIndexOrThrow(COL_THUMBNAIL_URI))
        )
    }
}
