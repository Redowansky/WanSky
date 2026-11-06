package app.pepper.wansky.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** His library catalog (redowansky.bond). Wansky sends its own
 *  User-Agent, so the Library app's server-side version gate (which
 *  only matches the RedowanskyLibraryApp UA) does not apply. */
object Catalog {
    const val API_URL = "https://redowansky.bond/api/movies"
    const val UA = "WanskyApp/0.2.5"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private var cached: List<Movie>? = null

    suspend fun fetch(force: Boolean = false): List<Movie> {
        cached?.let { if (!force) return it }
        val list = withContext(Dispatchers.IO) {
            val req = Request.Builder().url(API_URL).header("User-Agent", UA).build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IllegalStateException("Catalog HTTP ${resp.code}")
                val body = resp.body?.string() ?: throw IllegalStateException("Empty catalog")
                val arr = JSONObject(body).optJSONArray("movies")
                    ?: throw IllegalStateException("Bad catalog response")
                val out = mutableListOf<Movie>()
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let { out.add(Movie.fromJson(it)) }
                }
                out
            }
        }
        cached = list
        return list
    }

    fun byId(id: String): Movie? = cached?.find { it.id == id }
}
