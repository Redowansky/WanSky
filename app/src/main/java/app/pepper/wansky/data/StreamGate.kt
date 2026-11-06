package app.pepper.wansky.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/** Signed stream URLs — same flow as the Library app (v0.6.28):
 *  main-bucket files play through /stream/<key>?t=<token>, minted at
 *  play time; anything else plays direct. Tokens live 24h. */
object StreamGate {
    const val MEDIA_PUBLIC_PREFIX =
        "https://pub-88baf9e209354ab5b4edee733305154f.r2.dev/"
    const val STREAM_BASE = "https://redowansky.bond/stream/"
    const val TOKEN_URL = "https://redowansky.bond/api/play-token"
    const val UA = "WanskyApp/0.1.0"

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    /** R2 key for a media-bucket URL, or null for anything else. */
    fun keyOf(url: String): String? {
        if (!url.startsWith(MEDIA_PUBLIC_PREFIX)) return null
        val key = url.removePrefix(MEDIA_PUBLIC_PREFIX).substringBefore("?")
        if (key.isBlank() || key.split("/").any { it == ".." }) return null
        return key
    }

    fun streamPath(key: String, token: String): String {
        val encoded = key.split("/").joinToString("/") {
            URLEncoder.encode(it, "UTF-8").replace("+", "%20")
        }
        return STREAM_BASE + encoded + "?t=" + token
    }

    /** Resolve a catalog URL to something ExoPlayer can play.
     *  Returns null only when minting fails. */
    suspend fun resolve(url: String): String? {
        val key = keyOf(url) ?: return url
        return try {
            withContext(Dispatchers.IO) {
                val req = Request.Builder()
                    .url(TOKEN_URL + "?key=" + URLEncoder.encode(key, "UTF-8"))
                    .header("User-Agent", UA)
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    val json = JSONObject(resp.body?.string() ?: return@use null)
                    val stream = json.optString("stream")
                    if (stream.isNotBlank()) {
                        "https://redowansky.bond" + stream
                    } else {
                        val token = json.optString("token")
                        if (token.isBlank()) null else streamPath(key, token)
                    }
                }
            }
        } catch (e: Exception) {
            null
        }
    }
}
