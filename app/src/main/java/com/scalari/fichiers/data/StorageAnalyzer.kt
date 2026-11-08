package com.scalari.fichiers.data

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.scalari.fichiers.data.vault.VaultManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File

data class SizedEntry(val file: File, val size: Long, val isDirectory: Boolean, val files: Int)

data class DuplicateGroup(val files: List<File>, val size: Long) {
    /** Espace récupérable en ne gardant qu'un exemplaire. */
    val wasted: Long get() = size * (files.size - 1)
}

data class ApkCandidate(val file: File, val label: String, val size: Long)

/** Analyse du stockage : poids des dossiers, doublons, APK déjà installés, dossiers vides. */
object StorageAnalyzer {

    private const val MIN_DUPLICATE_SIZE = 100L * 1024

    private fun skip(f: File, showHidden: Boolean): Boolean =
        (!showHidden && f.name.startsWith(".")) || f.name == VaultManager.DIR_NAME || f.name == TrashManager.TRASH_DIR

    private suspend fun measure(f: File): Pair<Long, Int> {
        if (!f.isDirectory) return f.length() to 1
        var bytes = 0L
        var count = 0
        for (c in f.walkTopDown()) {
            if (count % 256 == 0) currentCoroutineContext().ensureActive()
            if (c.isFile) {
                bytes += c.length()
                count++
            }
        }
        return bytes to count
    }

    /** Taille de chaque élément de [dir], triée ; [onPartial] reçoit les résultats au fur et à mesure. */
    suspend fun childrenSizes(dir: File, showHidden: Boolean, onPartial: (List<SizedEntry>) -> Unit): List<SizedEntry> =
        withContext(Dispatchers.IO) {
            val children = dir.listFiles()?.filterNot { skip(it, showHidden) }.orEmpty()
            val out = ArrayList<SizedEntry>()
            // Fichiers d'abord (immédiat), puis les dossiers un par un.
            children.filter { it.isFile }.forEach { out += SizedEntry(it, it.length(), false, 1) }
            onPartial(out.sortedByDescending { it.size })
            for (d in children.filter { it.isDirectory }) {
                val (bytes, count) = measure(d)
                out += SizedEntry(d, bytes, true, count)
                onPartial(out.sortedByDescending { it.size })
            }
            out.sortedByDescending { it.size }
        }

    /**
     * Inventaire rapide via l'index MediaStore (taille connue sans parcourir le disque) ;
     * repli sur un parcours du disque si l'index est indisponible ou vide.
     */
    @Suppress("DEPRECATION")
    private suspend fun inventory(context: Context, root: File, showHidden: Boolean, onProgress: (String, Int, Int) -> Unit): List<Candidate> {
        val rootPath = root.absolutePath + "/"
        val out = ArrayList<Candidate>()
        val data = android.provider.MediaStore.MediaColumns.DATA
        val size = android.provider.MediaStore.MediaColumns.SIZE
        val date = android.provider.MediaStore.MediaColumns.DATE_MODIFIED
        runCatching {
            context.contentResolver.query(
                android.provider.MediaStore.Files.getContentUri("external"),
                arrayOf(data, size, date), "$size>=?", arrayOf(MIN_DUPLICATE_SIZE.toString()), null,
            )?.use { c ->
                val iData = c.getColumnIndexOrThrow(data)
                val iSize = c.getColumnIndexOrThrow(size)
                val iDate = c.getColumnIndexOrThrow(date)
                while (c.moveToNext()) {
                    val path = c.getString(iData) ?: continue
                    if (!path.startsWith(rootPath)) continue
                    if (path.contains("/${VaultManager.DIR_NAME}/") || path.contains("/${TrashManager.TRASH_DIR}/")) continue
                    if (!showHidden && path.contains("/.")) continue
                    out += Candidate(path, c.getLong(iSize), c.getLong(iDate) * 1000)
                    if (out.size % 2000 == 0) onProgress("Inventaire des fichiers", out.size, 0)
                }
            }
        }
        if (out.isNotEmpty()) return out
        root.walkTopDown().onEnter { it == root || !skip(it, showHidden) }.forEach { f ->
            if (f.isFile && !skip(f, showHidden)) {
                val len = f.length()
                if (len >= MIN_DUPLICATE_SIZE) out += Candidate(f.absolutePath, len, f.lastModified())
                if (out.size % 500 == 0) {
                    currentCoroutineContext().ensureActive()
                    onProgress("Inventaire des fichiers", out.size, 0)
                }
            }
        }
        return out
    }

