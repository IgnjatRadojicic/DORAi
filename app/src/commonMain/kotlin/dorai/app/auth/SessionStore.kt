package dorai.app.auth

import androidx.compose.runtime.Composable
import dorai.shared.model.DoraiJson
import dorai.shared.model.Tokens

/**
 * Small encrypted key-value storage of the platform: Android Keystore on Android; on desktop the OS keychain
 * once `java-keyring` is added (memory until then). Values are short strings.
 */
interface SecretVault {
    fun read(key: String): String?

    /** Null deletes the key. */
    fun write(key: String, value: String?)
}

/** Keeps values for the life of the process only: tests, and desktop until the keychain is available. */
class MemoryVault : SecretVault {
    private val values = mutableMapOf<String, String>()
    override fun read(key: String): String? = values[key]
    override fun write(key: String, value: String?) {
        if (value == null) values.remove(key) else values[key] = value
    }
}

/** The vault of this platform, created inside composition because Android needs its context. */
@Composable
expect fun rememberSecretVault(): SecretVault

/** What the app remembers between runs: which server, its certificate fingerprint, and the login (null when signed out). */
data class SavedSession(val server: String, val fingerprint: String?, val tokens: Tokens?)

/** Reads and writes the [SavedSession] in a [SecretVault]. A damaged entry counts as signed out, never as an error. */
class SessionStore(private val vault: SecretVault) {
    fun load(): SavedSession? {
        val server = vault.read(SERVER) ?: return null
        val tokens = vault.read(TOKENS)?.let { runCatching { DoraiJson.decodeFromString(Tokens.serializer(), it) }.getOrNull() }
        return SavedSession(server, vault.read(FINGERPRINT), tokens)
    }

    fun save(session: SavedSession?) {
        vault.write(SERVER, session?.server)
        vault.write(FINGERPRINT, session?.fingerprint)
        vault.write(TOKENS, session?.tokens?.let { DoraiJson.encodeToString(Tokens.serializer(), it) })
    }

    private companion object {
        const val SERVER = "server"
        const val FINGERPRINT = "fingerprint"
        const val TOKENS = "tokens"
    }
}
