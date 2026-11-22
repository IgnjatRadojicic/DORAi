package dorai.app.auth

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dorai.shared.model.DoraiJson
import dorai.shared.model.Tokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Tokens encrypted with an AES key that lives in the Android Keystore and never leaves the device,
 * saved in the app's DataStore, one value per server. A value that no longer decrypts (new key,
 * restored backup) reads as none.
 */
class KeystoreTokenVault(private val store: DataStore<Preferences>) : TokenVault {
    override suspend fun read(server: String): Tokens? = withContext(Dispatchers.IO) {
        val sealed = store.data.first()[tokensKey(server)]?.let { Base64.decode(it, Base64.NO_WRAP) } ?: return@withContext null
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, sealed, 0, IV_BYTES))
        DoraiJson.decodeFromString<Tokens>(cipher.doFinal(sealed, IV_BYTES, sealed.size - IV_BYTES).decodeToString())
    }

    override suspend fun write(server: String, tokens: Tokens?) = withContext(Dispatchers.IO) {
        val sealed = tokens?.let {
            val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
            Base64.encodeToString(cipher.iv + cipher.doFinal(DoraiJson.encodeToString(it).encodeToByteArray()), Base64.NO_WRAP)
        }
        store.edit {
            if (sealed == null) it.remove(tokensKey(server)) else it[tokensKey(server)] = sealed
            // The single session of earlier versions, which did not say its server.
            it.remove(LEGACY_TOKENS)
        }
        Unit
    }

    private fun tokensKey(server: String) = stringPreferencesKey("tokens $server")

    private fun key(): SecretKey {
        val keystore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keystore.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply { init(spec) }.generateKey()
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "dorai-tokens"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
        val LEGACY_TOKENS = stringPreferencesKey("tokens")
    }
}
