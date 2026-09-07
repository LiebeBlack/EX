package com.apex.files.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.apex.files.MainActivity
import com.apex.files.R
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * ForegroundService for media playback with proper notification.
 * Required for background audio playback in Android 8.0+.
 */
@AndroidEntryPoint
class MediaPlaybackService : Service() {

    companion object {
        private const val CHANNEL_ID = "media_playback_channel"
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_PLAY_PAUSE = "com.apex.files.ACTION_PLAY_PAUSE"
        private const val ACTION_STOP = "com.apex.files.ACTION_STOP"
        private const val ACTION_NEXT = "com.apex.files.ACTION_NEXT"
        private const val ACTION_PREVIOUS = "com.apex.files.ACTION_PREVIOUS"

        fun startService(context: Context, title: String, artist: String? = null) {
            val intent = Intent(context, MediaPlaybackService::class.java).apply {
                putExtra("title", title)
                putExtra("artist", artist ?: "Unknown Artist")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            context.stopService(Intent(context, MediaPlaybackService::class.java))
        }
    }

    @Inject
    lateinit var notificationManager: NotificationManager

    private fun getNotifier(): NotificationManager {
        return if (::notificationManager.isInitialized) {
            notificationManager
        } else {
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        }
    }

    private var isPlaying = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> togglePlayPause()
            ACTION_STOP -> stopPlayback()
            ACTION_NEXT -> playNext()
            ACTION_PREVIOUS -> playPrevious()
            else -> {
                // Initial start with media info
                val title = intent?.getStringExtra("title") ?: "Media Playback"
                val artist = intent?.getStringExtra("artist") ?: "Unknown Artist"
                startForeground(NOTIFICATION_ID, createNotification(title, artist))
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Media Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notification for media playback controls"
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
            }
            getNotifier().createNotificationChannel(channel)
        }
    }

    private fun createNotification(title: String, artist: String): Notification {
        // Create pending intents for notification actions
        val playPauseIntent = Intent(this, MediaPlaybackService::class.java).apply {
            action = ACTION_PLAY_PAUSE
        }
        val playPausePendingIntent = PendingIntent.getService(
            this,
            0,
            playPauseIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, MediaPlaybackService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val nextIntent = Intent(this, MediaPlaybackService::class.java).apply {
            action = ACTION_NEXT
        }
        val nextPendingIntent = PendingIntent.getService(
            this,
            2,
            nextIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val previousIntent = Intent(this, MediaPlaybackService::class.java).apply {
            action = ACTION_PREVIOUS
        }
        val previousPendingIntent = PendingIntent.getService(
            this,
            3,
            previousIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Main activity intent
        val mainIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val mainPendingIntent = PendingIntent.getActivity(
            this,
            0,
            mainIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val playPauseIcon = if (isPlaying) {
            android.R.drawable.ic_media_pause
        } else {
            android.R.drawable.ic_media_play
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(artist)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(mainPendingIntent)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(android.R.drawable.ic_media_previous, "Previous", previousPendingIntent)
            .addAction(playPauseIcon, if (isPlaying) "Pause" else "Play", playPausePendingIntent)
            .addAction(android.R.drawable.ic_media_next, "Next", nextPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .build()
    }

    private fun togglePlayPause() {
        isPlaying = !isPlaying
        // Update notification with new play/pause state
        // In a real implementation, you would also control the actual media player here
    }

    private fun stopPlayback() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun playNext() {
        // Implement next track logic
    }

    private fun playPrevious() {
        // Implement previous track logic
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clean up resources
    }
}