package dorai.app.auth

import com.github.javakeyring.Keyring
import com.github.javakeyring.PasswordAccessException
import dorai.shared.model.DoraiJson
import dorai.shared.model.Tokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Tokens in the operating system's secure storage: Windows Credential Manager, the macOS Keychain,
 * or the Secret Service on Linux, one entry per server. Without one, [Keyring.create] fails and the
 * tokens stay in memory.
 */
class KeyringTokenVault : TokenVault {
    override suspend fun read(server: String): Tokens? = withKeyring { keyring ->
        try {
            DoraiJson.decodeFromString<Tokens>(keyring.getPassword(SERVICE, account(server)))
        } catch (_: PasswordAccessException) {
            null
        }
    }

    override suspend fun write(server: String, tokens: Tokens?) = withKeyring { keyring ->
        if (tokens != null) {
            keyring.setPassword(SERVICE, account(server), DoraiJson.encodeToString(tokens))
        } else {
            keyring.forget(account(server))
        }
        // The single session of earlier versions, which did not say its server.
        keyring.forget(LEGACY_ACCOUNT)
    }

    private fun Keyring.forget(account: String) {
        try { deletePassword(SERVICE, account) } catch (_: PasswordAccessException) { /* nothing saved */ }
    }

    private fun account(server: String) = "session $server"

    private suspend fun <T> withKeyring(block: (Keyring) -> T): T =
        withContext(Dispatchers.IO) { Keyring.create().use(block) }

    private companion object {
        const val SERVICE = "DORAi"
        const val LEGACY_ACCOUNT = "session"
    }
}
