package com.scalari.fichiers.data

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
            StorageRoot(
                path = root,
                name = if (primary) "Stockage interne" else volume?.getDescription(context) ?: "Stockage externe",
                isPrimary = primary,
                isRemovable = volume?.isRemovable ?: !primary,
                total = stat?.totalBytes ?: 0L,
                free = stat?.availableBytes ?: 0L,
            )
        }.distinctBy { it.path.absolutePath }
    }

    fun primary(): File = Environment.getExternalStorageDirectory()

    fun rootOf(context: Context, file: File): StorageRoot? =
        list(context).filter { FileOps.isInside(file, it.path) }.maxByOrNull { it.path.absolutePath.length }
}
