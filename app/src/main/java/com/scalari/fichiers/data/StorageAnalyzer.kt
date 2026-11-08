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
import java.security.MessageDigest

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
     * Doublons stricts : même taille, puis même empreinte partielle, puis même SHA-256 complet.
     * [onProgress] : (étape, fait, total).
     */
    suspend fun duplicates(root: File, showHidden: Boolean, onProgress: (String, Int, Int) -> Unit): List<DuplicateGroup> =
        withContext(Dispatchers.IO) {
            onProgress("Inventaire des fichiers", 0, 0)
            val bySize = HashMap<Long, MutableList<File>>()
            var seen = 0
            root.walkTopDown().onEnter { it == root || !skip(it, showHidden) }.forEach { f ->
                if (f.isFile && !skip(f, showHidden)) {
                    val len = f.length()
                    if (len >= MIN_DUPLICATE_SIZE) bySize.getOrPut(len) { ArrayList() } += f
                    seen++
                    if (seen % 500 == 0) {
                        currentCoroutineContext().ensureActive()
                        onProgress("Inventaire des fichiers", seen, 0)
                    }
                }
            }
            val sameSize = bySize.values.filter { it.size > 1 }

            fun regroup(groups: List<List<File>>, label: String, key: (File) -> String?): List<List<File>> {
                val total = groups.sumOf { it.size }
                var done = 0
                val result = ArrayList<List<File>>()
                for (g in groups) {
                    val byKey = HashMap<String, MutableList<File>>()
                    for (f in g) {
                        key(f)?.let { byKey.getOrPut(it) { ArrayList() } += f }
                        done++
                        if (done % 20 == 0) onProgress(label, done, total)
                    }
                    byKey.values.filter { it.size > 1 }.forEach { result += it }
                }
                return result
            }

            currentCoroutineContext().ensureActive()
            val partial = regroup(sameSize, "Comparaison rapide") { partialHash(it) }
            currentCoroutineContext().ensureActive()
            val full = regroup(partial, "Vérification complète") { fullHash(it) }
            full.map { g -> DuplicateGroup(g.sortedBy { it.lastModified() }, g.first().length()) }
                .sortedByDescending { it.wasted }
        }

    private fun partialHash(f: File): String? = runCatching {
        val md = MessageDigest.getInstance("SHA-256")
        java.io.RandomAccessFile(f, "r").use { raf ->
            val buf = ByteArray(16 * 1024)
            val n = raf.read(buf)
            if (n > 0) md.update(buf, 0, n)
            if (raf.length() > buf.size * 2) {
                raf.seek(raf.length() - buf.size)
                val m = raf.read(buf)
                if (m > 0) md.update(buf, 0, m)
            }
        }
        md.digest().joinToString("") { "%02x".format(it) }
    }.getOrNull()

    private fun fullHash(f: File): String? = runCatching {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { input ->
            val buf = ByteArray(256 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        md.digest().joinToString("") { "%02x".format(it) }
    }.getOrNull()

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
