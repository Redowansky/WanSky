package app.pepper.wansky.ui

import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Slider
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.layout.ContentScale
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaMetadata
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
 *  audio — the v0.2.7 rule); Song mode deliberately does not. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YtPlayerScreen(nav: NavController, videoId: String) {
    val ctx = LocalContext.current
    val title = YtQueue.title ?: "YouTube"
    var upNext by remember { mutableStateOf<List<YouTube.Video>?>(null) }
    var upNextChannel by remember { mutableStateOf("") }
    var streams by remember { mutableStateOf<YtStreams?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var retryKey by remember { mutableStateOf(0) }
    var mode by remember { mutableStateOf(YtMode.Video) }
    var controller by remember { mutableStateOf<MediaController?>(null) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var isPlaying by remember { mutableStateOf(false) }
    var notifAsked by remember { mutableStateOf(false) }

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
                c.play()
            }
        }
    }

    LaunchedEffect(videoId) {
        upNext = try {
            val res = YouTube.related(videoId)
            upNextChannel = res.channel
            res.videos
        } catch (e: Exception) {
            emptyList()
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

    Scaffold(
        topBar = {
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
    ) { inner ->
        Column(Modifier.padding(inner).fillMaxSize()) {
            // Song / Video toggle (Hamyra-style Music mode).
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
                            Button({
                                ctx.startActivity(
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse(
                                            "https://www.youtube.com/watch?v=$videoId"
                                        )
                                    )
                                )
                            }) { Text("Open in YouTube") }
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
                    AndroidView(
                        factory = { c ->
                            PlayerView(c).apply { useController = true }
                        },
                        update = { it.player = controller },
                        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                    )
                }
                else -> {
                    // Song mode: big art + controls, no video surface.
                    val s = streams!!
                    Column(
                        Modifier.fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        AsyncImage(
                            model = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth()
                                .aspectRatio(16f / 9f)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            s.title.ifBlank { title },
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 2
                        )
                        if (s.uploader.isNotBlank()) {
                            Text(
                                s.uploader,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Spacer(Modifier.height(8.dp))
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
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton({
                                if (isPlaying) controller?.pause()
                                else controller?.play()
                            }) {
                                Icon(
                                    if (isPlaying) Icons.Filled.Pause
                                    else Icons.Filled.PlayArrow,
                                    if (isPlaying) "Pause" else "Play"
                                )
                            }
                            IconButton(
                                onClick = {
                                    val next = upNext?.firstOrNull()
                                    if (next != null) {
                                        YtQueue.title = next.title
                                        nav.navigate("ytplayer/${next.id}") {
                                            popUpTo("youtube")
                                        }
                                    }
                                },
                                enabled = !upNext.isNullOrEmpty()
                            ) {
                                Icon(Icons.Filled.SkipNext, "Next")
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                if (upNextChannel.isBlank()) "Up next"
                else "More from $upNextChannel",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(Modifier.height(8.dp))
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
                    if (upNextChannel.isBlank()) "No related videos found."
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
