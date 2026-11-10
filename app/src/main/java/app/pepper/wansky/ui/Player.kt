package app.pepper.wansky.ui

import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import app.pepper.wansky.data.YouTube
import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Slider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.layout.ContentScale
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaMetadata
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import app.pepper.wansky.data.YtExtractor
import app.pepper.wansky.data.YtStreams
import app.pepper.wansky.service.PlaybackService
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

/** In-memory handoff to the player destinations (avoids encoding
 *  long signed URLs into navigation arguments). */
object PlayQueue {
    var url: String? = null
    var title: String? = null
    var webUrl: String? = null
}

/** Title handoff for the YouTube player (the id travels in the route). */
object YtQueue {
    var title: String? = null
}

/** Lifecycle for a player WebView (v0.2.7) — two bugs, one helper:
 *  (1) Ghost audio: a WebView keeps playing after its screen goes
 *  away unless it is paused and destroyed. Back (or Home / lock)
 *  must mean silence, so ON_PAUSE pauses it and dispose destroys
 *  it. ON_RESUME resumes it when the user comes back to the player
 *  screen. (2) Black video: by default a WebView renders on a
 *  software layer that cannot display video — audio plays over a
 *  black box. The fix is LAYER_TYPE_HARDWARE, set in the factory
 *  of each player WebView below. */
