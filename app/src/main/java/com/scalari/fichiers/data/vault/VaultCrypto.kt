package com.scalari.fichiers.data.vault

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Primitives du coffre-fort.
 *
 * Format d'un fichier chiffré (« FMV1 ») :
 *   magic(4) | nonceBase(8) | { len(4) | AES-256-GCM(bloc ≤ 1 Mio) }*
 * Nonce d'un bloc = nonceBase ‖ index(4). Données associées = index(4) ‖ dernier(1) :
 * un bloc déplacé, supprimé ou un fichier tronqué est détecté au déchiffrement.
 * Le chiffrement par blocs évite de charger un gros fichier (vidéo) en mémoire.
 */
object VaultCrypto {
    private const val MAGIC = 0x464D5631 // "FMV1"
    const val CHUNK_SIZE = 1 shl 20
    private const val TAG_BITS = 128
    private const val IV_LEN = 12
    const val PBKDF2_ITERATIONS = 210_000

    private val random = SecureRandom()

    fun randomBytes(n: Int): ByteArray = ByteArray(n).also { random.nextBytes(it) }

    fun newDataKey(): SecretKey = SecretKeySpec(randomBytes(32), "AES")

    fun keyFromBytes(bytes: ByteArray): SecretKey = SecretKeySpec(bytes, "AES")

    fun deriveKey(pin: CharArray, salt: ByteArray, iterations: Int): SecretKey {
        val spec = PBEKeySpec(pin, salt, iterations, 256)
        try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return SecretKeySpec(bytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    /** Chiffre un petit bloc de données : iv(12) ‖ ciphertext+tag. */
    fun seal(key: SecretKey, plain: ByteArray, aad: ByteArray? = null): ByteArray {
        val iv = randomBytes(IV_LEN)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        aad?.let { cipher.updateAAD(it) }
        return iv + cipher.doFinal(plain)
    }

    /** Inverse de [seal] ; lève une exception si la clé est mauvaise ou les données altérées. */
    fun open(key: SecretKey, data: ByteArray, aad: ByteArray? = null): ByteArray {
        if (data.size < IV_LEN + TAG_BITS / 8) throw IOException("Données chiffrées invalides")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, data, 0, IV_LEN))
        aad?.let { cipher.updateAAD(it) }
        return cipher.doFinal(data, IV_LEN, data.size - IV_LEN)
    }

    private fun nonce(base: ByteArray, index: Int): ByteArray =
        ByteBuffer.allocate(IV_LEN).put(base).putInt(index).array()

    private fun aad(index: Int, last: Boolean): ByteArray =
        ByteBuffer.allocate(5).putInt(index).put(if (last) 1 else 0).array()

    /** Lit jusqu'à buffer.size octets ; retourne le nombre lu (0 en fin de flux). */
    private fun readFully(input: InputStream, buffer: ByteArray): Int {
        var total = 0
        while (total < buffer.size) {
            val n = input.read(buffer, total, buffer.size - total)
            if (n < 0) break
            total += n
        }
        return total
    }

    fun encryptStream(key: SecretKey, input: InputStream, output: OutputStream, onProgress: (Int) -> Unit = {}) {
        val out = DataOutputStream(output)
        val base = randomBytes(8)
        out.writeInt(MAGIC)
        out.write(base)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        var current = ByteArray(CHUNK_SIZE)
        var next = ByteArray(CHUNK_SIZE)
        var currentLen = readFully(input, current)
        var index = 0
        while (true) {
            val nextLen = if (currentLen == CHUNK_SIZE) readFully(input, next) else 0
            val last = nextLen == 0
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce(base, index)))
            cipher.updateAAD(aad(index, last))
            val ct = cipher.doFinal(current, 0, currentLen)
            out.writeInt(ct.size)
            out.write(ct)
            onProgress(currentLen)
            if (last) break
            val tmp = current; current = next; next = tmp
            currentLen = nextLen
            index++
        }
        out.flush()
    }

    fun decryptStream(key: SecretKey, input: InputStream, output: OutputStream, onProgress: (Int) -> Unit = {}) {
        val inp = DataInputStream(input.buffered())
        if (inp.readInt() != MAGIC) throw IOException("Fichier du coffre invalide")
        val base = ByteArray(8).also { inp.readFully(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        var index = 0
        var len = inp.readInt()
        while (true) {
            if (len < TAG_BITS / 8 || len > CHUNK_SIZE + TAG_BITS / 8) throw IOException("Bloc corrompu")
            val ct = ByteArray(len).also { inp.readFully(it) }
            val nextLen = try { inp.readInt() } catch (e: EOFException) { -1 }
            val last = nextLen < 0
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce(base, index)))
            cipher.updateAAD(aad(index, last))
            val plain = cipher.doFinal(ct) // AEADBadTagException si altéré
            output.write(plain)
            onProgress(plain.size)
            if (last) break
            len = nextLen
            index++
        }
        output.flush()
    }
}
