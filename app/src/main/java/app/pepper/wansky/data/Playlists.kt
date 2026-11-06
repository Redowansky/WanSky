package app.pepper.wansky.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** Live TV playlists, fetched on-device. The 103 BDIX list only
 *  answers Bangladesh IPs — it fails on other networks, which the
 *  UI reports per-playlist instead of failing the whole load. */
object Playlists {
    const val URL_103 = "http://103.229.254.25:7001/playlist.m3u8"
    const val URL_BDIX = "https://raw.githubusercontent.com/Sopnnonil775/Bdix3/refs/heads/main/Bdix.m3u"
    const val URL_IPTVORG = "https://iptv-org.github.io/iptv/index.m3u"

    val ALL = listOf(
        "103 BDIX" to URL_103,
        "Bdix" to URL_BDIX,
        "iptv-org" to URL_IPTVORG
    )

    data class Channel(
        val name: String,
        val group: String,
        val logo: String?,
        val url: String,
        val source: String
    )

    data class LoadResult(val channels: List<Channel>, val errors: List<String>)

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private var cached: LoadResult? = null

    suspend fun loadAll(force: Boolean = false): LoadResult {
        cached?.let { if (!force) return it }
        val parts = coroutineScope {
            ALL.map { (label, url) ->
                async(Dispatchers.IO) {
                    try {
                        val req = Request.Builder().url(url)
                            .header("User-Agent", "WanskyApp/0.2.1").build()
                        client.newCall(req).execute().use { resp ->
                            if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
                            parseM3u(resp.body?.string().orEmpty(), label) to (null as String?)
                        }
                    } catch (e: Exception) {
                        emptyList<Channel>() to "$label: ${e.message ?: e.javaClass.simpleName}"
                    }
                }
            }.awaitAll()
        }
        val result = LoadResult(
            channels = parts.flatMap { it.first },
            errors = parts.mapNotNull { it.second }
        )
        cached = result
        return result
    }

    fun isSports(c: Channel): Boolean {
        val g = c.group.lowercase()
        val n = c.name.lowercase()
        return "sport" in g || "sport" in n || "cricket" in n || "football" in n
    }

    /** Tolerant M3U parser. The iptv-org list contains malformed
     *  #EXTINF lines (commas inside quoted fields), so the channel
     *  name is taken after the LAST comma, and entries with no
     *  usable stream URL are skipped instead of breaking the parse. */
    fun parseM3u(text: String, source: String): List<Channel> {
        val lines = text.lineSequence().map { it.trim() }.toList()
        val out = mutableListOf<Channel>()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            if (line.startsWith("#EXTINF")) {
                val group = Regex("group-title=\"([^\"]*)\"")
                    .find(line)?.groupValues?.getOrNull(1).orEmpty()
                val logo = Regex("tvg-logo=\"([^\"]*)\"")
                    .find(line)?.groupValues?.getOrNull(1)
                val name = line.substringAfterLast(",", "").trim()
                var j = i + 1
                var url: String? = null
                while (j < lines.size) {
                    val cand = lines[j]
                    if (cand.isNotEmpty() && !cand.startsWith("#")) {
                        url = cand
                        break
                    }
                    j++
                }
                if (name.isNotBlank() && url != null &&
                    (url.startsWith("http://") || url.startsWith("https://"))
                ) {
                    out.add(Channel(name, group.ifBlank { "General" }, logo?.ifBlank { null }, url, source))
                }
                i = j
            } else {
                i++
            }
        }
        return out
    }
}
