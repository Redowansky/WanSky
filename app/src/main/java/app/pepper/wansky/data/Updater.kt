package app.pepper.wansky.data

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
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
 *  can never update over each other — that's the conflict he hit. */
object Updater {
    const val VERSION_URL =
        "https://pub-89e47025e7844f1eaf672a00cfdbba12.r2.dev/app/wansky-version.json"
    private const val UA = "WanskyApp/0.2.2"

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

    /** Downloads the APK, then opens the installer when it finishes.
     *  If the app is killed mid-download, the finished file stays in
     *  DownloadManager and the user can tap its notification to
     *  install. Returns false only when the download can't start. */
    fun downloadAndInstall(ctx: Context, apkUrl: String): Boolean {
        return try {
            val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val req = DownloadManager.Request(Uri.parse(apkUrl))
                .setTitle("Wansky update")
                .setDescription("Downloading the new version…")
                .setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                )
                .setDestinationInExternalFilesDir(
                    ctx, Environment.DIRECTORY_DOWNLOADS, "wansky-update.apk"
                )
                .setMimeType("application/vnd.android.package-archive")
            val id = dm.enqueue(req)
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context, intent: Intent) {
                    if (intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) != id) return
                    try {
                        ctx.unregisterReceiver(this)
                    } catch (e: Exception) {
                    }
                    val file = File(
                        ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
                        "wansky-update.apk"
                    )
                    if (!file.exists()) return
                    val apkUri = FileProvider.getUriForFile(
                        ctx, "app.pepper.wansky.fileprovider", file
                    )
                    val install = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(apkUri, "application/vnd.android.package-archive")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                    }
                    ctx.startActivity(install)
                }
            }
            ContextCompat.registerReceiver(
                ctx, receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            true
        } catch (e: Exception) {
            false
        }
    }
}
