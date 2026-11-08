package com.scalari.fichiers.data

import com.github.junrar.Archive
import com.github.junrar.exception.UnsupportedRarV5Exception
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.CompressorStreamFactory
import java.io.BufferedInputStream
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.util.Locale

/**
 * Extraction des archives autres que ZIP : 7z, TAR (.tar, .tar.gz/.tgz, .tar.bz2, .tar.xz),
 * fichiers compressés seuls (.gz, .bz2, .xz) et RAR (version 4 ; RAR5 n'est pas pris en charge).
 */
object ArchiveExtractor {

    fun interface Progress {
        /** [done]/[total] en octets (total 0 = inconnu), [name] = fichier en cours. */
        fun update(done: Long, total: Long, name: String)
    }

    private val tarSuffixes = listOf(".tar", ".tar.gz", ".tgz", ".tar.bz2", ".tbz2", ".tbz", ".tar.xz", ".txz")
    private val singleSuffixes = listOf(".gz", ".bz2", ".xz")
    val extensions = setOf("zip", "jar", "7z", "tar", "gz", "tgz", "bz2", "tbz2", "tbz", "xz", "txz", "rar")

    fun isArchive(name: String): Boolean = name.substringAfterLast('.', "").lowercase(Locale.ROOT) in extensions

    fun isZip(name: String): Boolean = name.lowercase(Locale.ROOT).let { it.endsWith(".zip") || it.endsWith(".jar") }

    /** Nom du dossier de destination : « photos.tar.gz » → « photos ». */
    fun baseName(name: String): String {
        val lower = name.lowercase(Locale.ROOT)
        val suffix = (tarSuffixes + singleSuffixes + listOf(".7z", ".rar", ".zip", ".jar")).filter { lower.endsWith(it) }
            .maxByOrNull { it.length }
        return if (suffix != null && name.length > suffix.length) name.dropLast(suffix.length) else name
    }

    /** Fichier cible dans [dest], refusé s'il sortirait du dossier (« zip slip »). */
    fun safeTarget(dest: File, entryName: String): File {
        val out = File(dest, entryName)
        val destPath = dest.canonicalPath + File.separator
        val canonical = out.canonicalPath
        if (!canonical.startsWith(destPath) && canonical + File.separator != destPath) {
            throw IOException("Entrée d'archive dangereuse : $entryName")
        }
        return out
    }

    /** Extrait [archive] dans [dest] (dossier existant). Retourne la liste des fichiers créés. */
    fun extract(archive: File, dest: File, cancel: () -> Unit, progress: Progress): List<File> {
        val lower = archive.name.lowercase(Locale.ROOT)
        return when {
            lower.endsWith(".7z") -> extract7z(archive, dest, cancel, progress)
            lower.endsWith(".rar") -> extractRar(archive, dest, cancel, progress)
            tarSuffixes.any { lower.endsWith(it) } -> extractTar(archive, dest, cancel, progress)
            singleSuffixes.any { lower.endsWith(it) } -> extractSingle(archive, dest, cancel, progress)
            else -> throw IOException("Format d'archive non pris en charge")
        }
    }

    private fun copy(input: InputStream, out: File, cancel: () -> Unit, onBytes: (Int) -> Unit) {
        out.parentFile?.mkdirs()
        out.outputStream().use { o ->
            val buffer = ByteArray(256 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                o.write(buffer, 0, n)
                onBytes(n)
                cancel()
            }
        }
    }

    /** Compte les octets lus sur le fichier brut : progression des flux compressés. */
    private class CountingStream(input: InputStream) : FilterInputStream(input) {
        var count = 0L
        override fun read(): Int = super.read().also { if (it >= 0) count++ }
        override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { if (it > 0) count += it }
    }

