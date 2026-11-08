package com.scalari.fichiers.data.vault

import android.content.Context
import android.util.Base64
import com.scalari.fichiers.data.FileOps
import com.scalari.fichiers.data.FileType
import com.scalari.fichiers.data.FileTypes
import com.scalari.fichiers.data.StorageRoots
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.crypto.SecretKey

data class VaultEntry(
    val id: String,
    /** Nom affiché (chemin relatif si le fichier venait d'un dossier). */
    val name: String,
    val originalPath: String,
    val size: Long,
    val modified: Long,
    val addedAt: Long,
) {
    val type: FileType get() = FileTypes.of(name, false)
    val fileName: String get() = name.substringAfterLast('/')
}

sealed interface VaultState {
    data object NotSetUp : VaultState
    data object Locked : VaultState
    data class Unlocked(val entries: List<VaultEntry>) : VaultState
}

/**
 * Coffre-fort chiffré.
 *
 * Les données vivent dans un dossier caché du stockage principal (elles survivent à une
 * désinstallation et restent récupérables avec le code). Une clé de données aléatoire
 * (AES-256) chiffre fichiers et index ; elle est elle-même chiffrée par une clé dérivée
 * du code (PBKDF2-HMAC-SHA256) et, en option, par une clé Android Keystore liée à la biométrie.
 * Sans le code, contenu ET noms de fichiers sont illisibles.
 */
object VaultManager {
    const val DIR_NAME = ".CoffreFichiers"
    private const val META = "meta.json"
    private const val INDEX = "index.bin"
    private val INDEX_AAD = "index".toByteArray()
    private val THUMB_AAD = "thumb".toByteArray()

    /** Vignettes déchiffrées en mémoire, vidées au verrouillage. */
    private val thumbCache = android.util.LruCache<String, android.graphics.Bitmap>(120)
    private val DEK_AAD = "dek".toByteArray()
    private const val MIN_PIN = 4

    val root: File get() = File(StorageRoots.primary(), DIR_NAME)
    private val dataDir: File get() = File(root, "data")

    private var key: SecretKey? = null
    private var entries: List<VaultEntry> = emptyList()

    private val _state = MutableStateFlow<VaultState>(VaultState.Locked)
    val state: StateFlow<VaultState> = _state.asStateFlow()

    /** Fichiers à ajouter une fois le coffre déverrouillé (depuis l'explorateur). */
    private val _pending = MutableStateFlow<List<File>>(emptyList())
    val pending: StateFlow<List<File>> = _pending.asStateFlow()

    /** Vrai pendant qu'une appli externe ouvre un fichier : évite le verrouillage auto. */
    @Volatile
    var externalOpen = false

    // Limitation des essais de code.
    private var failures = 0
    private var blockedUntil = 0L

    fun isSetUp(): Boolean = File(root, META).exists()

    fun isVaultPath(file: File): Boolean = FileOps.isInside(file, root)

    fun refreshState() {
        _state.value = when {
            key != null -> VaultState.Unlocked(entries)
            isSetUp() -> VaultState.Locked
            else -> VaultState.NotSetUp
        }
    }

    fun setPending(files: List<File>) { _pending.value = files }
    fun takePending(): List<File> = _pending.value.also { _pending.value = emptyList() }

    fun validatePin(pin: String): String? = when {
        pin.length < MIN_PIN -> "Au moins $MIN_PIN chiffres"
        !pin.all { it.isDigit() } -> "Chiffres uniquement"
        else -> null
    }

    /** Délai restant (ms) avant un nouvel essai autorisé. */
    fun lockoutRemaining(): Long = (blockedUntil - System.currentTimeMillis()).coerceAtLeast(0)

    // ------------------------------------------------------------------ Création / déverrouillage

    fun setup(pin: String) {
        validatePin(pin)?.let { throw IOException(it) }
        if (isSetUp()) throw IOException("Le coffre existe déjà")
        if (!dataDir.mkdirs() && !dataDir.isDirectory) throw IOException("Impossible de créer le coffre")
        runCatching { File(root, ".nomedia").createNewFile() }
        val dek = VaultCrypto.newDataKey()
        writeMeta(pin, dek)
        key = dek
        entries = emptyList()
        saveIndex()
        refreshState()
    }

