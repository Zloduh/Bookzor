package com.bookzor.core.player

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.bookzor.core.database.BookzorDatabase

@UnstableApi
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    lateinit var playerController: AudioPlayerController
        private set

    override fun onCreate() {
        super.onCreate()
        val db = BookzorDatabase.getInstance(applicationContext)
        playerController = AudioPlayerController(
            context = applicationContext,
            playbackStateDao = db.playbackStateDao()
        )

        val intent = packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(
                this,
                0,
                it,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        val notificationProvider = androidx.media3.session.DefaultMediaNotificationProvider.Builder(applicationContext)
            .setChannelId("bookzor_playback")
            .setChannelName(R.string.playback_channel_name)
            .setNotificationId(101)
            .build()
        setMediaNotificationProvider(notificationProvider)

        val seekBackCmd = androidx.media3.session.CommandButton.Builder()
            .setPlayerCommand(androidx.media3.common.Player.COMMAND_SEEK_BACK)
            .setDisplayName("Rewind 15s")
            .setIconResId(android.R.drawable.ic_media_rew)
            .build()

        val seekForwardCmd = androidx.media3.session.CommandButton.Builder()
            .setPlayerCommand(androidx.media3.common.Player.COMMAND_SEEK_FORWARD)
            .setDisplayName("Forward 15s")
            .setIconResId(android.R.drawable.ic_media_ff)
            .build()

        val sessionBuilder = MediaSession.Builder(this, playerController.exoPlayer)
            .setCustomLayout(listOf(seekBackCmd, seekForwardCmd))
        if (intent != null) {
            sessionBuilder.setSessionActivity(intent)
        }

        mediaSession = sessionBuilder.build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        playerController.release()
        super.onDestroy()
    }
}