    private fun compressed(raw: InputStream, lower: String): InputStream {
        val bis = BufferedInputStream(raw)
        return when {
            lower.endsWith(".gz") || lower.endsWith(".tgz") -> CompressorStreamFactory().createCompressorInputStream(CompressorStreamFactory.GZIP, bis)
            lower.endsWith(".bz2") || lower.endsWith(".tbz2") || lower.endsWith(".tbz") -> CompressorStreamFactory().createCompressorInputStream(CompressorStreamFactory.BZIP2, bis)
            lower.endsWith(".xz") || lower.endsWith(".txz") -> CompressorStreamFactory().createCompressorInputStream(CompressorStreamFactory.XZ, bis)
            else -> bis
        }
    }

    private fun extractTar(archive: File, dest: File, cancel: () -> Unit, progress: Progress): List<File> {
        val created = ArrayList<File>()
        val total = archive.length()
        val counter = CountingStream(archive.inputStream())
        TarArchiveInputStream(compressed(counter, archive.name.lowercase(Locale.ROOT))).use { tar ->
            while (true) {
                val entry = tar.nextEntry ?: break
                cancel()
                if (entry.isSymbolicLink || entry.isLink) continue // liens ignorés par sécurité
                val out = safeTarget(dest, entry.name)
                if (entry.isDirectory) {
                    out.mkdirs()
                } else {
                    progress.update(counter.count, total, out.name)
                    copy(tar, out, cancel) { progress.update(counter.count, total, out.name) }
                    entry.modTime?.time?.let { if (it > 0) out.setLastModified(it) }
                    created += out
                }
            }
        }
        return created
    }

    private fun extractSingle(archive: File, dest: File, cancel: () -> Unit, progress: Progress): List<File> {
        val out = safeTarget(dest, baseName(archive.name))
        val total = archive.length()
        val counter = CountingStream(archive.inputStream())
        compressed(counter, archive.name.lowercase(Locale.ROOT)).use { input ->
            copy(input, out, cancel) { progress.update(counter.count, total, out.name) }
        }
        return listOf(out)
    }

    @Suppress("DEPRECATION")
    private fun extract7z(archive: File, dest: File, cancel: () -> Unit, progress: Progress): List<File> {
        val created = ArrayList<File>()
        SevenZFile(archive).use { z ->
            val total = z.entries.sumOf { if (it.hasStream()) it.size else 0L }
            var done = 0L
            while (true) {
                val entry = z.nextEntry ?: break
                cancel()
                val out = safeTarget(dest, entry.name)
                if (entry.isDirectory) {
                    out.mkdirs()
                    continue
                }
                out.parentFile?.mkdirs()
                progress.update(done, total, out.name)
                out.outputStream().use { o ->
                    if (entry.hasStream()) {
                        val buffer = ByteArray(256 * 1024)
                        while (true) {
                            val n = z.read(buffer)
                            if (n < 0) break
                            o.write(buffer, 0, n)
                            done += n
                            progress.update(done, total, out.name)
                            cancel()
                        }
                    }
                }
                if (entry.hasLastModifiedDate) out.setLastModified(entry.lastModifiedDate.time)
                created += out
            }
        }
        return created
    }

    private fun extractRar(archive: File, dest: File, cancel: () -> Unit, progress: Progress): List<File> {
        val created = ArrayList<File>()
        try {
            Archive(archive).use { rar ->
                if (rar.isEncrypted) throw IOException("Archive RAR protégée par mot de passe : non prise en charge")
                val headers = rar.fileHeaders
                val total = headers.sumOf { if (it.isDirectory) 0L else it.fullUnpackSize }
                var done = 0L
                for (h in headers) {
                    cancel()
                    val name = (h.fileName ?: continue).replace('\\', '/')
                    val out = safeTarget(dest, name)
                    if (h.isDirectory) {
                        out.mkdirs()
                        continue
                    }
                    out.parentFile?.mkdirs()
                    progress.update(done, total, out.name)
                    out.outputStream().use { o -> rar.extractFile(h, o) }
                    done += h.fullUnpackSize
                    progress.update(done, total, out.name)
                    h.mTime?.time?.let { if (it > 0) out.setLastModified(it) }
                    created += out
                }
            }
        } catch (e: UnsupportedRarV5Exception) {
            throw IOException("Format RAR5 non pris en charge (seul RAR4 l'est)")
        }
        return created
    }
}