    private fun writeMeta(pin: String, dek: SecretKey) {
        val salt = VaultCrypto.randomBytes(16)
        val iterations = VaultCrypto.PBKDF2_ITERATIONS
        val pinKey = VaultCrypto.deriveKey(pin.toCharArray(), salt, iterations)
        val wrapped = VaultCrypto.seal(pinKey, dek.encoded, DEK_AAD)
        val json = JSONObject()
            .put("version", 1)
            .put("salt", b64(salt))
            .put("iterations", iterations)
            .put("key", b64(wrapped))
        atomicWrite(File(root, META), json.toString().toByteArray())
    }

    /** Retourne true si le code est correct. Opération lente (PBKDF2) : appeler hors thread UI. */
    fun unlock(pin: String): Boolean {
        if (lockoutRemaining() > 0) return false
        val meta = JSONObject(File(root, META).readText())
        val pinKey = VaultCrypto.deriveKey(pin.toCharArray(), unb64(meta.getString("salt")), meta.getInt("iterations"))
        val dekBytes = try {
            VaultCrypto.open(pinKey, unb64(meta.getString("key")), DEK_AAD)
        } catch (e: Exception) {
            failures++
            if (failures >= 5) blockedUntil = System.currentTimeMillis() + 30_000L * (failures - 4)
            return false
        }
        failures = 0
        unlockWithKeyBytes(dekBytes)
        return true
    }

    fun unlockWithKeyBytes(dekBytes: ByteArray) {
        val k = VaultCrypto.keyFromBytes(dekBytes)
        entries = loadIndex(k)
        key = k
        refreshState()
    }

    /** Clé de données brute, pour l'enrôlement biométrique. */
    fun currentKeyBytes(): ByteArray? = key?.encoded

    fun changePin(oldPin: String, newPin: String) {
        validatePin(newPin)?.let { throw IOException(it) }
        val meta = JSONObject(File(root, META).readText())
        val oldKey = VaultCrypto.deriveKey(oldPin.toCharArray(), unb64(meta.getString("salt")), meta.getInt("iterations"))
        val dek = try {
            VaultCrypto.keyFromBytes(VaultCrypto.open(oldKey, unb64(meta.getString("key")), DEK_AAD))
        } catch (e: Exception) {
            throw IOException("Code actuel incorrect")
        }
        writeMeta(newPin, dek)
    }

    fun lock(context: Context) {
        if (key != null || tempDir(context).exists()) {
            // Arrête une lecture en cours d'un fichier du coffre (sa copie déchiffrée va être effacée).
            com.scalari.fichiers.ui.player.PlayerConnection.stopIfUnder(tempDir(context))
        }
        key = null
        entries = emptyList()
        thumbCache.evictAll()
        clearTemp(context)
        refreshState()
    }

    // ------------------------------------------------------------------ Vignettes

    private fun thumbFile(id: String) = File(dataDir, "$id.thumb")

    private fun writeThumb(k: SecretKey, id: String, jpeg: ByteArray) {
        runCatching { atomicWrite(thumbFile(id), VaultCrypto.seal(k, jpeg, THUMB_AAD)) }
    }