    /**
     * Doublons stricts (taille, empreinte de 3 extraits, puis SHA-256 complet), lectures en
     * parallèle et empreintes mises en cache entre deux analyses. [onProgress] : (étape, fait, total).
     */
    suspend fun duplicates(context: Context, root: File, showHidden: Boolean, onProgress: (String, Int, Int) -> Unit): List<DuplicateGroup> =
        withContext(Dispatchers.IO) {
            onProgress("Inventaire des fichiers", 0, 0)
            val indexed = inventory(context, root, showHidden, onProgress)
            // L'index peut contenir des fichiers supprimés ou modifiés depuis : on ne garde que les
            // fichiers présents, avec leur taille et leur date réelles (pour la validité du cache).
            val sameSizeCandidates = indexed.groupBy { it.size }.values.filter { it.size > 1 }.flatten()
            val checked = sameSizeCandidates.mapNotNull { c ->
                val f = File(c.path)
                if (f.isFile) Candidate(c.path, f.length(), f.lastModified()) else null
            }
            val cache = HashCache(File(context.filesDir, "empreintes.tsv"))
            val groups = DuplicateFinder.find(checked, cache, parallelism = 4, onProgress = onProgress)
            cache.save(checked.mapTo(HashSet()) { it.path })
            groups.map { g ->
                DuplicateGroup(g.sortedBy { it.mtime }.map { File(it.path) }, g.first().size)
            }.sortedByDescending { it.wasted }
        }

    /** Fichiers APK dont l'application est déjà installée, dans la même version ou plus récente. */
    @Suppress("DEPRECATION")
    suspend fun installedApks(context: Context, showHidden: Boolean): List<ApkCandidate> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        MediaQueries.query(context, Category.APKS, showHidden).mapNotNull { item ->
            currentCoroutineContext().ensureActive()
            val info = runCatching { pm.getPackageArchiveInfo(item.path, 0) }.getOrNull() ?: return@mapNotNull null
            val installed = try {
                pm.getPackageInfo(info.packageName, 0)
            } catch (e: PackageManager.NameNotFoundException) {
                return@mapNotNull null
            }
            val apkVersion = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
            val installedVersion = if (Build.VERSION.SDK_INT >= 28) installed.longVersionCode else installed.versionCode.toLong()
            if (installedVersion < apkVersion) return@mapNotNull null
            val label = runCatching {
                info.applicationInfo?.let { ai ->
                    ai.sourceDir = item.path
                    ai.publicSourceDir = item.path
                    ai.loadLabel(pm).toString()
                }
            }.getOrNull() ?: info.packageName
            ApkCandidate(item.file, label, item.size)
        }
    }

    /** Dossiers vides (sans aucun fichier ni sous-dossier). */
    suspend fun emptyFolders(root: File, showHidden: Boolean): List<File> = withContext(Dispatchers.IO) {
        val out = ArrayList<File>()
        root.walkTopDown().onEnter { it == root || !skip(it, showHidden) }.forEach { f ->
            if (f != root && f.isDirectory && f.list()?.isEmpty() == true && !skip(f, showHidden)) out += f
            if (out.size % 100 == 0) currentCoroutineContext().ensureActive()
        }
        out.sortedBy { it.absolutePath.lowercase() }
    }
}
