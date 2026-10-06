package mx.diego.wispr.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import mx.diego.wisprkit.Credentials
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * URL del endpoint MCP y token, cifrados con AES-GCM y una clave del Android Keystore (no
 * exportable). Es lo único que se persiste: credenciales, nunca datos financieros.
 * EncryptedSharedPreferences está deprecado; esto es lo que Google recomienda en su lugar.
 */
class CredentialStore(context: Context) {
    private val prefs = context.getSharedPreferences("credenciales", Context.MODE_PRIVATE)

    fun load(): Credentials? {
        val blob = prefs.getString(KEY_BLOB, null) ?: return null
        return runCatching {
            val (iv, data) = blob.split(":").map { Base64.decode(it, Base64.NO_WRAP) }
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            val json = JSONObject(String(cipher.doFinal(data), Charsets.UTF_8))
            Credentials(json.getString("endpoint"), json.getString("token"))
        }.getOrNull()
    }

    fun save(credentials: Credentials) {
        val plain = JSONObject()
            .put("endpoint", credentials.endpoint)
            .put("token", credentials.token)
            .toString()
            .toByteArray(Charsets.UTF_8)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val blob = listOf(cipher.iv, cipher.doFinal(plain))
            .joinToString(":") { Base64.encodeToString(it, Base64.NO_WRAP) }
        prefs.edit().putString(KEY_BLOB, blob).apply()
    }

    fun delete() {
        prefs.edit().remove(KEY_BLOB).apply()
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "wispr-money-mcp"
        const val KEY_BLOB = "blob"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
