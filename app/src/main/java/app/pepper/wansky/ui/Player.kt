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

/** YouTube player (v0.2): the embed in a 16:9 box on top, then an
 *  "Up next" list of related videos. Tapping one swaps the player
 *  (the old player leaves the back stack, so Back returns to the
 *  YouTube tab). Fullscreen expand inside the embed isn't wired in
 *  v0.2 — the video plays inline.
 *
 *  Error 153 fix (v0.2.6): YouTube has enforced, since late 2025,
 *  that an embedded player must identify its embedder through the
 *  HTTP Referer header. Loading the embed URL directly with
 *  loadUrl() is a top-level navigation with NO embedding page, so
 *  YouTube sees no Referer and refuses every video with "Video
 *  player configuration error" (Error 153). The fix is to wrap the
 *  iframe in a real HTML document and load it with a base URL, so
 *  the iframe request carries our domain as Referer — and to pass
 *  the same origin in the iframe's origin= parameter. The base URL
 *  must be a real domain we own (redowansky.bond); YouTube's own
 *  domain as the embedding origin is refused too (Error 152). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YtPlayerScreen(nav: NavController, videoId: String) {
    val title = YtQueue.title ?: "YouTube"
    var upNext by remember { mutableStateOf<List<YouTube.Video>?>(null) }
    var upNextChannel by remember { mutableStateOf("") }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    WebViewPlayerLifecycle { webViewRef }

    LaunchedEffect(videoId) {
        upNext = try {
            val res = YouTube.related(videoId)
            upNextChannel = res.channel
            res.videos
        } catch (e: Exception) {
            emptyList()
        }
    }

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
        Column(Modifier.padding(inner).fillMaxSize()) {
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
                        // The iframe lives in a wrapper document whose
                        // base URL is our own domain — that is what
                        // YouTube reads as the embedder (see the
                        // Error 153 note above). Never load the embed
                        // URL directly with loadUrl().
                        val html = """
                            <!DOCTYPE html><html><head>
                            <meta name="viewport" content="width=device-width, initial-scale=1">
                            <style>html,body{margin:0;padding:0;background:#000;height:100%}iframe{width:100%;height:100%;border:0}</style>
                            </head><body>
                            <iframe src="https://www.youtube.com/embed/$videoId?autoplay=1&rel=0&origin=https%3A%2F%2Fredowansky.bond"
                             allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share"
                             allowfullscreen></iframe>
                            </body></html>
                        """.trimIndent()
                        loadDataWithBaseURL(
                            "https://redowansky.bond", html,
                            "text/html", "UTF-8", null
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
            )
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
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
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
