package com.example.alldownloader.service

import android.content.Context
import com.example.alldownloader.data.db.DownloadRepository
import com.example.alldownloader.data.model.DownloadItem
import com.example.alldownloader.data.model.DownloadStatus
import com.example.alldownloader.data.model.MediaCategory
import com.example.alldownloader.utils.FileUtils
import com.example.alldownloader.utils.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.RandomAccessFile
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class DownloadManager private constructor(private val context: Context) {

    private val repository = DownloadRepository.getInstance(context)
    private val prefManager = PreferencesManager.getInstance(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val activeJobs = ConcurrentHashMap<String, Job>()
    
    private val _runningDownloads = MutableStateFlow<List<DownloadItem>>(emptyList())
    val runningDownloads: StateFlow<List<DownloadItem>> = _runningDownloads.asStateFlow()

    companion object {
        @Volatile
        private var INSTANCE: DownloadManager? = null

        fun getInstance(context: Context): DownloadManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DownloadManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    init {
        scope.launch {
            refreshActiveDownloads()
        }
    }

    suspend fun refreshActiveDownloads() {
        val active = repository.getActiveDownloads()
        _runningDownloads.value = active
    }

    fun enqueueDownload(
        url: String,
        title: String,
        fileName: String,
        mimeType: String,
        category: MediaCategory,
        totalBytes: Long,
        resolution: String? = null,
        bitrate: String? = null,
        format: String? = null
    ): String {
        val id = UUID.randomUUID().toString()
        val downloadDir = FileUtils.getDownloadDirectory(context, category)
        val ext = fileName.substringAfterLast('.', "bin")
        val baseName = fileName.substringBeforeLast('.')
        val targetFile = FileUtils.getUniqueFile(downloadDir, baseName, ext)

        val item = DownloadItem(
            id = id,
            url = url,
            title = title,
            fileName = targetFile.name,
            filePath = targetFile.absolutePath,
            mimeType = mimeType,
            category = category,
            totalBytes = totalBytes,
            downloadedBytes = 0L,
            status = DownloadStatus.QUEUED,
            resolution = resolution,
            bitrate = bitrate,
            format = format,
            createdAt = System.currentTimeMillis()
        )

        scope.launch {
            repository.saveDownload(item)
            refreshActiveDownloads()
            startDownloadService()
            processQueue()
        }

        return id
    }

    private fun startDownloadService() {
        DownloadService.startService(context)
    }

    fun resumeDownload(id: String) {
        scope.launch {
            val item = repository.getDownload(id) ?: return@launch
            repository.updateStatus(id, DownloadStatus.QUEUED)
            refreshActiveDownloads()
            startDownloadService()
            processQueue()
        }
    }

    fun pauseDownload(id: String) {
        activeJobs[id]?.cancel()
        activeJobs.remove(id)
        scope.launch {
            repository.updateStatus(id, DownloadStatus.PAUSED)
            refreshActiveDownloads()
        }
    }

    fun cancelDownload(id: String) {
        activeJobs[id]?.cancel()
        activeJobs.remove(id)
        scope.launch {
            val item = repository.getDownload(id)
            if (item != null) {
                FileUtils.deleteFileFromStorage(item.filePath)
                repository.updateStatus(id, DownloadStatus.CANCELLED)
            }
            refreshActiveDownloads()
        }
    }

    fun retryDownload(id: String) {
        scope.launch {
            val item = repository.getDownload(id) ?: return@launch
            FileUtils.deleteFileFromStorage(item.filePath)
            val updated = item.copy(
                downloadedBytes = 0L,
                speedBytesPerSec = 0L,
                status = DownloadStatus.QUEUED,
                errorMessage = null
            )
            repository.saveDownload(updated)
            refreshActiveDownloads()
            startDownloadService()
            processQueue()
        }
    }

    private fun processQueue() {
        scope.launch {
            val maxConcurrent = prefManager.getSettings().maxConcurrentDownloads
            val activeCount = activeJobs.size
            if (activeCount >= maxConcurrent) return@launch

            val queued = repository.getActiveDownloads().filter { it.status == DownloadStatus.QUEUED }
            val availableSlots = maxConcurrent - activeCount

            queued.take(availableSlots).forEach { item ->
                startWorker(item)
            }
        }
    }

    private fun startWorker(item: DownloadItem) {
        if (activeJobs.containsKey(item.id)) return

        val job = scope.launch {
            downloadFile(item)
        }
        activeJobs[item.id] = job
    }

    private suspend fun downloadFile(item: DownloadItem) = withContext(Dispatchers.IO) {
        val targetFile = File(item.filePath)
        targetFile.parentFile?.mkdirs()
        val initialDownloaded = if (targetFile.exists()) targetFile.length() else 0L

        repository.updateStatus(item.id, DownloadStatus.DOWNLOADING)
        refreshActiveDownloads()

        var raf: RandomAccessFile? = null
        var inputStream: InputStream? = null
        var response: Response? = null

        try {
            val requestBuilder = Request.Builder()
                .url(item.url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")
                .header("Accept", "*/*")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Sec-Ch-Ua", "\"Chromium\";v=\"128\", \"Not;A=Brand\";v=\"24\", \"Google Chrome\";v=\"128\"")
                .header("Sec-Ch-Ua-Mobile", "?0")
                .header("Sec-Ch-Ua-Platform", "\"Windows\"")

            if (initialDownloaded > 0L) {
                requestBuilder.header("Range", "bytes=$initialDownloaded-")
            }

            response = okHttpClient.newCall(requestBuilder.build()).execute()

            if (!response.isSuccessful && response.code != 206) {
                throw Exception("Server returned HTTP error ${response.code}: ${response.message}")
            }

            val body = response.body ?: throw Exception("Response body is empty")
            val contentLength = body.contentLength()
            val totalBytes = when {
                response.code == 206 -> initialDownloaded + contentLength
                contentLength > 0 -> contentLength
                else -> item.totalBytes
            }

            inputStream = body.byteStream()
            raf = RandomAccessFile(targetFile, "rw")
            if (response.code == 206) {
                raf.seek(initialDownloaded)
            } else {
                raf.setLength(0)
            }

            val buffer = ByteArray(8192)
            var currentDownloaded = if (response.code == 206) initialDownloaded else 0L

            var lastProgressTime = System.currentTimeMillis()
            var bytesSinceLastCalc = 0L
            var currentSpeed = 0L

            var bytesRead = inputStream.read(buffer)
            while (isActive && bytesRead != -1) {
                raf.write(buffer, 0, bytesRead)
                currentDownloaded += bytesRead
                bytesSinceLastCalc += bytesRead

                val now = System.currentTimeMillis()
                val delta = now - lastProgressTime
                if (delta >= 800) {
                    currentSpeed = (bytesSinceLastCalc * 1000) / delta
                    lastProgressTime = now
                    bytesSinceLastCalc = 0L

                    repository.updateProgress(
                        item.id,
                        currentDownloaded,
                        totalBytes,
                        currentSpeed,
                        DownloadStatus.DOWNLOADING
                    )
                    refreshActiveDownloads()
                    DownloadService.updateProgressNotification(
                        context,
                        item.copy(
                            downloadedBytes = currentDownloaded,
                            totalBytes = totalBytes,
                            speedBytesPerSec = currentSpeed
                        )
                    )
                }
                bytesRead = inputStream.read(buffer)
            }

            if (!isActive) {
                // Was paused or cancelled
                return@withContext
            }

            // Download finished successfully
            FileUtils.scanFileIntoMediaStore(context, targetFile, item.mimeType)

            repository.updateProgress(
                item.id,
                currentDownloaded,
                if (totalBytes > 0) totalBytes else currentDownloaded,
                0L,
                DownloadStatus.COMPLETED
            )
            repository.updateStatus(item.id, DownloadStatus.COMPLETED, null, System.currentTimeMillis())
            refreshActiveDownloads()

            DownloadService.showCompletionNotification(
                context,
                item.copy(
                    downloadedBytes = currentDownloaded,
                    totalBytes = currentDownloaded,
                    status = DownloadStatus.COMPLETED
                )
            )

        } catch (e: Exception) {
            if (isActive) {
                val errorMsg = e.localizedMessage ?: "Unknown download error"
                repository.updateStatus(item.id, DownloadStatus.FAILED, errorMsg)
                refreshActiveDownloads()
                DownloadService.showFailureNotification(context, item, errorMsg)
            }
        } finally {
            runCatching { inputStream?.close() }
            runCatching { raf?.close() }
            runCatching { response?.close() }
            activeJobs.remove(item.id)
            processQueue()
        }
    }
}
