package app.pepper.wansky.ui

import android.content.Context
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Captures the stack trace of an uncaught crash to a file, so the
 *  next launch can show it (and the user can paste it to Pepper).
 *  The app still crashes the same way — this only records the cause. */
object CrashTrap {
    private const val FILE = "wansky-crash.log"

    fun install(ctx: Context) {
        val appCtx = ctx.applicationContext
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                val sw = StringWriter()
                e.printStackTrace(PrintWriter(sw))
                val stamp =
                    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                appCtx.openFileOutput(FILE, Context.MODE_PRIVATE).use {
                    it.write(("Wansky crash at $stamp\n$sw").toByteArray())
                }
            } catch (_: Exception) {
                // Never let the recorder itself throw.
            }
            prev?.uncaughtException(t, e)
        }
    }

    fun read(ctx: Context): String? = try {
        ctx.openFileInput(FILE).bufferedReader().readText()
    } catch (_: Exception) {
        null
    }

    fun clear(ctx: Context) {
        try {
            ctx.deleteFile(FILE)
        } catch (_: Exception) {
        }
    }
}
