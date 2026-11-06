package app.pepper.wansky.data

import android.content.Context

/** My List — favorite movie ids, kept in SharedPreferences. */
object Favorites {
    private const val PREFS = "wansky_favs"
    private const val KEY = "ids"

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun ids(ctx: Context): Set<String> =
        prefs(ctx).getStringSet(KEY, emptySet()) ?: emptySet()

    fun isFav(ctx: Context, id: String): Boolean = ids(ctx).contains(id)

    /** Toggles and returns the new state (true = now a favorite). */
    fun toggle(ctx: Context, id: String): Boolean {
        val set = ids(ctx).toMutableSet()
        val nowFav = if (set.contains(id)) {
            set.remove(id)
            false
        } else {
            set.add(id)
            true
        }
        prefs(ctx).edit().putStringSet(KEY, set).apply()
        return nowFav
    }
}
