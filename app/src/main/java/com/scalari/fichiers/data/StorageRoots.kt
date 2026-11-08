package com.scalari.fichiers.data

import android.app.usage.StorageStatsManager
import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import java.io.File

data class StorageRoot(
    val path: File,
    val name: String,
    val isPrimary: Boolean,
    val isRemovable: Boolean,
    val total: Long,
    val free: Long,
) {
    val used: Long get() = (total - free).coerceAtLeast(0)
}

object StorageRoots {

    /** Volumes montés (stockage interne, cartes SD, clés USB OTG). */
    fun list(context: Context): List<StorageRoot> {
        val sm = context.getSystemService(StorageManager::class.java)
        val primaryPath = Environment.getExternalStorageDirectory().absolutePath
        return context.getExternalFilesDirs(null).filterNotNull().mapNotNull { appDir ->
            val idx = appDir.absolutePath.indexOf("/Android/data/")
            if (idx < 0) return@mapNotNull null
            val root = File(appDir.absolutePath.substring(0, idx))
            val volume = runCatching { sm?.getStorageVolume(root) }.getOrNull()
            val primary = volume?.isPrimary ?: (root.absolutePath == primaryPath)
            val stat = runCatching { StatFs(root.path) }.getOrNull()
            // Pour le stockage interne, StatFs ne mesure que la partition des données. Les Réglages
            // Android affichent la capacité totale de l'appareil (système compris) : on utilise la même source.
            val (total, free) = if (primary) primaryTotals(context) ?: (stat?.totalBytes to stat?.availableBytes)
            else (stat?.totalBytes to stat?.availableBytes)
            StorageRoot(
                path = root,
                name = if (primary) "Stockage interne" else volume?.getDescription(context) ?: "Stockage externe",
                isPrimary = primary,
                isRemovable = volume?.isRemovable ?: !primary,
                total = total ?: 0L,
                free = free ?: 0L,
            )
        }.distinctBy { it.path.absolutePath }
    }

    private fun primaryTotals(context: Context): Pair<Long?, Long?>? = runCatching {
        val ssm = context.getSystemService(StorageStatsManager::class.java) ?: return null
        ssm.getTotalBytes(StorageManager.UUID_DEFAULT) to ssm.getFreeBytes(StorageManager.UUID_DEFAULT)
    }.getOrNull()

    fun primary(): File = Environment.getExternalStorageDirectory()

    fun rootOf(context: Context, file: File): StorageRoot? =
        list(context).filter { FileOps.isInside(file, it.path) }.maxByOrNull { it.path.absolutePath.length }
}
