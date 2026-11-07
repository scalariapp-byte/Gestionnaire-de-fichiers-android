package com.scalari.fichiers.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.io.IOException
import java.util.Locale

object FileOps {

    private const val MAX_SEARCH_RESULTS = 2000

    fun list(dir: File, settings: AppSettings): List<FileItem> {
        val files = dir.listFiles() ?: return emptyList()
        val items = files.asSequence()
            .filter { settings.showHidden || !it.name.startsWith(".") }
            .map { it.toFileItem() }
            .toList()
        return sort(items, settings.sortBy, settings.ascending, settings.foldersFirst)
    }

    fun sort(items: List<FileItem>, sortBy: SortBy, ascending: Boolean, foldersFirst: Boolean): List<FileItem> {
        val byName: Comparator<FileItem> = compareBy<FileItem, String>(NaturalOrder) { it.name }
        val base: Comparator<FileItem> = when (sortBy) {
            SortBy.NAME -> byName
            SortBy.SIZE -> compareBy<FileItem> { if (it.isDirectory) it.childCount.toLong() else it.size }.then(byName)
            SortBy.DATE -> compareBy<FileItem> { it.lastModified }.then(byName)
            SortBy.TYPE -> compareBy<FileItem> { it.extension }.then(byName)
        }
        val ordered = if (ascending) base else base.reversed()
        return if (foldersFirst) {
            items.sortedWith(compareByDescending<FileItem> { it.isDirectory }.then(ordered))
        } else {
            items.sortedWith(ordered)
        }
    }

    /** Recherche récursive par nom ; émet la liste cumulée au fil de l'eau. */
    fun search(root: File, query: String, showHidden: Boolean): Flow<List<FileItem>> = flow {
        val q = query.trim().lowercase(Locale.ROOT)
        if (q.isEmpty()) {
            emit(emptyList())
            return@flow
        }
        val results = ArrayList<FileItem>()
        var lastEmit = 0L
        val walker = root.walkTopDown().onEnter { it == root || showHidden || !it.name.startsWith(".") }
        for (f in walker) {
            currentCoroutineContext().ensureActive()
            if (f == root) continue
            if (!showHidden && f.name.startsWith(".")) continue
            if (f.name.lowercase(Locale.ROOT).contains(q)) {
                results += f.toFileItem()
                if (results.size >= MAX_SEARCH_RESULTS) break
                val now = System.currentTimeMillis()
                if (now - lastEmit > 300) {
                    emit(results.toList())
                    lastEmit = now
                }
            }
        }
        emit(results.toList())
    }.flowOn(Dispatchers.IO)

    /** Retourne un message d'erreur, ou null si le nom est valide. */
    fun validateName(name: String): String? {
        val n = name.trim()
        return when {
            n.isEmpty() -> "Le nom ne peut pas être vide"
            n == "." || n == ".." -> "Nom réservé"
            n.contains('/') || n.contains('\u0000') -> "Caractère interdit : /"
            n.toByteArray().size > 255 -> "Nom trop long"
            else -> null
        }
    }

    fun uniqueFile(dir: File, name: String): File {
        var candidate = File(dir, name)
        if (!candidate.exists()) return candidate
        val dot = name.lastIndexOf('.')
        val hasExt = dot > 0 && !candidate.isDirectory
        val base = if (hasExt) name.substring(0, dot) else name
        val ext = if (hasExt) name.substring(dot) else ""
        var i = 1
        while (true) {
            candidate = File(dir, "$base ($i)$ext")
            if (!candidate.exists()) return candidate
            i++
        }
    }

    fun createFolder(parent: File, name: String): File {
        validateName(name)?.let { throw IOException(it) }
        val f = File(parent, name.trim())
        if (f.exists()) throw IOException("« ${f.name} » existe déjà")
        if (!f.mkdir()) throw IOException("Impossible de créer le dossier")
        return f
    }

    fun createFile(parent: File, name: String): File {
        validateName(name)?.let { throw IOException(it) }
        val f = File(parent, name.trim())
        if (f.exists()) throw IOException("« ${f.name} » existe déjà")
        if (!f.createNewFile()) throw IOException("Impossible de créer le fichier")
        return f
    }

    fun rename(file: File, newName: String): File {
        validateName(newName)?.let { throw IOException(it) }
        val parent = file.parentFile ?: throw IOException("Dossier parent introuvable")
        val target = File(parent, newName.trim())
        if (target.name == file.name) return file
        if (target.exists() && !target.name.equals(file.name, ignoreCase = true)) {
            throw IOException("« ${target.name} » existe déjà")
        }
        if (target.name.equals(file.name, ignoreCase = true)) {
            // Changement de casse seul : passer par un nom temporaire (FS insensibles à la casse).
            val tmp = uniqueFile(parent, ".renommage-${System.nanoTime()}")
            if (!file.renameTo(tmp) || !tmp.renameTo(target)) throw IOException("Échec du renommage")
            return target
        }
        if (!file.renameTo(target)) throw IOException("Échec du renommage")
        return target
    }

    fun sizeOf(file: File): Long =
        if (file.isDirectory) file.walkTopDown().filter { it.isFile }.sumOf { it.length() } else file.length()

    data class DirStats(val bytes: Long, val files: Int, val folders: Int)

    fun stats(files: List<File>): DirStats {
        var bytes = 0L
        var count = 0
        var folders = 0
        for (root in files) {
            for (f in root.walkTopDown()) {
                if (f.isFile) {
                    bytes += f.length(); count++
                } else if (f != root || files.size > 1) {
                    folders++
                }
            }
        }
        return DirStats(bytes, count, folders)
    }

    fun isInside(child: File, parent: File): Boolean {
        val c = child.absolutePath
        val p = parent.absolutePath
        return c == p || c.startsWith("$p/")
    }
}
