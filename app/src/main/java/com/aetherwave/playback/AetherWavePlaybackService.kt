package com.aetherwave.playback

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSessionService
import android.app.NotificationChannel
import android.app.NotificationManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
@UnstableApi
class AetherWavePlaybackService : MediaSessionService() {
    @Inject lateinit var player: ExoPlayer

    private var mediaSession: MediaSession? = null

    private companion object {
        const val PLAYBACK_CHANNEL_ID = "aetherwave_playback_high"
        const val PLAYBACK_NOTIFICATION_ID = 1001
    }

    override fun onCreate() {
        super.onCreate()
        // High importance keeps the playback notification at the top of the shade and lock screen.
        // A new channel id is used so the setting also applies to installs that already have the old channel.
        createPlaybackChannel()
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelId(PLAYBACK_CHANNEL_ID)
                .setNotificationId(PLAYBACK_NOTIFICATION_ID)
                .build(),
        )
        player.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            true,
        )
        if (mediaSession == null) {
            mediaSession = MediaSession.Builder(this, player).build()
        }
    }

    private fun createPlaybackChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            PLAYBACK_CHANNEL_ID,
            "Music playback",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Shows the song that is playing, with playback controls"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        // Only the session is released. The ExoPlayer is an app-wide singleton that
        // PlaybackController also drives; releasing it here leaves a dead player in the
        // Hilt graph and breaks playback the next time the service starts.
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }
}
