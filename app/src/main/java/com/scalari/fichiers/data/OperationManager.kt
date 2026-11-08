package com.scalari.fichiers.data

import android.media.MediaScannerConnection
import android.os.SystemClock
import com.scalari.fichiers.FileManagerApp
import com.scalari.fichiers.data.vault.VaultEntry
import com.scalari.fichiers.data.vault.VaultManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.nio.charset.Charset
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

data class OperationProgress(
    val title: String,
    val currentFile: String = "",
    val processedBytes: Long = 0,
    val totalBytes: Long = 0,
    val processedItems: Int = 0,
    val totalItems: Int = 0,
) {
    /** Progression 0..1, ou null si indéterminée. */
    val fraction: Float?
        get() = when {
            totalBytes > 0 -> (processedBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
            totalItems > 0 -> (processedItems.toFloat() / totalItems).coerceIn(0f, 1f)
            else -> null
        }
}

/**
 * Exécute les opérations longues (copie, déplacement, suppression, compression…) hors de l'écran
 * courant, expose leur progression et permet l'annulation.
 */
object OperationManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _progress = MutableStateFlow<OperationProgress?>(null)
    val progress: StateFlow<OperationProgress?> = _progress.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    /** Émis après chaque opération : les écrans rechargent leur contenu. */
    private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 16)
    val changes: SharedFlow<Unit> = _changes.asSharedFlow()

    private var job: Job? = null
    private const val MAX_SCAN = 2000

    val isBusy: Boolean get() = job?.isActive == true

    fun cancel() {
        job?.cancel()
    }

    fun notifyChanged() {
        _changes.tryEmit(Unit)
    }

    private class Tracker(val title: String, var totalBytes: Long, val totalItems: Int) {
        var bytes = 0L
        var items = 0
        private var last = 0L
        fun update(name: String, force: Boolean = false) {
            val now = SystemClock.uptimeMillis()
            if (force || now - last > 100) {
                last = now
                OperationManager._progress.value = OperationProgress(title, name, bytes, totalBytes, items, totalItems)
            }
        }
    }

    /** [quiet] : pas de message de réussite (les erreurs restent signalées). */
    private fun run(title: String, quiet: Boolean = false, block: suspend (touched: MutableSet<String>) -> String) {
        if (isBusy) {
            _messages.tryEmit("Une opération est déjà en cours")
            return
        }
        _progress.value = OperationProgress(title)
        job = scope.launch {
            val touched = LinkedHashSet<String>()
            try {
                val message = block(touched)
                if (!quiet) _messages.emit(message)
            } catch (e: CancellationException) {
                _messages.tryEmit("Opération annulée")
                throw e
            } catch (e: Exception) {
                _messages.tryEmit("Erreur : ${e.message ?: e.javaClass.simpleName}")
            } finally {
                _progress.value = null
                _changes.tryEmit(Unit)
                scan(touched)
            }
        }
    }

    private fun scan(paths: Collection<String>) {
        if (paths.isEmpty()) return
        runCatching {
            MediaScannerConnection.scanFile(FileManagerApp.instance, paths.take(MAX_SCAN).toTypedArray(), null, null)
        }
    }

    private fun collectPaths(file: File, into: MutableSet<String>) {
        if (into.size >= MAX_SCAN) return
        if (file.isDirectory) {
            file.walkTopDown().filter { it.isFile }.take(MAX_SCAN - into.size).forEach { into += it.absolutePath }
        } else {
            into += file.absolutePath
        }
    }

    private fun plural(n: Int, word: String) = if (n > 1) "$n ${word}s" else "$n $word"

    // ---------------------------------------------------------------- Copie / déplacement

    fun paste(files: List<File>, destDir: File, move: Boolean) = run(if (move) "Déplacement" else "Copie") { touched ->
        for (src in files) {
            if (src.isDirectory && FileOps.isInside(destDir, src)) {
                throw IOException("Impossible de placer « ${src.name} » dans lui-même")
            }
        }
        val sizes = files.map { FileOps.sizeOf(it) }
        val t = Tracker(if (move) "Déplacement" else "Copie", sizes.sum(), files.size)
        var done = 0
        files.forEachIndexed { index, src ->
            currentCoroutineContext().ensureActive()
            if (!src.exists()) return@forEachIndexed
            if (move && src.parentFile?.absolutePath == destDir.absolutePath) {
                t.bytes += sizes[index]
            } else {
                val target = FileOps.uniqueFile(destDir, src.name)
                if (move && src.renameTo(target)) {
                    collectPaths(target, touched)
                    touched += src.absolutePath
                    t.bytes += sizes[index]
                } else {
                    copyRecursive(src, target, t, touched)
                    if (move) {
                        collectPaths(src, touched)
                        if (!src.deleteRecursively()) throw IOException("Copié mais impossible de supprimer l'original ${src.name}")
                    }
                }
            }
            done++
            t.items = done
            t.update(src.name, force = true)
        }
        "${plural(done, "élément")} ${if (move) "déplacé" else "copié"}${if (done > 1) "s" else ""}"
    }

    private suspend fun copyRecursive(src: File, dst: File, t: Tracker, touched: MutableSet<String>) {
        currentCoroutineContext().ensureActive()
        if (src.isDirectory) {
            if (!dst.exists() && !dst.mkdirs()) throw IOException("Impossible de créer ${dst.name}")
            src.listFiles()?.forEach { copyRecursive(it, File(dst, it.name), t, touched) }
            dst.setLastModified(src.lastModified())
        } else {
            t.update(src.name, force = true)
            try {
                src.inputStream().use { input ->
                    dst.outputStream().use { output ->
                        val buffer = ByteArray(256 * 1024)
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            output.write(buffer, 0, n)
                            t.bytes += n
                            t.update(src.name)
                            currentCoroutineContext().ensureActive()
                        }
                    }
                }
            } catch (e: Throwable) {
                dst.delete()
                throw e
            }
            dst.setLastModified(src.lastModified())
            if (touched.size < MAX_SCAN) touched += dst.absolutePath
        }
    }

    // ---------------------------------------------------------------- Suppression

    fun delete(files: List<File>, toTrash: Boolean) =
        run(if (toTrash) "Mise à la corbeille" else "Suppression", quiet = true) { touched ->
            val ctx = FileManagerApp.instance
            val t = Tracker(if (toTrash) "Mise à la corbeille" else "Suppression", 0, files.size)
            files.forEachIndexed { i, f ->
                currentCoroutineContext().ensureActive()
                t.items = i
                t.update(f.name, force = true)
                collectPaths(f, touched)
                if (toTrash && !TrashManager.isInTrash(f)) {
                    TrashManager.moveToTrash(ctx, f)
                } else if (!f.deleteRecursively()) {
                    throw IOException("Impossible de supprimer ${f.name}")
                }
            }
            "${plural(files.size, "élément")} ${if (toTrash) "mis à la corbeille" else "supprimé" + if (files.size > 1) "s" else ""}"
        }

    fun restoreFromTrash(entries: List<TrashEntry>) = run("Restauration") { touched ->
        val t = Tracker("Restauration", 0, entries.size)
        entries.forEachIndexed { i, e ->
            currentCoroutineContext().ensureActive()
            t.items = i
            t.update(e.displayName, force = true)
            collectPaths(TrashManager.restore(e), touched)
        }
        "${plural(entries.size, "élément")} restauré${if (entries.size > 1) "s" else ""}"
    }

    fun deleteFromTrash(entries: List<TrashEntry>) = run("Suppression définitive", quiet = true) { _ ->
        val t = Tracker("Suppression définitive", 0, entries.size)
        entries.forEachIndexed { i, e ->
            currentCoroutineContext().ensureActive()
            t.items = i
            t.update(e.displayName, force = true)
            TrashManager.deletePermanently(e)
        }
        "Corbeille : ${plural(entries.size, "élément")} supprimé${if (entries.size > 1) "s" else ""}"
    }

    // ---------------------------------------------------------------- Archives ZIP

    fun compress(files: List<File>, zipFile: File) = run("Compression") { touched ->
        val baseDir = files.first().parentFile
        val t = Tracker("Compression", files.sumOf { FileOps.sizeOf(it) }, files.size)
        try {
            ZipOutputStream(zipFile.outputStream().buffered()).use { zos ->
                for (root in files) {
                    for (f in root.walkTopDown()) {
                        currentCoroutineContext().ensureActive()
                        if (f.absolutePath == zipFile.absolutePath) continue
                        val rel = if (baseDir != null) f.relativeTo(baseDir).path else f.name
                        if (f.isDirectory) {
                            zos.putNextEntry(ZipEntry("$rel/").apply { time = f.lastModified() })
                            zos.closeEntry()
                        } else {
                            t.update(f.name, force = true)
                            zos.putNextEntry(ZipEntry(rel).apply { time = f.lastModified() })
                            f.inputStream().use { input ->
                                val buffer = ByteArray(256 * 1024)
                                while (true) {
                                    val n = input.read(buffer)
                                    if (n < 0) break
                                    zos.write(buffer, 0, n)
                                    t.bytes += n
                                    t.update(f.name)
                                }
                            }
                            zos.closeEntry()
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            zipFile.delete()
            throw e
        }
        touched += zipFile.absolutePath
        "Archive créée : ${zipFile.name}"
    }

    private fun openZip(file: File): ZipFile =
        try {
            ZipFile(file).also { z -> z.entries().asSequence().forEach { it.name } }
        } catch (e: Exception) {
            // Noms non UTF-8 (archives Windows anciennes) : nouvel essai en IBM437.
            try {
                ZipFile(file, Charset.forName("IBM437"))
            } catch (e2: Exception) {
                throw IOException("Archive invalide ou corrompue")
            }
        }

    fun extract(zip: File, parentDir: File) = run("Extraction") { touched ->
        val dest = FileOps.uniqueFile(parentDir, ArchiveExtractor.baseName(zip.name))
        if (!ArchiveExtractor.isZip(zip.name)) {
            if (!dest.mkdirs()) throw IOException("Impossible de créer ${dest.name}")
            val ctx = currentCoroutineContext()
            val t = Tracker("Extraction", 0, 0)
            try {
                val files = ArchiveExtractor.extract(zip, dest, cancel = { ctx.ensureActive() }) { done, total, name ->
                    t.totalBytes = total
                    t.bytes = done
                    t.update(name)
                }
                files.take(MAX_SCAN).forEach { touched += it.absolutePath }
            } catch (e: Throwable) {
                dest.deleteRecursively()
                throw e
            }
            return@run "Extrait dans « ${dest.name} »"
        }
        if (!dest.mkdirs()) throw IOException("Impossible de créer ${dest.name}")
        val destCanonical = dest.canonicalPath + File.separator
        try {
            openZip(zip).use { zf ->
                val entries = zf.entries().toList()
                val t = Tracker("Extraction", entries.sumOf { it.size.coerceAtLeast(0) }, entries.size)
                entries.forEachIndexed { i, entry ->
                    currentCoroutineContext().ensureActive()
                    val out = File(dest, entry.name)
                    val canonical = out.canonicalPath
                    if (!canonical.startsWith(destCanonical) && canonical + File.separator != destCanonical) {
                        throw IOException("Entrée d'archive dangereuse : ${entry.name}")
                    }
                    if (entry.isDirectory) {
                        out.mkdirs()
                    } else {
                        out.parentFile?.mkdirs()
                        t.update(out.name, force = true)
                        zf.getInputStream(entry).use { input ->
                            out.outputStream().use { output ->
                                val buffer = ByteArray(256 * 1024)
                                while (true) {
                                    val n = input.read(buffer)
                                    if (n < 0) break
                                    output.write(buffer, 0, n)
                                    t.bytes += n
                                    t.update(out.name)
                                    currentCoroutineContext().ensureActive()
                                }
                            }
                        }
                        if (entry.time > 0) out.setLastModified(entry.time)
                        if (touched.size < MAX_SCAN) touched += out.absolutePath
                    }
                    t.items = i + 1
                }
            }
        } catch (e: Throwable) {
            dest.deleteRecursively()
            throw e
        }
        "Extrait dans « ${dest.name} »"
    }

    // ---------------------------------------------------------------- Coffre-fort

    fun vaultAdd(files: List<File>) = run("Chiffrement") { touched ->
        val list = ArrayList<Pair<File, String>>()
        for (f in files) {
            if (f.isDirectory) {
                val base = f.parentFile ?: f
                f.walkTopDown().filter { it.isFile }.forEach { list += it to it.relativeTo(base).path }
            } else if (f.isFile) {
                list += f to f.name
            }
        }
        val t = Tracker("Chiffrement", list.sumOf { it.first.length() }, list.size)
        list.forEachIndexed { i, (f, name) ->
            currentCoroutineContext().ensureActive()
            t.update(name, force = true)
            if (touched.size < MAX_SCAN) touched += f.absolutePath
            VaultManager.addFile(f, name) { n ->
                t.bytes += n
                t.update(name)
            }
            t.items = i + 1
        }
        // Supprime les dossiers devenus vides.
        files.filter { it.isDirectory }.forEach { dir ->
            dir.walkBottomUp().filter { it.isDirectory && it.list()?.isEmpty() == true }.forEach { it.delete() }
        }
        "${plural(list.size, "fichier")} chiffré${if (list.size > 1) "s" else ""} dans le coffre-fort"
    }

    fun vaultRestore(entries: List<VaultEntry>) = run("Restauration") { touched ->
        val t = Tracker("Restauration", entries.sumOf { it.size }, entries.size)
        entries.forEachIndexed { i, e ->
            currentCoroutineContext().ensureActive()
            t.update(e.fileName, force = true)
            val target = VaultManager.restore(e) { n ->
                t.bytes += n
                t.update(e.fileName)
            }
            touched += target.absolutePath
            t.items = i + 1
        }
        "${plural(entries.size, "fichier")} restauré${if (entries.size > 1) "s" else ""}"
    }

    fun vaultDelete(entries: List<VaultEntry>) = run("Suppression", quiet = true) { _ ->
        entries.forEach {
            currentCoroutineContext().ensureActive()
            VaultManager.remove(it)
        }
        "${plural(entries.size, "fichier")} supprimé${if (entries.size > 1) "s" else ""} du coffre"
    }
}
