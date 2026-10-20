package com.bookzor.core.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.MediaStyleNotificationHelper
import com.bookzor.core.database.BookzorDatabase

@UnstableApi
class PlaybackService : MediaSessionService() {

    companion object {
        const val ACTION_REWIND = "com.bookzor.ACTION_REWIND"
        const val ACTION_PLAY_PAUSE = "com.bookzor.ACTION_PLAY_PAUSE"
        const val ACTION_FORWARD = "com.bookzor.ACTION_FORWARD"
        const val NOTIFICATION_ID = 101
        const val CHANNEL_ID = "bookzor_audio_v2"
    }

    private var mediaSession: MediaSession? = null
    var playerController: AudioPlayerController? = null
        private set

    override fun onCreate() {
        super.onCreate()
        val controller = AudioPlayerController.instance
        playerController = controller
        val player = controller?.exoPlayer ?: run {
            val db = BookzorDatabase.getInstance(applicationContext)
            AudioPlayerController(applicationContext, db.playbackStateDao()).exoPlayer
        }

        createNotificationChannel()

        val intent = packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(
                this,
                0,
                it,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        val sessionBuilder = MediaSession.Builder(this, player)
        if (intent != null) {
            sessionBuilder.setSessionActivity(intent)
        }

        mediaSession = sessionBuilder.build()

        // Listen for playback changes to keep lockscreen controls updated
        player.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                if (events.contains(Player.EVENT_IS_PLAYING_CHANGED) ||
                    events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED) ||
                    events.contains(Player.EVENT_MEDIA_METADATA_CHANGED)
                ) {
                    updateNotification()
                }
            }
        })
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val player = mediaSession?.player

        when (intent?.action) {
            ACTION_REWIND -> player?.seekBack()
            ACTION_PLAY_PAUSE -> {
                if (player != null) {
                    if (player.isPlaying) player.pause() else player.play()
                }
            }
            ACTION_FORWARD -> player?.seekForward()
        }

        val notification = buildMediaNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            android.util.Log.w("PlaybackService", "startForeground error: ${e.message}")
        }

        return super.onStartCommand(intent, flags, startId)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.playback_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Audiobook playback controls"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }
    }

    private fun buildMediaNotification(): Notification {
        val session = mediaSession
        val player = session?.player
        val isPlaying = player?.isPlaying == true

        val title = player?.currentMediaItem?.mediaMetadata?.title?.toString()
            ?: "Audiobook"
        val author = player?.currentMediaItem?.mediaMetadata?.artist?.toString()
            ?: "Bookzor"

        val rewindIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, PlaybackService::class.java).setAction(ACTION_REWIND),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val playPauseIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, PlaybackService::class.java).setAction(ACTION_PLAY_PAUSE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val forwardIntent = PendingIntent.getService(
            this,
            3,
            Intent(this, PlaybackService::class.java).setAction(ACTION_FORWARD),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(
                this,
                0,
                it,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(author)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(isPlaying)
            .addAction(android.R.drawable.ic_media_rew, "Rewind 15s", rewindIntent)
            .addAction(
                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (isPlaying) "Pause" else "Play",
                playPauseIntent
            )
            .addAction(android.R.drawable.ic_media_ff, "Forward 15s", forwardIntent)

        if (launchIntent != null) {
            builder.setContentIntent(launchIntent)
        }

        if (session != null) {
            builder.setStyle(
                MediaStyleNotificationHelper.MediaStyle(session)
                    .setShowActionsInCompactView(0, 1, 2)
            )
        }

        return builder.build()
    }

    private fun updateNotification() {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        try {
            nm.notify(NOTIFICATION_ID, buildMediaNotification())
        } catch (_: Exception) {}
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        mediaSession?.run {
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
