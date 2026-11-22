package dorai.app.auth

import dorai.shared.api.TokenStore
import dorai.shared.model.Tokens
import kotlinx.coroutines.CancellationException

/**
 * Where the tokens survive a restart, one session per server address: the operating system's keychain on
 * desktop, a Keystore-encrypted value on Android. Either may fail (no keychain on this computer); the app
 * then keeps them in memory only.
 */
interface TokenVault {
    suspend fun read(server: String): Tokens?

    /** Null removes the session for [server]. */
    suspend fun write(server: String, tokens: Tokens?)
}

/** Keeps nothing past the process: tests, and computers without a keychain. */
class MemoryTokenVault(tokens: Map<String, Tokens> = emptyMap()) : TokenVault {
    private val sessions = tokens.toMutableMap()

    override suspend fun read(server: String) = sessions[server]
    override suspend fun write(server: String, tokens: Tokens?) {
        if (tokens == null) sessions.remove(server) else sessions[server] = tokens
    }
}

/**
 * The app's [TokenStore] for one [server]. Every request reads the tokens, so they are kept in memory and
 * only written through to the [vault]. When the tokens are forgotten (logout, or a refresh the server
 * refused), [onCleared] tells the shell to show the login page.
 */
class SavedTokenStore(private val vault: TokenVault, val server: String) : TokenStore {
    private var tokens: Tokens? = null
    private var loaded = false

    var onCleared: () -> Unit = {}

    override suspend fun load(): Tokens? {
        if (!loaded) {
            tokens = quietly { vault.read(server) }
            loaded = true
        }
        return tokens
    }

    override suspend fun save(tokens: Tokens?) {
        this.tokens = tokens
        loaded = true
        quietly { vault.write(server, tokens) }
        if (tokens == null) onCleared()
    }

    /** A vault that fails leaves the tokens in memory: the user signs in again at the next start. */
    private suspend fun <T> quietly(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }
}
