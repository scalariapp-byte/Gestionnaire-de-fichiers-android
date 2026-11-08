package com.scalari.fichiers.data.vault

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Déverrouillage par empreinte / visage : la clé du coffre est chiffrée par une clé du
 * Keystore matériel qui n'est utilisable qu'après authentification biométrique forte.
 * Ajouter une nouvelle empreinte invalide la clé (il faut alors ressaisir le code).
 */
object VaultBiometric {
    private const val ALIAS = "coffre_biometrie"
    private const val PREFS = "coffre_biometrie"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isAvailable(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

    fun isEnabled(context: Context): Boolean =
        prefs(context).contains("ct") && runCatching { keyStore().containsAlias(ALIAS) }.getOrDefault(false)

    fun disable(context: Context) {
        prefs(context).edit().clear().apply()
        runCatching { keyStore().deleteEntry(ALIAS) }
    }

    private fun keyStore() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun createKey(): SecretKey {
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(true)
                .setInvalidatedByBiometricEnrollment(true)
                .build()
        )
        return gen.generateKey()
    }

    private fun prompt(
        activity: FragmentActivity,
        title: String,
        cipher: Cipher,
        onSuccess: (Cipher) -> Unit,
        onError: (String) -> Unit,
    ) {
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                val c = result.cryptoObject?.cipher
                if (c != null) onSuccess(c) else onError("Authentification incomplète")
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onError(errString.toString())
            }
        }
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setNegativeButtonText("Utiliser le code")
            .setAllowedAuthenticators(BIOMETRIC_STRONG)
            .build()
        BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback)
            .authenticate(info, BiometricPrompt.CryptoObject(cipher))
    }

    /** Enrôle la clé du coffre (déjà déverrouillé). */
    fun enable(activity: FragmentActivity, dek: ByteArray, onDone: (Boolean, String?) -> Unit) {
        try {
            runCatching { keyStore().deleteEntry(ALIAS) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, createKey()) }
            prompt(activity, "Activer le déverrouillage biométrique", cipher, onSuccess = { c ->
                try {
                    val ct = c.doFinal(dek)
                    prefs(activity).edit()
                        .putString("iv", Base64.encodeToString(c.iv, Base64.NO_WRAP))
                        .putString("ct", Base64.encodeToString(ct, Base64.NO_WRAP))
                        .apply()
                    onDone(true, null)
                } catch (e: Exception) {
                    onDone(false, e.message)
                }
            }, onError = { onDone(false, it) })
        } catch (e: Exception) {
            onDone(false, e.message)
        }
    }

    /** Demande l'empreinte et renvoie la clé du coffre déchiffrée. */
    fun unlock(activity: FragmentActivity, onKey: (ByteArray) -> Unit, onError: (String) -> Unit) {
        try {
            val p = prefs(activity)
            val iv = Base64.decode(p.getString("iv", null) ?: return onError("Biométrie non configurée"), Base64.NO_WRAP)
            val ct = Base64.decode(p.getString("ct", null) ?: return onError("Biométrie non configurée"), Base64.NO_WRAP)
            val key = keyStore().getKey(ALIAS, null) as? SecretKey ?: return onError("Clé biométrique absente")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv)) }
            prompt(activity, "Déverrouiller le coffre-fort", cipher, onSuccess = { c ->
                try {
                    onKey(c.doFinal(ct))
                } catch (e: Exception) {
                    onError("Échec du déchiffrement")
                }
            }, onError = onError)
        } catch (e: KeyPermanentlyInvalidatedException) {
            disable(activity)
            onError("Empreintes modifiées : saisissez le code puis réactivez la biométrie")
        } catch (e: Exception) {
            onError(e.message ?: "Biométrie indisponible")
        }
    }
}
