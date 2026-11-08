package com.scalari.fichiers.ui.player

import android.content.Context

/** Mémorise où chaque vidéo a été interrompue, pour la reprendre au même endroit. */
object ResumeStore {
    private const val PREFS = "reprise_lecture"
    private const val MAX = 200

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun get(context: Context, path: String): Long = prefs(context).getLong(path, 0L)

    /** Ignore les 10 premières et dernières secondes : on recommence ou on considère la vidéo vue. */
    fun save(context: Context, path: String, position: Long, duration: Long) {
        val p = prefs(context)
        if (duration <= 0) return
        if (position < 10_000 || position > duration - 10_000) {
            p.edit().remove(path).apply()
            return
        }
        val e = p.edit().putLong(path, position)
        if (p.all.size > MAX) p.all.keys.take(p.all.size - MAX).forEach { e.remove(it) }
        e.apply()
    }

    fun clear(context: Context, path: String) = prefs(context).edit().remove(path).apply()
}
