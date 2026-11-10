package app.pepper.wansky.service

import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/** Owns the YouTube ExoPlayer (v0.2.8). The player screen drives it
 *  through a MediaController, so Song mode keeps playing in the
 *  background with a notification + lock-screen controls, while Video
 *  mode pauses when the app goes away (no ghost audio). The service
 *  stops itself once playback is paused and no controller is
 *  attached. */
class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this).build().apply {
            playWhenReady = true
        }
        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(
        controllerInfo: MediaSession.ControllerInfo
    ): MediaSession? = mediaSession

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}
