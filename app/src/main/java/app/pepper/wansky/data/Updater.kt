package app.pepper.wansky.data

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/** In-app updates (v0.2.1): the app checks a small version.json on R2
 *  at Home launch. If the remote versionCode is newer, Home shows an
 *  "Update available" banner; tapping Update downloads the APK with
 *  DownloadManager and opens the system installer. No more manual
 *  uninstall/reinstall from the Downloads folder.
 *
 *  The check is fail-open: any failure (offline, bad JSON) just means
 *  no banner. The notes stay generic by his rule — never a changelog.
 *
 *  This only works end-to-end because v0.2.1+ are SIGNED release
 *  builds from one stable keystore: the installer then updates the
 *  app in place. Debug builds (a fresh throwaway key per cloud run)
 *  can never update over each other — that's the conflict he hit.
 *
 *  v0.2.3 lesson: v0.2.1 waited for DownloadManager's "download
 *  finished" broadcast to open the installer — on his phone that
 *  broadcast never arrived, so the banner sat on "Downloading…"
 *  forever with no progress and no error. The download is now
 *  POLLED once a second from DownloadManager itself (status, bytes
 *  downloaded, failure reason), which is the source of truth, and
 *  the banner shows live progress. The broadcast is gone entirely. */
object Updater {
    const val VERSION_URL =
        "https://pub-89e47025e7844f1eaf672a00cfdbba12.r2.dev/app/wansky-version.json"
    private const val UA = "WanskyApp/0.2.3"

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

    /** Live state of one update download, polled from DownloadManager. */
    sealed interface DownloadState {
        data object Idle : DownloadState
        data class Progress(val done: Long, val total: Long) : DownloadState
        data class Failed(val reason: String) : DownloadState
        data object Done : DownloadState
    }

    /** Starts the download, then polls its status once a second until
     *  it finishes or fails, reporting each state through onState
     *  (always called on the main thread). On success the installer
     *  opens automatically. If the user leaves Home mid-download the
     *  polling stops but the system download keeps going — its own
     *  notification can be tapped to install. */
    suspend fun download(
        ctx: Context, apkUrl: String, onState: (DownloadState) -> Unit
    ) {
        onState(DownloadState.Idle)
        val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val id = try {
            dm.enqueue(
                DownloadManager.Request(Uri.parse(apkUrl))
                    .setTitle("Wansky update")
                    .setDescription("Downloading the new version…")
                    .setNotificationVisibility(
                        DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                    )
                    .setDestinationInExternalFilesDir(
                        ctx, Environment.DIRECTORY_DOWNLOADS, "wansky-update.apk"
                    )
                    .setMimeType("application/vnd.android.package-archive")
            )
        } catch (e: Exception) {
            onState(DownloadState.Failed(e.message ?: "couldn't start"))
            return
        }
        while (true) {
            val snap = withContext(Dispatchers.IO) {
                val c = dm.query(DownloadManager.Query().setFilterById(id))
                var status = DownloadManager.STATUS_FAILED
                var done = 0L
                var total = -1L
                var reason = 0
                if (c != null) {
                    if (c.moveToFirst()) {
                        status = c.getInt(
                            c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
                        )
                        done = c.getLong(
                            c.getColumnIndexOrThrow(
                                DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR
                            )
                        )
                        total = c.getLong(
                            c.getColumnIndexOrThrow(
                                DownloadManager.COLUMN_TOTAL_SIZE_BYTES
                            )
                        )
                        reason = c.getInt(
                            c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)
                        )
                    }
                    c.close()
                }
                Triple(status, done to total, reason)
            }
            val (status, doneTotal, reason) = snap
            val (done, total) = doneTotal
            when (status) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    onState(DownloadState.Done)
                    installDownloaded(ctx)
                    return
                }
                DownloadManager.STATUS_FAILED -> {
                    onState(DownloadState.Failed(reasonText(reason)))
                    return
                }
                else -> {
                    onState(DownloadState.Progress(done, total))
                    delay(1000)
                }
            }
        }
    }

    private fun reasonText(reason: Int): String = when (reason) {
        DownloadManager.ERROR_CANNOT_RESUME -> "download can't resume"
        DownloadManager.ERROR_DEVICE_NOT_FOUND -> "storage not found"
        DownloadManager.ERROR_FILE_ALREADY_EXISTS -> "file already exists"
        DownloadManager.ERROR_FILE_ERROR -> "couldn't write the file"
        DownloadManager.ERROR_HTTP_DATA_ERROR -> "network error while downloading"
        DownloadManager.ERROR_INSUFFICIENT_SPACE -> "not enough storage"
        DownloadManager.ERROR_TOO_MANY_REDIRECTS -> "too many redirects"
        DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "server refused the download"
        DownloadManager.ERROR_UNKNOWN -> "unknown error"
        else -> "error $reason"
    }

    /** Opens the system installer for the downloaded APK. */
    private fun installDownloaded(ctx: Context) {
        val file = File(
            ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            "wansky-update.apk"
        )
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
                "Couldn't open the installer — tap the finished download notification instead.",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}