    /**
     * Vignette d'une image/vidéo du coffre (déchiffrée en mémoire). Pour les fichiers ajoutés
     * avant l'existence des vignettes, elle est créée une fois à partir d'une copie temporaire.
     */
    fun thumbnail(context: Context, entry: VaultEntry): android.graphics.Bitmap? {
        thumbCache.get(entry.id)?.let { return it }
        val k = key ?: return null
        if (!Thumbnails.canThumbnail(entry.name)) return null
        val f = thumbFile(entry.id)
        val jpeg = if (f.exists()) {
            runCatching { VaultCrypto.open(k, f.readBytes(), THUMB_AAD) }.getOrNull()
        } else {
            val temp = runCatching { openTemp(context, entry) }.getOrNull() ?: return null
            Thumbnails.create(temp, entry.name)?.also { writeThumb(k, entry.id, it) }
        } ?: return null
        val bmp = android.graphics.BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size) ?: return null
        thumbCache.put(entry.id, bmp)
        return bmp
    }

    // ------------------------------------------------------------------ Index

    private fun loadIndex(k: SecretKey): List<VaultEntry> {
        val f = File(root, INDEX)
        if (!f.exists()) return emptyList()
        val arr = JSONArray(String(VaultCrypto.open(k, f.readBytes(), INDEX_AAD)))
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            VaultEntry(
                id = o.getString("id"),
                name = o.getString("name"),
                originalPath = o.optString("path"),
                size = o.optLong("size"),
                modified = o.optLong("modified"),
                addedAt = o.optLong("added"),
            )
        }
    }

    private fun saveIndex() {
        val k = key ?: throw IOException("Coffre verrouillé")
        val arr = JSONArray()
        entries.forEach {
            arr.put(
                JSONObject().put("id", it.id).put("name", it.name).put("path", it.originalPath)
                    .put("size", it.size).put("modified", it.modified).put("added", it.addedAt)
            )
        }
        atomicWrite(File(root, INDEX), VaultCrypto.seal(k, arr.toString().toByteArray(), INDEX_AAD))
        _state.value = VaultState.Unlocked(entries.sortedByDescending { it.addedAt })
    }

    // ------------------------------------------------------------------ Contenu

    private fun requireKey(): SecretKey = key ?: throw IOException("Coffre verrouillé")

    /**
     * Chiffre [file] dans le coffre puis supprime l'original.
     * L'original n'est supprimé qu'après écriture complète du fichier chiffré et de l'index.
     */
    fun addFile(file: File, displayName: String, onProgress: (Int) -> Unit) {
        val k = requireKey()
        dataDir.mkdirs()
        val id = UUID.randomUUID().toString()
        val blob = File(dataDir, id)
        try {
            file.inputStream().use { input ->
                blob.outputStream().use { out -> VaultCrypto.encryptStream(k, input, out.buffered(), onProgress) }
            }
            if (Thumbnails.canThumbnail(displayName)) {
                Thumbnails.create(file, displayName)?.let { writeThumb(k, id, it) }
            }
            entries = entries + VaultEntry(id, displayName, file.absolutePath, file.length(), file.lastModified(), System.currentTimeMillis())
            saveIndex()
        } catch (e: Throwable) {
            blob.delete()
            thumbFile(id).delete()
            entries = entries.filterNot { it.id == id }
            throw e
        }
        if (!file.delete()) throw IOException("Chiffré, mais impossible de supprimer l'original ${file.name}")
    }

    /** Déchiffre [entry] vers [target]. */
    fun decryptTo(entry: VaultEntry, target: File, onProgress: (Int) -> Unit = {}) {
        val k = requireKey()
        target.parentFile?.mkdirs()
        try {
            File(dataDir, entry.id).inputStream().use { input ->
                target.outputStream().use { out -> VaultCrypto.decryptStream(k, input, out, onProgress) }
            }
            if (entry.modified > 0) target.setLastModified(entry.modified)
        } catch (e: Throwable) {
            target.delete()
            throw e
        }
    }

    /**
     * Sort [entry] du coffre : déchiffre vers [targetDir] (par défaut l'emplacement d'origine,
     * ou Téléchargements s'il n'existe plus) puis la retire du coffre.
     */
    fun restore(entry: VaultEntry, targetDir: File? = null, onProgress: (Int) -> Unit): File {
        val original = File(entry.originalPath)
        val parent = targetDir?.takeIf { it.isDirectory || it.mkdirs() }
            ?: original.parentFile?.takeIf { it.isDirectory || it.mkdirs() }
            ?: File(StorageRoots.primary(), "Download")
        val target = FileOps.uniqueFile(parent, entry.fileName)
        decryptTo(entry, target, onProgress)
        remove(entry)
        return target
    }

    fun remove(entry: VaultEntry) {
        requireKey()
        File(dataDir, entry.id).delete()
        thumbFile(entry.id).delete()
        thumbCache.remove(entry.id)
        entries = entries.filterNot { it.id == entry.id }
        saveIndex()
    }

    fun tempDir(context: Context) = File(context.cacheDir, "coffre")

    /** Copie déchiffrée temporaire (cache privé de l'app), effacée au verrouillage. */
    fun openTemp(context: Context, entry: VaultEntry): File {
        val target = File(File(tempDir(context), entry.id), entry.fileName)
        if (!target.exists() || target.length() != entry.size) decryptTo(entry, target)
        return target
    }

    fun clearTemp(context: Context) {
        tempDir(context).deleteRecursively()
    }

    // ------------------------------------------------------------------ Utilitaires

    private fun atomicWrite(target: File, bytes: ByteArray) {
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(target)) {
            target.writeBytes(bytes)
            tmp.delete()
        }
    }

    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
    private fun unb64(s: String) = Base64.decode(s, Base64.NO_WRAP)
}
