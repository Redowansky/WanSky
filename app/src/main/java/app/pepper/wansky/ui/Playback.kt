package app.pepper.wansky.ui

import androidx.navigation.NavController
import app.pepper.wansky.data.Movie
import app.pepper.wansky.data.Playlists
import app.pepper.wansky.data.StreamGate
import app.pepper.wansky.data.isEmbedUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Play a catalog title: embeds go to the WebView player, files get a
 *  signed stream URL first. onError reports failures to the caller
 *  instead of silently doing nothing. */
fun playMovie(
    scope: CoroutineScope,
    nav: NavController,
    movie: Movie,
    sourceIndex: Int = 0,
    onError: (String) -> Unit = {}
) {
    val src = movie.sources.getOrNull(sourceIndex) ?: return
    val label = "${movie.title} — ${src.label}"
    if (isEmbedUrl(src.url)) {
        PlayQueue.webUrl = src.url
        PlayQueue.title = label
        nav.navigate("webview")
        return
    }
    scope.launch {
        val resolved = StreamGate.resolve(src.url)
        if (resolved == null) {
            onError("Couldn't start playback — check your connection and try again.")
            return@launch
        }
        PlayQueue.url = resolved
        PlayQueue.title = label
        nav.navigate("player")
    }
}

/** Play a live channel (raw stream URL, no token needed). */
fun playChannel(nav: NavController, c: Playlists.Channel) {
    PlayQueue.url = c.url
    PlayQueue.title = c.name
    nav.navigate("player")
}

/** The language badge on a poster: "Hindi" / "English" when the
 *  catalog entry makes it obvious, else null. */
fun Movie.language(): String? {
    val first = sources.firstOrNull() ?: return null
    if ("/hindi/" in first.url) return "Hindi"
    if ("/english/" in first.url) return "English"
    val l = first.label.lowercase()
    if (l == "hindi") return "Hindi"
    if (l == "english") return "English"
    return null
}
