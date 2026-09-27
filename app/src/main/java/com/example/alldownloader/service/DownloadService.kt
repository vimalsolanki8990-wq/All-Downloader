package com.example.alldownloader.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.alldownloader.MainActivity
import com.example.alldownloader.R
import com.example.alldownloader.data.model.DownloadItem
import com.example.alldownloader.utils.FileUtils
import java.io.File

class DownloadService : Service() {

    companion object {
        const val CHANNEL_ID = "alldown_channel_downloads"
        const val NOTIFICATION_ID_FOREGROUND = 1001

        const val ACTION_START = "com.example.alldownloader.ACTION_START"
        const val ACTION_PAUSE = "com.example.alldownloader.ACTION_PAUSE"
        const val ACTION_CANCEL = "com.example.alldownloader.ACTION_CANCEL"
        const val ACTION_RESUME = "com.example.alldownloader.ACTION_RESUME"
        const val EXTRA_DOWNLOAD_ID = "extra_download_id"

        fun startService(context: Context) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_START
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun updateProgressNotification(context: Context, item: DownloadItem) {
            val notifManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val percent = item.progressPercent
            val speed = FileUtils.formatSpeed(item.speedBytesPerSec)
            val downloadedStr = FileUtils.formatFileSize(item.downloadedBytes)
            val totalStr = if (item.totalBytes > 0) FileUtils.formatFileSize(item.totalBytes) else "—"

            val pauseIntent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_PAUSE
                putExtra(EXTRA_DOWNLOAD_ID, item.id)
            }
            val pausePendingIntent = PendingIntent.getService(
                context,
                item.id.hashCode(),
                pauseIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val cancelIntent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_CANCEL
                putExtra(EXTRA_DOWNLOAD_ID, item.id)
            }
            val cancelPendingIntent = PendingIntent.getService(
                context,
                item.id.hashCode() + 1,
                cancelIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val openAppPending = PendingIntent.getActivity(
                context,
                0,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_logo_alldown)
                .setContentTitle(context.getString(R.string.notif_downloading, item.fileName))
                .setContentText("$percent% • $speed • $downloadedStr / $totalStr")
                .setProgress(100, percent, item.totalBytes <= 0)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(openAppPending)
                .addAction(R.drawable.ic_pause, context.getString(R.string.btn_pause), pausePendingIntent)
                .addAction(R.drawable.ic_cancel, context.getString(R.string.btn_cancel), cancelPendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()

            notifManager.notify(NOTIFICATION_ID_FOREGROUND, notification)
        }

        fun showCompletionNotification(context: Context, item: DownloadItem) {
            val notifManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val file = File(item.filePath)
            var openPendingIntent: PendingIntent? = null

            if (file.exists()) {
                try {
                    val uri: Uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                    val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, item.mimeType.ifBlank { "*/*" })
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    openPendingIntent = PendingIntent.getActivity(
                        context,
                        item.id.hashCode(),
                        viewIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                } catch (ignored: Exception) {}
            }

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_check_circle)
                .setContentTitle(context.getString(R.string.notif_download_complete, item.fileName))
                .setContentText(context.getString(R.string.notif_tap_to_open))
                .setAutoCancel(true)
                .setContentIntent(openPendingIntent)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()

            notifManager.notify(item.id.hashCode(), notification)
        }

        fun showFailureNotification(context: Context, item: DownloadItem, errorMsg: String) {
            val notifManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_error_outline)
                .setContentTitle(context.getString(R.string.notif_download_failed, item.fileName))
                .setContentText(errorMsg)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()

            notifManager.notify(item.id.hashCode(), notification)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        startForeground(NOTIFICATION_ID_FOREGROUND, createInitialNotification())

        val downloadId = intent?.getStringExtra(EXTRA_DOWNLOAD_ID)
        val downloadManager = DownloadManager.getInstance(applicationContext)

        when (intent?.action) {
            ACTION_PAUSE -> {
                if (downloadId != null) {
                    downloadManager.pauseDownload(downloadId)
                }
            }
            ACTION_CANCEL -> {
                if (downloadId != null) {
                    downloadManager.cancelDownload(downloadId)
                }
            }
            ACTION_RESUME -> {
                if (downloadId != null) {
                    downloadManager.resumeDownload(downloadId)
                }
            }
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.channel_downloads_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.channel_downloads_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createInitialNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            this.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openAppPending = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_logo_alldown)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.download_manager))
            .setOngoing(true)
            .setContentIntent(openAppPending)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
