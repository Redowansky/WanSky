package app.pepper.wansky.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.AgeRestrictedContentException
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.exceptions.GeographicRestrictionException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Resolved playable streams for one YouTube video (v0.2.8). The
 *  native ExoPlayer plays these directly — no WebView, so the black
 *  video box, Error 153 and ghost audio all go away at once. */
data class YtStreams(
    val title: String,
    val uploader: String,
    val durationSecs: Long,
    /** Adaptive DASH manifest (video+audio) — preferred: one item
     *  serves both Video and Song mode (Song just disables the video
     *  track, no rebuffer on toggle). */
    val dashUrl: String,
    /** Best progressive video+audio stream ≤1080p (fallback when the
     *  video has no DASH manifest). */
    val videoUrl: String,
    /** Best audio-only stream (Song mode fallback when no DASH). */
    val audioUrl: String
)

/** Thrown when a video cannot be resolved — carries a message that is
 *  safe to show to the user as-is. */
class YtExtractException(message: String) : Exception(message)

object YtExtractor {
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var inited = false

    /** NewPipe's Downloader backed by the app's OkHttp client. */
    private class OkHttpDownloader : Downloader() {
        override fun execute(request: Request): Response {
            val builder = okhttp3.Request.Builder()
                .url(request.url())
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 14; Pixel 8) " +
                        "AppleWebKit/537.36 (KHTML, like Gecko) " +
                        "Chrome/126.0.0.0 Mobile Safari/537.36"
                )
            for ((name, values) in request.headers()) {
                for (value in values) builder.addHeader(name, value)
            }
            val data = request.dataToSend()
            builder.method(request.httpMethod(), data?.toRequestBody(null))
            http.newCall(builder.build()).execute().use { res ->
                return Response(
                    res.code,
                    res.message,
                    res.headers.toMultimap(),
                    res.body?.string() ?: "",
                    res.request.url.toString()
                )
            }
        }
    }

    private fun initOnce() {
        if (inited) return
        synchronized(this) {
            if (!inited) {
                NewPipe.init(OkHttpDownloader())
                inited = true
            }
        }
    }

    /** Resolve a video id to playable stream URLs. Synchronous
     *  extraction, so it always runs on Dispatchers.IO. */
    suspend fun extract(videoId: String): YtStreams = withContext(Dispatchers.IO) {
        initOnce()
        val info = try {
            StreamInfo.getInfo("https://www.youtube.com/watch?v=$videoId")
        } catch (e: AgeRestrictedContentException) {
            throw YtExtractException(
                "This video is age-restricted and can't play inside Wansky. " +
                    "Open it in YouTube instead."
            )
        } catch (e: GeographicRestrictionException) {
            throw YtExtractException(
                "This video isn't available in your country."
            )
        } catch (e: ReCaptchaException) {
            throw YtExtractException(
                "YouTube asked for a check before playing this one. " +
                    "Open it in YouTube instead."
            )
        } catch (e: ExtractionException) {
            throw YtExtractException("Couldn't load this video: ${e.message}")
        } catch (e: IOException) {
            throw YtExtractException("Couldn't reach YouTube. Check your connection.")
        }

        val dashUrl = try { info.dashMpdUrl ?: "" } catch (e: Exception) { "" }

        // Best muxed (video+audio) progressive stream, 1080p max.
        var bestVideo: String? = null
        var bestHeight = 0
        for (vs in info.videoStreams) {
            if (vs.isVideoOnly) continue
            val height = vs.resolution
                .takeWhile { it.isDigit() }.toIntOrNull() ?: continue
            if (height in 1..1080 && height > bestHeight) {
                bestHeight = height
                bestVideo = vs.url
            }
        }
        // Fall back to the highest muxed stream of any height.
        if (bestVideo == null) {
            for (vs in info.videoStreams) {
                if (!vs.isVideoOnly) { bestVideo = vs.url; break }
            }
        }

        // Best audio-only stream by bitrate.
        var bestAudio: String? = null
        var bestBitrate = 0
        for (audio in info.audioStreams) {
            if (audio.averageBitrate > bestBitrate) {
                bestBitrate = audio.averageBitrate
                bestAudio = audio.url
            }
        }

        if (dashUrl.isBlank() && bestVideo == null && bestAudio == null) {
            throw YtExtractException("No playable stream found for this video.")
        }
        YtStreams(
            title = info.name ?: "",
            uploader = info.uploaderName ?: "",
            durationSecs = info.duration,
            dashUrl = dashUrl,
            videoUrl = bestVideo ?: "",
            audioUrl = bestAudio ?: ""
        )
    }
}
