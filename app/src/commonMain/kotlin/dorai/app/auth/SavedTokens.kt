package dorai.app.auth

import dorai.shared.api.TokenStore
import dorai.shared.model.Tokens
import kotlinx.coroutines.CancellationException

/**
 * Where the tokens survive a restart: the operating system's keychain on desktop, a Keystore-encrypted
 * value on Android. Either may fail (no keychain on this computer); the app then keeps them in memory only.
 */
interface TokenVault {
    suspend fun read(): Tokens?

    /** Null removes them. */
    suspend fun write(tokens: Tokens?)
}

/** Keeps nothing past the process: tests, and computers without a keychain. */
class MemoryTokenVault(private var tokens: Tokens? = null) : TokenVault {
    override suspend fun read() = tokens
    override suspend fun write(tokens: Tokens?) { this.tokens = tokens }
}

/**
 * The app's [TokenStore]. Every request reads the tokens, so they are kept in memory and only written
 * through to the [vault]. When the tokens are forgotten (logout, or a refresh the server refused),
 * [onCleared] tells the shell to show the login page.
 */
class SavedTokenStore(private val vault: TokenVault) : TokenStore {
    private var tokens: Tokens? = null
    private var loaded = false

    var onCleared: () -> Unit = {}

    override suspend fun load(): Tokens? {
        if (!loaded) {
            tokens = quietly { vault.read() }
            loaded = true
        }
        return tokens
    }

    override suspend fun save(tokens: Tokens?) {
        this.tokens = tokens
        loaded = true
        quietly { vault.write(tokens) }
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
