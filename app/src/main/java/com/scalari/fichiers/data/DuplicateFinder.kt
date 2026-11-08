package com.scalari.fichiers.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger

/** Fichier candidat : chemin, taille, date de modification (ms). */
data class Candidate(val path: String, val size: Long, val mtime: Long)

/**
 * Cache des empreintes, indexé par chemin et valable tant que taille et date sont inchangées :
 * une nouvelle analyse ne relit pas les fichiers déjà comparés.
 */
class HashCache(private val file: File?) {
    private data class Entry(val size: Long, val mtime: Long, var partial: String?, var full: String?)

    private val map = HashMap<String, Entry>()
    private var dirty = false

    init {
        runCatching {
            file?.takeIf { it.exists() }?.forEachLine { line ->
                val p = line.split('\t')
                if (p.size == 5) map[p[0]] = Entry(p[1].toLong(), p[2].toLong(), p[3].ifEmpty { null }, p[4].ifEmpty { null })
            }
        }
    }

    @Synchronized
    fun get(c: Candidate, full: Boolean): String? =
        map[c.path]?.takeIf { it.size == c.size && it.mtime == c.mtime }?.let { if (full) it.full else it.partial }

    @Synchronized
    fun put(c: Candidate, full: Boolean, hash: String) {
        val e = map[c.path]?.takeIf { it.size == c.size && it.mtime == c.mtime }
            ?: Entry(c.size, c.mtime, null, null).also { map[c.path] = it }
        if (full) e.full = hash else e.partial = hash
        dirty = true
    }

    /** Enregistre en ne gardant que les fichiers vus lors de cette analyse. */
    @Synchronized
    fun save(seen: Set<String>) {
        val f = file ?: return
        if (!dirty && map.keys.all { it in seen }) return
        runCatching {
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.bufferedWriter().use { w ->
                for ((path, e) in map) {
                    if (path !in seen || path.contains('\t') || path.contains('\n')) continue
                    w.append(path).append('\t').append(e.size.toString()).append('\t').append(e.mtime.toString())
                        .append('\t').append(e.partial ?: "").append('\t').append(e.full ?: "").append('\n')
                }
            }
            if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
            dirty = false
        }
    }
}

/**
 * Recherche de doublons stricts, en trois passes de plus en plus coûteuses :
 * 1. même taille (gratuit) ; 2. empreinte de 3 extraits de 16 Kio (début, milieu, fin) ;
 * 3. SHA-256 complet, seulement pour les fichiers encore candidats.
 * Les lectures sont faites en parallèle ([parallelism]) et les empreintes mises en cache.
 */
object DuplicateFinder {
    private const val CHUNK = 16 * 1024

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun find(
        candidates: List<Candidate>,
        cache: HashCache? = null,
        parallelism: Int = 4,
        onProgress: (step: String, done: Int, total: Int) -> Unit = { _, _, _ -> },
    ): List<List<Candidate>> {
        val io: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(parallelism)
        val sameSize = candidates.groupBy { it.size }.values.filter { it.size > 1 }

        suspend fun pass(groups: List<List<Candidate>>, step: String, full: Boolean): List<List<Candidate>> = coroutineScope {
            val files = groups.flatten()
            val done = AtomicInteger()
            onProgress(step, 0, files.size)
            val keys = files.map { c ->
                async(io) {
                    ensureActive()
                    val h = cache?.get(c, full) ?: (if (full) fullHash(c.path) else partialHash(c.path, c.size))?.also { cache?.put(c, full, it) }
                    val n = done.incrementAndGet()
                    if (n % 25 == 0 || n == files.size) onProgress(step, n, files.size)
                    h
                }
            }.awaitAll()
            files.indices.filter { keys[it] != null }
                .groupBy { files[it].size to keys[it] }
                .values.filter { it.size > 1 }
                .map { idx -> idx.map { files[it] } }
        }

        val partial = pass(sameSize, "Comparaison rapide", full = false)
        // Un fichier de moins de 3 extraits est déjà entièrement lu par l'empreinte partielle.
        val (small, big) = partial.partition { it.first().size <= 3L * CHUNK }
        val full = if (big.isEmpty()) emptyList() else pass(big, "Vérification complète", full = true)
        return small + full
    }

    fun partialHash(path: String, size: Long): String? = runCatching {
        val md = MessageDigest.getInstance("SHA-256")
        RandomAccessFile(path, "r").use { raf ->
            val buf = ByteArray(CHUNK)
            fun chunkAt(pos: Long) {
                raf.seek(pos)
                var read = 0
                while (read < CHUNK) {
                    val n = raf.read(buf, read, CHUNK - read)
                    if (n < 0) break
                    read += n
                }
                md.update(buf, 0, read)
            }
            if (size <= 3L * CHUNK) {
                var pos = 0L
                while (pos < size) { chunkAt(pos); pos += CHUNK }
            } else {
                chunkAt(0)
                chunkAt(size / 2 - CHUNK / 2)
                chunkAt(size - CHUNK)
            }
        }
        hex(md.digest())
    }.getOrNull()

    fun fullHash(path: String): String? = runCatching {
        val md = MessageDigest.getInstance("SHA-256")
        File(path).inputStream().use { input ->
            val buf = ByteArray(1024 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        hex(md.digest())
    }.getOrNull()

    private fun hex(b: ByteArray): String {
        val chars = CharArray(b.size * 2)
        val digits = "0123456789abcdef"
        for (i in b.indices) {
            chars[i * 2] = digits[(b[i].toInt() shr 4) and 0xF]
            chars[i * 2 + 1] = digits[b[i].toInt() and 0xF]
        }
        return String(chars)
    }
}