@Composable
private fun WebViewPlayerLifecycle(webView: () -> WebView?) {
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> webView()?.onPause()
                Lifecycle.Event.ON_RESUME -> webView()?.onResume()
                else -> {}
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            webView()?.apply { onPause(); stopLoading(); destroy() }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(nav: NavController) {
    val ctx = LocalContext.current
    val url = PlayQueue.url
    val title = PlayQueue.title ?: "Playing"
    // Bumping retryKey rebuilds the player from scratch.
    var retryKey by remember { mutableStateOf(0) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var isReady by remember { mutableStateOf(false) }

    // A new URL resets the error/loading state (in an effect, never
    // during composition).
    LaunchedEffect(url) {
        errorMsg = null
        isReady = false
    }

    // The player is created once per (url, retry). Any failure while
    // building it — or while a stream loads — becomes a visible error
    // screen instead of taking the whole app down (the Live TV crash).
    val player = remember(url, retryKey) {
        try {
            ExoPlayer.Builder(ctx.applicationContext).build().apply {
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_READY) isReady = true
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        errorMsg = "This stream couldn't play. " +
                            "The channel may be offline — try another one, " +
                            "or check your connection.\n(${error.message})"
                    }
                })
                url?.let {
                    setMediaItem(MediaItem.fromUri(it))
                    prepare()
                    playWhenReady = true
                }
            }
        } catch (e: Exception) {
            errorMsg = "The player couldn't start: ${e.message}"
            null
        }
    }
    DisposableEffect(player) { onDispose { player?.release() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1) },
                navigationIcon = {
                    IconButton({ nav.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { inner ->
        when {
            url == null -> {
                Box(
                    Modifier.padding(inner).fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) { Text("Nothing to play.") }
            }
            errorMsg != null -> {
                Box(
                    Modifier.padding(inner).fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Couldn't play this one", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(8.dp))
                        Text(errorMsg!!, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(16.dp))
                        Button({
                            errorMsg = null
                            isReady = false
                            retryKey++
                        }) { Text("Try again") }
                        Spacer(Modifier.height(8.dp))
                        Button({ nav.popBackStack() }) { Text("Back") }
                    }
                }
            }
            else -> {
                Box(Modifier.padding(inner).fillMaxSize()) {
                    AndroidView(
                        factory = { c ->
                            PlayerView(c).apply {
                                this.player = player
                                useController = true
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                    if (!isReady) {
                        Box(
                            Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) { CircularProgressIndicator() }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebViewPlayerScreen(nav: NavController) {
    val url = PlayQueue.webUrl
    val title = PlayQueue.title ?: "Playing"
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    WebViewPlayerLifecycle { webViewRef }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1) },
                navigationIcon = {
                    IconButton({ nav.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { inner ->
        if (url == null) {
            Box(
                Modifier.padding(inner).fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("Nothing to play.")
            }
        } else {
            AndroidView(
                factory = { c ->
                    WebView(c).apply {
                        // Hardware layer: without it the video surface
                        // stays black while audio plays (v0.2.7).
                        setLayerType(View.LAYER_TYPE_HARDWARE, null)
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        webChromeClient = WebChromeClient()
                        webViewClient = WebViewClient()
                        webViewRef = this
                        loadUrl(url)
                    }
                },
                modifier = Modifier.padding(inner).fillMaxSize()
            )
        }
    }
}

/** Song / Video toggle for the native YouTube player. */
private enum class YtMode { Video, Song }

private fun fmtTime(ms: Long): String {
    val s = (ms / 1000).toInt().coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

/** One audio track, read from the player's current tracks (v0.3.0). */
private data class AudioOpt(val label: String, val group: Tracks.Group, val trackIndex: Int)

/** YouTube player (v0.2.8): NATIVE ExoPlayer, no WebView.
 *
 *  The WebView route failed twice — Error 153 (v0.2.6 fixed the
 *  Referer), then the black video box WITH audio on 0.2.7 despite
 *  LAYER_TYPE_HARDWARE + manifest hardwareAccelerated. So the player
 *  is now NewPipeExtractor → ExoPlayer, owned by PlaybackService and
 *  driven through a MediaController.
 *
 *  That also delivers the Music mode: a Song/Video toggle. Video
 *  plays the video; Song plays the audio-only track and keeps going
 *  in the background with a notification. With a DASH manifest both
 *  modes share one item (Song just disables the video track, so the
 *  toggle never rebuffers); without DASH the modes swap between the
 *  progressive video URL and the audio-only URL, keeping the
 *  position.
 *
 *  Video mode pauses when the app goes to the background (no ghost
 *  audio — the v0.2.7 rule); Song mode deliberately does not.
 *
 *  v0.3.0 — YouTube / YouTube Music style player UI (his ask,
 *  2026-11-10: the 0.2.9 player "looks too simple"): custom overlay
 *  controls on the video (tap to show/hide, auto-hide while
 *  playing, prev / play / next, seek + times, settings gear with
 *  speed + audio track, fullscreen), a YT Music style Song screen
 *  (big square art, shuffle / prev / big play / next / repeat,
 *  UP NEXT tab) and a channel row with avatar, views, Share and
 *  Open in YouTube below the player. No LYRICS tab — there is no
 *  lyrics source to fill it with. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YtPlayerScreen(nav: NavController, videoId: String) {
    val ctx = LocalContext.current
    val activity = ctx as? Activity
    val title = YtQueue.title ?: "YouTube"
    var upNext by remember { mutableStateOf<List<YouTube.Video>?>(null) }
    var upNextChannel by remember { mutableStateOf("") }
    var upNextAvatar by remember { mutableStateOf("") }
    var upNextViews by remember { mutableStateOf("") }
    var upNextPublished by remember { mutableStateOf("") }
    var streams by remember { mutableStateOf<YtStreams?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var retryKey by remember { mutableStateOf(0) }
    var mode by remember { mutableStateOf(YtMode.Video) }
    var controller by remember { mutableStateOf<MediaController?>(null) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var isPlaying by remember { mutableStateOf(false) }
    var notifAsked by remember { mutableStateOf(false) }
    // v0.3.0 player UI state
    var shuffle by remember { mutableStateOf(false) }
    var repeatOne by remember { mutableStateOf(false) }
    var fullscreen by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
    var showSettings by remember { mutableStateOf(false) }
    var speed by remember { mutableFloatStateOf(1f) }
    var audioOpts by remember { mutableStateOf<List<AudioOpt>>(emptyList()) }
    var audioSelected by remember { mutableStateOf(-1) }

    fun setFullscreen(on: Boolean) {
        fullscreen = on
        activity?.requestedOrientation = if (on) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
    // Back in fullscreen exits fullscreen first, it never leaves the
    // screen (MainActivity handles rotation via configChanges, so
    // the activity is NOT recreated and playback is not restarted).
    BackHandler(enabled = fullscreen) { setFullscreen(false) }

    // One MediaController for this screen, bound to PlaybackService.
    // Hardened (v0.2.9): if the service can't be resolved the screen
    // shows an error instead of crashing — the missing
    // MediaSessionService intent-filter did exactly that on 0.2.8.
    DisposableEffect(Unit) {
        val future = try {
            MediaController.Builder(
                ctx,
                SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java))
            ).buildAsync()
        } catch (e: Exception) {
            errorMsg = "Couldn't start the player: ${e.message}"
            null
        }
        future?.addListener(
            {
                try {
                    controller = future.get()
                } catch (e: Exception) {
                    errorMsg = "Couldn't start the player: ${e.message}"
                }
            },
            ContextCompat.getMainExecutor(ctx)
        )
        onDispose {
            // Leaving the screen in Video mode = silence (the v0.2.7
            // ghost-audio rule). Song mode keeps playing: the service
            // stays alive with its notification.
            if (mode == YtMode.Video) controller?.pause()
            if (future != null) MediaController.releaseFuture(future)
            controller = null
        }
    }

    // Resolve the video to playable stream URLs.
    LaunchedEffect(videoId, retryKey) {
        errorMsg = null
        streams = null
        mode = YtMode.Video
        if (fullscreen) setFullscreen(false)
        controlsVisible = true
        streams = try {
            YtExtractor.extract(videoId)
        } catch (e: Exception) {
            errorMsg = e.message ?: "Couldn't load this video."
            null
        }
    }

    // Hand the resolved stream to the service player.
    LaunchedEffect(controller, streams) {
        val c = controller
        val s = streams
        if (c != null && s != null) {
            val url = if (s.dashUrl.isNotBlank()) s.dashUrl else s.videoUrl
            if (url.isBlank()) {
                errorMsg = "No playable stream found for this video."
            } else {
                val item = MediaItem.Builder().setUri(url)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(s.title.ifBlank { title })
                            .setArtist(s.uploader)
                            .build()
                    ).build()
                c.setMediaItem(item)
                c.prepare()
                c.setPlaybackSpeed(speed)
                c.repeatMode =
                    if (repeatOne) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                c.play()
            }
        }
    }

    LaunchedEffect(videoId) {
        try {
            val res = YouTube.related(videoId)
            upNext = res.videos
            upNextChannel = res.channel
            upNextAvatar = res.channelAvatar
            upNextViews = res.views
            upNextPublished = res.published
        } catch (e: Exception) {
            upNext = emptyList()
        }
    }

    // Seek-bar + play/pause state, polled from the controller.
    LaunchedEffect(controller) {
        while (true) {
            controller?.let {
                positionMs = it.currentPosition
                val d = it.duration
                durationMs = if (d > 0) d else 0L
                isPlaying = it.isPlaying
            }
            delay(500)
        }
    }

    // Video mode pauses when the app goes away (no ghost audio);
    // Song mode deliberately keeps playing.
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        var wasPlaying = false
        val observer = LifecycleEventObserver { _, event ->
            val c = controller ?: return@LifecycleEventObserver
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    if (mode == YtMode.Video) {
                        wasPlaying = c.isPlaying
                        c.pause()
                    }
                }
                Lifecycle.Event.ON_RESUME -> {
                    if (mode == YtMode.Video && wasPlaying) c.play()
                }
                else -> {}
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    // The background-audio notification needs POST_NOTIFICATIONS on
    // Android 13+. If he denies it, Song mode still plays — there is
    // just no notification to pause from (the app has to be reopened).
    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* result ignored: playback works either way */ }
    fun ensureNotifPermission() {
        if (notifAsked) return
        notifAsked = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                ctx, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun applyMode(newMode: YtMode) {
        val c = controller ?: return
        val s = streams ?: return
        if (newMode == mode) return
        if (newMode == YtMode.Song && fullscreen) setFullscreen(false)
        mode = newMode
        if (newMode == YtMode.Song) ensureNotifPermission()
        if (s.dashUrl.isNotBlank()) {
            // One adaptive item: just disable / re-enable the video
            // track. No rebuffer, no position loss.
            c.trackSelectionParameters = c.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, newMode == YtMode.Song)
                .build()
        } else {
            val url = if (newMode == YtMode.Song) s.audioUrl else s.videoUrl
            if (url.isNotBlank()) {
                val item = MediaItem.Builder().setUri(url)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(s.title.ifBlank { title })
                            .setArtist(s.uploader)
                            .build()
                    ).build()
                c.setMediaItem(item, c.currentPosition)
                c.prepare()
                c.play()
            }
        }
    }

    /** Next in Up next — a random pick when shuffle is on. */
    fun playNext() {
        val list = upNext ?: return
        if (list.isEmpty()) return
        val others = list.filter { it.id != videoId }
        val pick = if (shuffle && others.size > 1) others.random()
            else others.firstOrNull() ?: list.firstOrNull()
        if (pick != null) {
            YtQueue.title = pick.title
            nav.navigate("ytplayer/${pick.id}") {
                popUpTo("youtube")
            }
        }
    }

    fun shareVideo() {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(
                Intent.EXTRA_TEXT,
                "https://www.youtube.com/watch?v=$videoId"
            )
        }
        ctx.startActivity(Intent.createChooser(send, "Share video"))
    }

    fun openInYouTube() {
        ctx.startActivity(
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://www.youtube.com/watch?v=$videoId")
            )
        )
    }

    // Auto-hide the video controls while playing (YouTube behaviour).
    LaunchedEffect(isPlaying, controlsVisible, mode) {
        if (mode == YtMode.Video && controlsVisible && isPlaying) {
            delay(3000)
            controlsVisible = false
        }
    }

    // Playback settings: speed + audio track (replaces the stock
    // PlayerView gear, which went away with the custom controls).
    if (showSettings) {
        LaunchedEffect(Unit) {
            val c = controller
            if (c != null) {
                val opts = mutableListOf<AudioOpt>()
                for (g in c.currentTracks.groups) {
                    if (g.type != C.TRACK_TYPE_AUDIO) continue
                    for (i in 0 until g.length) {
                        val f = g.getTrackFormat(i)
                        val label = f.label ?: f.language ?: "Track ${i + 1}"
                        opts.add(AudioOpt(label, g, i))
                    }
                }
                audioOpts = opts
                audioSelected = opts.indexOfFirst {
                    it.group.isTrackSelected(it.trackIndex)
                }
            }
        }
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text("Playback settings") },
            text = {
                Column {
                    Text("Speed", style = MaterialTheme.typography.titleSmall)
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { s ->
                            TextButton(
                                onClick = {
                                    speed = s
                                    controller?.setPlaybackSpeed(s)
                                },
                                contentPadding = PaddingValues(4.dp)
                            ) {
                                Text(
                                    "${if (s == 1f) "1" else s.toString()}×",
                                    fontWeight = if (speed == s) FontWeight.Bold
                                        else FontWeight.Normal,
                                    color = if (speed == s)
                                        MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("Audio", style = MaterialTheme.typography.titleSmall)
                    if (audioOpts.isEmpty()) {
                        Text(
                            "Default audio",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        TextButton(onClick = {
                            controller?.let { c ->
                                c.trackSelectionParameters =
                                    c.trackSelectionParameters.buildUpon()
                                        .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                                        .build()
                            }
                            audioSelected = -1
                        }) {
                            Text(
                                "Auto",
                                fontWeight = if (audioSelected == -1) FontWeight.Bold
                                    else FontWeight.Normal,
                                color = if (audioSelected == -1)
                                    MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        audioOpts.forEachIndexed { idx, opt ->
                            Row(
                                Modifier.fillMaxWidth()
                                    .clickable {
                                        controller?.let { c ->
                                            c.trackSelectionParameters =
                                                c.trackSelectionParameters.buildUpon()
                                                    .addOverride(
                                                        TrackSelectionOverride(
                                                            opt.group.mediaTrackGroup,
                                                            opt.trackIndex
                                                        )
                                                    )
                                                    .build()
                                        }
                                        audioSelected = idx
                                    },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = audioSelected == idx,
                                    onClick = null
                                )
                                Text(
                                    opt.label,
                                    Modifier.padding(start = 8.dp),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton({ showSettings = false }) { Text("Done") }
            }
        )
    }

    Scaffold(
        topBar = {
            if (!fullscreen) {
                TopAppBar(
                    title = {
                        Text(
                            streams?.title?.ifBlank { title } ?: title,
                            maxLines = 1
                        )
                    },
                    navigationIcon = {
                        IconButton({ nav.popBackStack() }) {
                            Icon(Icons.Filled.ArrowBack, "Back")
                        }
                    }
                )
            }
        },
        contentWindowInsets = if (fullscreen) WindowInsets(0, 0, 0, 0)
            else ScaffoldDefaults.contentWindowInsets
    ) { inner ->
        Column(Modifier.padding(inner).fillMaxSize()) {
            // Song / Video toggle. It stays composed (height 0) in
            // fullscreen, and the video surface below is key()ed, so
            // toggling fullscreen never recreates the PlayerView —
            // recreating it would restart playback.
            Box(
                Modifier.then(
                    if (fullscreen) Modifier.height(0.dp).clipToBounds()
                    else Modifier
                ).fillMaxWidth()
            ) {
                Row(
                    Modifier.fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        { applyMode(YtMode.Video) },
                        modifier = Modifier.weight(1f),
                        enabled = mode != YtMode.Video
                    ) { Text("▶ Video") }
                    Button(
                        { applyMode(YtMode.Song) },
                        modifier = Modifier.weight(1f),
                        enabled = mode != YtMode.Song
                    ) { Text("♪ Song") }
                }
            }
            when {
                errorMsg != null -> {
                    Box(
                        Modifier.fillMaxWidth().padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "Couldn't play this one",
                                style = MaterialTheme.typography.titleLarge
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                errorMsg!!,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(Modifier.height(16.dp))
                            Button({ retryKey++ }) { Text("Try again") }
                            Spacer(Modifier.height(8.dp))
                            Button({ openInYouTube() }) { Text("Open in YouTube") }
                        }
                    }
                }
                streams == null -> {
                    Box(
                        Modifier.fillMaxWidth().padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator() }
                }
                mode == YtMode.Video -> {
                    key("yt-video-surface") {
                        Box(
                            modifier = if (fullscreen) Modifier.fillMaxSize()
                            else Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                        ) {
                            AndroidView(
                                factory = { c ->
                                    PlayerView(c).apply { useController = false }
                                },
                                update = { it.player = controller },
                                modifier = Modifier.fillMaxSize()
                            )
                            // Tap anywhere to show / hide the controls.
                            // Buttons and the seek bar are children, so
                            // their own taps win over the toggle.
                            Box(
                                Modifier.fillMaxSize()
                                    .clickable { controlsVisible = !controlsVisible }
                            ) {
                                if (!controlsVisible) {
                                    // Thin red progress line, YouTube-style.
                                    Box(
                                        Modifier.align(Alignment.BottomCenter)
                                            .fillMaxWidth().height(3.dp)
                                            .background(
                                                Color.Black.copy(alpha = 0.45f)
                                            )
                                    ) {
                                        Box(
                                            Modifier.fillMaxHeight()
                                                .fillMaxWidth(
                                                    if (durationMs > 0)
                                                        (positionMs.toFloat() / durationMs)
                                                            .coerceIn(0f, 1f)
                                                    else 0f
                                                )
                                                .background(Color.Red)
                                        )
                                    }
                                } else {
                                    Box(
                                        Modifier.fillMaxSize()
                                            .background(
                                                Color.Black.copy(alpha = 0.35f)
                                            )
                                    )
                                    // Top row: back + title in fullscreen,
                                    // settings + fullscreen always.
                                    Row(
                                        Modifier.align(Alignment.TopCenter)
                                            .fillMaxWidth().padding(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (fullscreen) {
                                            IconButton({ setFullscreen(false) }) {
                                                Icon(
                                                    Icons.Filled.ArrowBack,
                                                    "Back",
                                                    tint = Color.White
                                                )
                                            }
                                            Text(
                                                streams?.title?.ifBlank { title }
                                                    ?: title,
                                                color = Color.White,
                                                maxLines = 1,
                                                modifier = Modifier.weight(1f)
                                            )
                                        } else {
                                            Spacer(Modifier.weight(1f))
                                        }
                                        IconButton({ showSettings = true }) {
                                            Icon(
                                                Icons.Filled.Settings,
                                                "Settings",
                                                tint = Color.White
                                            )
                                        }
                                        IconButton({ setFullscreen(!fullscreen) }) {
                                            Icon(
                                                if (fullscreen)
                                                    Icons.Filled.FullscreenExit
                                                else Icons.Filled.Fullscreen,
                                                if (fullscreen) "Exit fullscreen"
                                                else "Fullscreen",
                                                tint = Color.White
                                            )
                                        }
                                    }
                                    // Centre: prev / play / next.
                                    Row(
                                        Modifier.align(Alignment.Center),
                                        horizontalArrangement =
                                            Arrangement.spacedBy(28.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        IconButton(
                                            { controller?.seekTo(0) },
                                            Modifier.size(48.dp)
                                        ) {
                                            Icon(
                                                Icons.Filled.SkipPrevious,
                                                "Previous",
                                                tint = Color.White,
                                                modifier = Modifier.size(40.dp)
                                            )
                                        }
                                        IconButton(
                                            {
                                                if (isPlaying) controller?.pause()
                                                else controller?.play()
                                            },
                                            Modifier.size(72.dp)
                                                .background(
                                                    Color.Black.copy(alpha = 0.45f),
                                                    CircleShape
                                                )
                                        ) {
                                            Icon(
                                                if (isPlaying) Icons.Filled.Pause
                                                else Icons.Filled.PlayArrow,
                                                if (isPlaying) "Pause" else "Play",
                                                tint = Color.White,
                                                modifier = Modifier.size(44.dp)
                                            )
                                        }
                                        IconButton(
                                            { playNext() },
                                            Modifier.size(48.dp),
                                            enabled = !upNext.isNullOrEmpty()
                                        ) {
                                            Icon(
                                                Icons.Filled.SkipNext,
                                                "Next",
                                                tint = Color.White,
                                                modifier = Modifier.size(40.dp)
                                            )
                                        }
                                    }
                                    // Bottom: seek + times.
                                    Column(
                                        Modifier.align(Alignment.BottomCenter)
                                            .fillMaxWidth()
                                            .padding(
                                                horizontal = 12.dp,
                                                vertical = 4.dp
                                            )
                                    ) {
                                        Slider(
                                            value = positionMs.toFloat(),
                                            onValueChange = {
                                                controller?.seekTo(it.toLong())
                                            },
                                            valueRange = 0f..durationMs.toFloat()
                                                .coerceAtLeast(1f),
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = SliderDefaults.colors(
                                                thumbColor = Color.Red,
                                                activeTrackColor = Color.Red,
                                                inactiveTrackColor =
                                                    Color.White.copy(alpha = 0.5f)
                                            )
                                        )
                                        Text(
                                            "${fmtTime(positionMs)} / " +
                                                fmtTime(durationMs),
                                            color = Color.White,
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.padding(start = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                else -> {
                    // Song mode, YT Music style: big square art,
                    // shuffle / prev / big play / next / repeat.
                    val s = streams!!
                    Column(
                        Modifier.fillMaxWidth()
                            .padding(horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(Modifier.height(8.dp))
                        AsyncImage(
                            model = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth(0.85f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(16.dp))
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            s.title.ifBlank { title },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            textAlign = TextAlign.Center
                        )
                        if (s.uploader.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                s.uploader,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Slider(
                            value = positionMs.toFloat(),
                            onValueChange = { controller?.seekTo(it.toLong()) },
                            valueRange = 0f..durationMs.toFloat()
                                .coerceAtLeast(1f),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                fmtTime(positionMs),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                fmtTime(durationMs),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton({ shuffle = !shuffle }) {
                                Icon(
                                    Icons.Filled.Shuffle,
                                    "Shuffle",
                                    tint = if (shuffle)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            IconButton({ controller?.seekTo(0) }) {
                                Icon(
                                    Icons.Filled.SkipPrevious,
                                    "Previous",
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                            IconButton(
                                {
                                    if (isPlaying) controller?.pause()
                                    else controller?.play()
                                },
                                Modifier.size(72.dp)
                                    .background(Color.White, CircleShape)
                            ) {
                                Icon(
                                    if (isPlaying) Icons.Filled.Pause
                                    else Icons.Filled.PlayArrow,
                                    if (isPlaying) "Pause" else "Play",
                                    tint = Color.Black,
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                            IconButton(
                                { playNext() },
                                enabled = !upNext.isNullOrEmpty()
                            ) {
                                Icon(
                                    Icons.Filled.SkipNext,
                                    "Next",
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                            IconButton({
                                repeatOne = !repeatOne
                                controller?.repeatMode =
                                    if (repeatOne) Player.REPEAT_MODE_ONE
                                    else Player.REPEAT_MODE_OFF
                            }) {
                                Icon(
                                    if (repeatOne) Icons.Filled.RepeatOne
                                    else Icons.Filled.Repeat,
                                    if (repeatOne) "Repeat one" else "Repeat",
                                    tint = if (repeatOne)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }
                }
            }
            // Below the player: channel row, UP NEXT tab, list.
            // Hidden (height 0) in fullscreen, same slot rule as the
            // toggle row above.
            if (streams != null && errorMsg == null) {
                Box(
                    Modifier.then(
                        if (fullscreen) Modifier.height(0.dp).clipToBounds()
                        else Modifier
                    ).fillMaxWidth()
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        Spacer(Modifier.height(12.dp))
                        Row(
                            Modifier.fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (upNextAvatar.isNotBlank()) {
                                AsyncImage(
                                    model = upNextAvatar,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(40.dp)
                                        .clip(CircleShape)
                                )
                            } else {
                                Box(
                                    Modifier.size(40.dp)
                                        .clip(CircleShape)
                                        .background(
                                            MaterialTheme.colorScheme
                                                .surfaceVariant
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        (upNextChannel.firstOrNull()
                                            ?: "?").toString(),
                                        color = MaterialTheme.colorScheme
                                            .onSurfaceVariant,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    upNextChannel.ifBlank {
                                        streams?.uploader ?: ""
                                    },
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                                val meta = listOf(upNextViews, upNextPublished)
                                    .filter { it.isNotBlank() }
                                    .joinToString(" • ")
                                if (meta.isNotBlank()) {
                                    Text(
                                        meta,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                            IconButton({ shareVideo() }) {
                                Icon(Icons.Filled.Share, "Share")
                            }
                            IconButton({ openInYouTube() }) {
                                Icon(
                                    Icons.Filled.OpenInNew,
                                    "Open in YouTube"
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(
                            Modifier.fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        ) {
                            Column {
                                Text(
                                    "UP NEXT",
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(4.dp))
                                Box(
                                    Modifier.height(2.dp).width(52.dp)
                                        .background(
                                            MaterialTheme.colorScheme.primary
                                        )
                                )
                            }
                        }
                        if (upNextChannel.isNotBlank()) {
                            Text(
                                "More from $upNextChannel",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(
                                    horizontal = 16.dp,
                                    vertical = 4.dp
                                )
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        val next = upNext
                        if (next == null) {
                            Box(
                                Modifier.fillMaxWidth().padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        } else if (next.isEmpty()) {
                            Text(
                                if (upNextChannel.isBlank())
                                    "No related videos found."
                                else "No more videos from this channel.",
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        } else {
                            LazyColumn(
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                    horizontal = 16.dp, vertical = 8.dp
                                ),
                                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(20.dp)
                            ) {
                                items(next, key = { it.id }) { v ->
                                    YtVideoCard(v) {
                                        YtQueue.title = v.title
                                        nav.navigate("ytplayer/${v.id}") {
                                            popUpTo("youtube")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
