package app.pepper.wansky.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/** In-app updates (v0.2.1): the app checks a small version.json on R2
 *  at Home launch. If the remote versionCode is newer, Home shows an
 *  "Update available" banner; tapping Update downloads the APK and
 *  opens the system installer. No more manual uninstall/reinstall
 *  from the Downloads folder.
 *
 *  The check is fail-open: any failure (offline, bad JSON) just means
 *  no banner. The notes stay generic by his rule — never a changelog.
 *
 *  This only works end-to-end because v0.2.1+ are SIGNED release
 *  builds from one stable keystore: the installer then updates the
 *  app in place. Debug builds (a fresh throwaway key per cloud run)
 *  can never update over each other — that's the conflict he hit.
 *
 *  v0.2.5 lesson — DownloadManager is dead on his phone, twice over:
 *  v0.2.1 waited for its "download finished" broadcast (never came);
 *  v0.2.3 polled it once a second (still stuck at "Downloading…
 *  0.0 MB" — no traffic ever moved, no error ever surfaced). This
 *  is the EXACT failure the Redowansky Library app hit and fixed in
 *  v0.6.6: its UpdateInstaller documents that DownloadManager on
 *  this phone starts nothing, shows nothing, and fails silently.
 *  The fix, same as the Library app: download DIRECTLY with OkHttp
 *  — the same network path the app already uses for the catalog and
 *  the version check — stream it to a file with live progress, and
 *  on ANY failure report the exact error in the banner (Retry) so
 *  it can be screenshotted, instead of going quiet. DownloadManager
 *  is gone entirely. */
object Updater {
    const val VERSION_URL =
        "https://pub-89e47025e7844f1eaf672a00cfdbba12.r2.dev/app/wansky-version.json"
    private const val UA = "WanskyApp/0.2.5"

    data class UpdateInfo(
        val versionCode: Int,
        val versionName: String,
        val apkUrl: String,
        val notes: String
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /** Returns the remote info, or null when it can't be read. */
    suspend fun check(): UpdateInfo? = try {
        withContext(Dispatchers.IO) {
            val req = Request.Builder().url(VERSION_URL).header("User-Agent", UA).build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val json = JSONObject(resp.body?.string() ?: return@withContext null)
                UpdateInfo(
                    versionCode = json.optInt("versionCode", 0),
                    versionName = json.optString("versionName", ""),
                    apkUrl = json.optString("apkUrl", ""),
                    notes = json.optString("notes", "Fixed some bugs and improvements.")
                ).takeIf { it.versionCode > 0 && it.apkUrl.isNotBlank() }
            }
        }
    } catch (e: Exception) {
        null
    }

    /** Live state of one update download. */
    sealed interface DownloadState {
        data object Idle : DownloadState
        data class Progress(val done: Long, val total: Long) : DownloadState
        data class Failed(val reason: String) : DownloadState
        data object Done : DownloadState
    }

    /** Downloads the APK directly with OkHttp (no DownloadManager),
     *  reporting each state through onState (always on the main
     *  thread). On success the installer opens automatically. */
    suspend fun download(
        ctx: Context, apkUrl: String, onState: (DownloadState) -> Unit
    ) {
        onState(DownloadState.Idle)
        // First run needs the one-time "allow installs" toggle; the
        // installer would otherwise fail with no useful message.
        if (android.os.Build.VERSION.SDK_INT >= 26 &&
            !ctx.packageManager.canRequestPackageInstalls()
        ) {
            Toast.makeText(
                ctx,
                "Allow installs for Wansky once, then tap Update again",
                Toast.LENGTH_LONG
            ).show()
            try {
                ctx.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${ctx.packageName}")
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (_: Exception) {
            }
            return
        }
        val dir = ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: ctx.cacheDir
        val tmp = File(dir, "wansky-update.apk.tmp")
        val file = File(dir, "wansky-update.apk")
        try {
            val dlClient = client.newBuilder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build()
            val req = Request.Builder().url(apkUrl)
                .header("User-Agent", UA)
                .header("Cache-Control", "no-cache")
                .build()
            val (downloaded, total) = withContext(Dispatchers.IO) {
                dlClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        throw java.io.IOException("server refused (HTTP ${resp.code})")
                    }
                    val body = resp.body ?: throw java.io.IOException("empty response")
                    val totalBytes = body.contentLength()
                    var done = 0L
                    var lastPosted = 0L
                    body.byteStream().use { input ->
                        FileOutputStream(tmp).use { out ->
                            val buf = ByteArray(64 * 1024)
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n)
                                done += n
                                if (done - lastPosted >= 256 * 1024) {
                                    lastPosted = done
                                    withContext(Dispatchers.Main) {
                                        onState(DownloadState.Progress(done, totalBytes))
                                    }
                                }
                            }
                        }
                    }
                    done to totalBytes
                }
            }
            if (downloaded == 0L) throw java.io.IOException("empty download (0 bytes)")
            if (total > 0 && downloaded != total) {
                throw java.io.IOException("incomplete download ($downloaded / $total bytes)")
            }
            tmp.renameTo(file)
            onState(DownloadState.Done)
            installDownloaded(ctx, file)
        } catch (e: Exception) {
            try { tmp.delete() } catch (_: Exception) {}
            onState(
                DownloadState.Failed(
                    "${e.javaClass.simpleName}: ${e.message ?: "no detail"}"
                )
            )
        }
    }

    /** Opens the system installer for the downloaded APK. */
    private fun installDownloaded(ctx: Context, file: File) {
        if (!file.exists()) {
            Toast.makeText(
                ctx,
                "Download finished but the file is missing — try again.",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        try {
            val apkUri = FileProvider.getUriForFile(
                ctx, "app.pepper.wansky.fileprovider", file
            )
            val install = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            ctx.startActivity(install)
        } catch (e: Exception) {
            Toast.makeText(
                ctx,
                "Couldn't open the installer: ${e.message ?: "unknown error"} — " +
                    "the APK is in the app's Download folder.",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}
