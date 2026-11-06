package app.pepper.wansky.data

import app.pepper.wansky.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/** YouTube via the official Data API v3 (v0.2). The key comes from
 *  BuildConfig — injected at build time from the YOUTUBE_API_KEY
 *  Actions secret (or local.properties for local builds), never
 *  committed to the repo.
 *
 *  Quota (default 10,000 units/day, shared by ALL users): a search
 *  costs 100 units, a videos.list call costs 1. One search + details
 *  = 101 units, so ~99 searches/day across everyone. Trending uses
 *  videos.list only (1 unit), so the home feed is nearly free. */
object YouTube {
    private const val BASE = "https://www.googleapis.com/youtube/v3"
    private const val UA = "WanskyApp/0.2.0"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    data class Video(
        val id: String,
        val title: String,
        val channel: String,
        val thumb: String,
        val duration: String,
        val views: String,
        val published: String
    )

    /** Chip → (default query when the search box is empty, category id,
     *  short-only). Music/Sports/News use YouTube's own categories. */
    enum class Filter(
        val label: String,
        val defaultQuery: String,
        val categoryId: String?,
        val shortOnly: Boolean
    ) {
        ALL("All", "", null, false),
        SHORTS("Shorts", "shorts", null, true),
        MUSIC("Music", "music", "10", false),
        SPORTS("Sports", "sports", "17", false),
        NEWS("News", "news", "25", false)
    }

    private fun key(): String {
        val k = BuildConfig.YOUTUBE_API_KEY
        if (k.isBlank()) throw IllegalStateException("NO_KEY")
        return k
    }

    private suspend fun get(url: String): JSONObject = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).header("User-Agent", UA).build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string() ?: ""
            if (!resp.isSuccessful) {
                val reason = try {
                    JSONObject(body).optJSONObject("error")
                        ?.optJSONArray("errors")?.optJSONObject(0)
                        ?.optString("reason") ?: ""
                } catch (e: Exception) {
                    ""
                }
                throw IllegalStateException("YT_${resp.code}_$reason")
            }
            JSONObject(body)
        }
    }

    /** Search. An empty query falls back to the chip's default query. */
    suspend fun search(query: String, filter: Filter): List<Video> {
        val k = key()
        val q = query.ifBlank { filter.defaultQuery }
        val sb = StringBuilder("$BASE/search?part=snippet&type=video&maxResults=20")
        sb.append("&q=").append(URLEncoder.encode(q, "UTF-8"))
        filter.categoryId?.let { sb.append("&videoCategoryId=").append(it) }
        if (filter.shortOnly) sb.append("&videoDuration=short")
        sb.append("&key=").append(k)
        val ids = parseSearchIds(get(sb.toString()))
        if (ids.isEmpty()) return emptyList()
        return details(ids)
    }

    /** Bangladesh trending — cheap (1 quota unit), so this is the
     *  default feed when nothing has been searched yet. */
    suspend fun trending(): List<Video> {
        val k = key()
        val json = get(
            "$BASE/videos?part=snippet,contentDetails,statistics" +
                "&chart=mostPopular&regionCode=BD&maxResults=20&key=$k"
        )
        return parseVideos(json)
    }

    /** Videos related to one video — the "Up next" list. */
    suspend fun related(videoId: String): List<Video> {
        val k = key()
        val json = get(
            "$BASE/search?part=snippet&type=video" +
                "&relatedToVideoId=$videoId&maxResults=20&key=$k"
        )
        val ids = parseSearchIds(json)
        if (ids.isEmpty()) return emptyList()
        return details(ids)
    }

    private suspend fun details(ids: List<String>): List<Video> {
        val k = key()
        val json = get(
            "$BASE/videos?part=snippet,contentDetails,statistics" +
                "&id=${ids.joinToString(",")}&key=$k"
        )
        return parseVideos(json)
    }

    private fun parseSearchIds(json: JSONObject): List<String> {
        val out = mutableListOf<String>()
        val arr = json.optJSONArray("items") ?: return out
        for (i in 0 until arr.length()) {
            val id = arr.optJSONObject(i)?.optJSONObject("id")?.optString("videoId")
            if (!id.isNullOrBlank()) out.add(id)
        }
        return out
    }

    private fun parseVideos(json: JSONObject): List<Video> {
        val out = mutableListOf<Video>()
        val arr = json.optJSONArray("items") ?: return out
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val sn = item.optJSONObject("snippet") ?: continue
            val thumbs = sn.optJSONObject("thumbnails")
            val thumb = thumbs?.optJSONObject("medium")?.optString("url")
                ?: thumbs?.optJSONObject("high")?.optString("url")
                ?: thumbs?.optJSONObject("default")?.optString("url")
                ?: ""
            out.add(
                Video(
                    id = item.optString("id"),
                    title = unescape(sn.optString("title")),
                    channel = unescape(sn.optString("channelTitle")),
                    thumb = thumb,
                    duration = formatDuration(
                        item.optJSONObject("contentDetails")?.optString("duration") ?: ""
                    ),
                    views = formatViews(
                        item.optJSONObject("statistics")?.optString("viewCount") ?: ""
                    ),
                    published = relativeTime(sn.optString("publishedAt"))
                )
            )
        }
        return out
    }

    private fun unescape(s: String): String = s
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")

    /** PT1H2M3S / PT12M34S / PT45S → 1:02:03 / 12:34 / 0:45. */
    fun formatDuration(iso: String): String {
        if (iso.isBlank()) return ""
        val h = Regex("""(\d+)H""").find(iso)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val m = Regex("""(\d+)M""").find(iso)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val s = Regex("""(\d+)S""").find(iso)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    fun formatViews(raw: String): String {
        val n = raw.toLongOrNull() ?: return ""
        return when {
            n >= 1_000_000 -> "%.1fM views".format(n / 1_000_000.0)
            n >= 1_000 -> "%.1fK views".format(n / 1_000.0)
            else -> "$n views"
        }
    }

    fun relativeTime(iso: String): String {
        return try {
            val then = java.time.Instant.parse(iso).toEpochMilli()
            val diff = System.currentTimeMillis() - then
            val days = diff / 86_400_000
            when {
                days >= 365 -> "${days / 365}y ago"
                days >= 30 -> "${days / 30}mo ago"
                days >= 1 -> "${days}d ago"
                diff / 3_600_000 >= 1 -> "${diff / 3_600_000}h ago"
                diff / 60_000 >= 1 -> "${diff / 60_000}m ago"
                else -> "just now"
            }
        } catch (e: Exception) {
            ""
        }
    }

    /** Turns a raw API failure into words the user can act on. */
    fun friendlyError(e: Exception): String {
        val m = e.message ?: ""
        return when {
            m == "NO_KEY" -> "The YouTube key isn't in this build yet — it needs the YOUTUBE_API_KEY build secret."
            m.contains("quotaExceeded") -> "YouTube's daily limit is reached — try again tomorrow."
            m.contains("keyInvalid") || m.contains("API_KEY_INVALID") -> "The YouTube key was rejected — it may need to be remade."
            m.startsWith("YT_403") -> "YouTube refused the request (key or quota) — try again later."
            m.startsWith("YT_") -> "YouTube didn't answer — check your connection and try again."
            else -> "YouTube didn't answer: ${e.message}"
        }
    }
}
