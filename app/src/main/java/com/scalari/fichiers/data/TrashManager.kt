package com.scalari.fichiers.data

import android.content.Context
import java.io.File
import java.io.IOException

data class TrashEntry(
    val file: File,
    val originalPath: String,
    val deletedAt: Long,
    val size: Long,
    val isDirectory: Boolean,
) {
    val displayName: String get() = File(originalPath).name
}

/**
 * Corbeille : les éléments sont déplacés (renameTo, instantané) dans un dossier caché à la racine
 * du volume qui les contient, avec un fichier .trashinfo qui mémorise le chemin d'origine.
 */
object TrashManager {
    const val TRASH_DIR = ".Corbeille-Fichiers"
    private const val INFO_EXT = ".trashinfo"

    private fun trashDirFor(context: Context, file: File): File {
        val root = StorageRoots.rootOf(context, file)?.path ?: StorageRoots.primary()
        val dir = File(root, TRASH_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
            runCatching { File(dir, ".nomedia").createNewFile() }
        }
        return dir
    }

    private fun infoFile(file: File) = File(file.parentFile, file.name + INFO_EXT)

    fun isInTrash(file: File): Boolean = file.absolutePath.contains("/$TRASH_DIR/") || file.name == TRASH_DIR

    fun moveToTrash(context: Context, file: File) {
        val dir = trashDirFor(context, file)
        if (FileOps.isInside(file, dir)) {
            if (!file.deleteRecursively()) throw IOException("Impossible de supprimer ${file.name}")
            return
        }
        val now = System.currentTimeMillis()
        val target = FileOps.uniqueFile(dir, "${now}_${file.name}")
        val info = infoFile(target)
        info.writeText("${file.absolutePath}\n$now")
        if (!file.renameTo(target)) {
            val ok = runCatching { file.copyRecursively(target, overwrite = false) }.getOrDefault(false)
            if (!ok || !file.deleteRecursively()) {
                target.deleteRecursively()
                info.delete()
                throw IOException("Impossible de mettre ${file.name} à la corbeille")
            }
        }
    }

    fun list(context: Context): List<TrashEntry> =
        StorageRoots.list(context).flatMap { root ->
            val dir = File(root.path, TRASH_DIR)
            dir.listFiles { f -> !f.name.endsWith(INFO_EXT) && f.name != ".nomedia" }.orEmpty().map { f ->
                val lines = runCatching { infoFile(f).readLines() }.getOrDefault(emptyList())
                TrashEntry(
                    file = f,
                    originalPath = lines.getOrNull(0) ?: File(root.path, f.name.substringAfter('_')).absolutePath,
                    deletedAt = lines.getOrNull(1)?.toLongOrNull() ?: f.lastModified(),
                    size = FileOps.sizeOf(f),
                    isDirectory = f.isDirectory,
                )
            }
        }.sortedByDescending { it.deletedAt }

    fun restore(entry: TrashEntry): File {
        val original = File(entry.originalPath)
        val parent = original.parentFile ?: throw IOException("Chemin d'origine invalide")
        if (!parent.exists() && !parent.mkdirs()) throw IOException("Impossible de recréer ${parent.absolutePath}")
        val target = FileOps.uniqueFile(parent, original.name)
        if (!entry.file.renameTo(target)) {
            val ok = runCatching { entry.file.copyRecursively(target, overwrite = false) }.getOrDefault(false)
            if (!ok || !entry.file.deleteRecursively()) throw IOException("Impossible de restaurer ${original.name}")
        }
        infoFile(entry.file).delete()
        return target
    }

    fun deletePermanently(entry: TrashEntry) {
        if (!entry.file.deleteRecursively()) throw IOException("Impossible de supprimer ${entry.displayName}")
        infoFile(entry.file).delete()
    }
}
