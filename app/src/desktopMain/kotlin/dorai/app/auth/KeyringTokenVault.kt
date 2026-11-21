package dorai.app.auth

import com.github.javakeyring.Keyring
import com.github.javakeyring.PasswordAccessException
import dorai.shared.model.DoraiJson
import dorai.shared.model.Tokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Tokens in the operating system's secure storage: Windows Credential Manager, the macOS Keychain,
 * or the Secret Service on Linux. Without one, [Keyring.create] fails and the tokens stay in memory.
 */
class KeyringTokenVault : TokenVault {
    override suspend fun read(): Tokens? = withKeyring { keyring ->
        try {
            DoraiJson.decodeFromString<Tokens>(keyring.getPassword(SERVICE, ACCOUNT))
        } catch (_: PasswordAccessException) {
            null
        }
    }

    override suspend fun write(tokens: Tokens?) = withKeyring { keyring ->
        if (tokens != null) {
            keyring.setPassword(SERVICE, ACCOUNT, DoraiJson.encodeToString(tokens))
        } else {
            try { keyring.deletePassword(SERVICE, ACCOUNT) } catch (_: PasswordAccessException) { /* nothing saved */ }
        }
    }

    private suspend fun <T> withKeyring(block: (Keyring) -> T): T =
        withContext(Dispatchers.IO) { Keyring.create().use(block) }

    private companion object {
        const val SERVICE = "DORAi"
        const val ACCOUNT = "session"
    }
}
