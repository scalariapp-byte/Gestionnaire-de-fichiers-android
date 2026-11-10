package com.scalari.fichiers.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.view.View
import android.widget.RemoteViews
import com.scalari.fichiers.FileManagerApp
import com.scalari.fichiers.MainActivity
import com.scalari.fichiers.R
import com.scalari.fichiers.data.StorageRoots
import com.scalari.fichiers.data.formatSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

/** Demandes d'ouverture d'un dossier venues de l'extérieur (widget). */
object OpenRequests {
    const val EXTRA_PATH = "com.scalari.fichiers.OPEN_PATH"
    private val _path = MutableStateFlow<String?>(null)
    val path: StateFlow<String?> = _path.asStateFlow()

    fun handle(intent: Intent?) {
        val p = intent?.getStringExtra(EXTRA_PATH) ?: return
        intent.removeExtra(EXTRA_PATH)
        if (File(p).isDirectory) _path.value = p
    }

    fun consume() { _path.value = null }
}

/**
 * Widget d'écran d'accueil : espace libre du stockage interne et quatre raccourcis
 * (favoris d'abord, puis Téléchargements, Appareil photo, Documents, Musique).
 */
class StorageWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        scope.launch {
            try {
                val views = build(context)
                ids.forEach { manager.updateAppWidget(it, views) }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val SLOTS = listOf(
            Triple(R.id.widget_slot1, R.id.widget_slot1_icon, R.id.widget_slot1_label),
            Triple(R.id.widget_slot2, R.id.widget_slot2_icon, R.id.widget_slot2_label),
            Triple(R.id.widget_slot3, R.id.widget_slot3_icon, R.id.widget_slot3_label),
            Triple(R.id.widget_slot4, R.id.widget_slot4_icon, R.id.widget_slot4_label),
        )

        /** Redessine tous les widgets posés (après un changement de favoris, une suppression…). */
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = runCatching { manager.getAppWidgetIds(ComponentName(context, StorageWidget::class.java)) }.getOrNull()
            if (ids == null || ids.isEmpty()) return
            scope.launch {
                val views = build(context)
                ids.forEach { manager.updateAppWidget(it, views) }
            }
        }

        private suspend fun build(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_storage)
            val root = runCatching { StorageRoots.list(context).firstOrNull { it.isPrimary } }.getOrNull()
            if (root != null && root.total > 0) {
                val percent = (root.used * 100 / root.total).toInt().coerceIn(0, 100)
                views.setProgressBar(R.id.widget_progress, 100, percent, false)
                views.setTextViewText(R.id.widget_free, "${formatSize(root.free)} libres")
                views.setTextViewText(R.id.widget_detail, "$percent % utilisés sur ${formatSize(root.total)}")
            }
            views.setOnClickPendingIntent(R.id.widget_header, openIntent(context, null, 0))

            val favorites = runCatching { FileManagerApp.instance.settings.settings.first().favorites }.getOrDefault(emptyList())
            val base = Environment.getExternalStorageDirectory()
            val defaults = listOf(
                File(base, Environment.DIRECTORY_DOWNLOADS) to "Téléchargements",
                File(base, Environment.DIRECTORY_DCIM) to "Appareil photo",
                File(base, Environment.DIRECTORY_DOCUMENTS) to "Documents",
                File(base, Environment.DIRECTORY_MUSIC) to "Musique",
            )
            val entries = (favorites.map { File(it) to (File(it).name.ifEmpty { it }) } + defaults)
                .filter { it.first.isDirectory }
                .distinctBy { it.first.absolutePath }
                .take(SLOTS.size)
            SLOTS.forEachIndexed { i, (slot, icon, label) ->
                val e = entries.getOrNull(i)
                if (e == null) {
                    views.setViewVisibility(slot, View.INVISIBLE)
                } else {
                    views.setViewVisibility(slot, View.VISIBLE)
                    views.setTextViewText(label, e.second)
                    views.setImageViewResource(icon, iconFor(e.first, base))
                    views.setOnClickPendingIntent(slot, openIntent(context, e.first.absolutePath, i + 1))
                }
            }
            return views
        }

        private fun iconFor(dir: File, base: File): Int = when (dir.absolutePath) {
            File(base, Environment.DIRECTORY_DOWNLOADS).absolutePath -> R.drawable.ic_widget_download
            File(base, Environment.DIRECTORY_DCIM).absolutePath -> R.drawable.ic_widget_camera
            File(base, Environment.DIRECTORY_MUSIC).absolutePath -> R.drawable.ic_widget_music
            else -> R.drawable.ic_widget_folder
        }

        private fun openIntent(context: Context, path: String?, requestCode: Int): PendingIntent {
            val intent = Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            if (path != null) intent.putExtra(OpenRequests.EXTRA_PATH, path)
            return PendingIntent.getActivity(
                context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
