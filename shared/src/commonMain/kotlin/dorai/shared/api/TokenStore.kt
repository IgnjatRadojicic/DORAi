package dorai.shared.api

import dorai.shared.model.Tokens

/**
 * Where an app keeps its tokens. Secure storage is the app's choice (Android Keystore, OS keychain);
 * [MemoryTokenStore] keeps them for the life of the process (CLI, tests).
 */
interface TokenStore {
    suspend fun load(): Tokens?

    /** Null forgets the tokens (logout, or a refresh the server refused). */
    suspend fun save(tokens: Tokens?)
}

class MemoryTokenStore(private var tokens: Tokens? = null) : TokenStore {
    override suspend fun load() = tokens

    override suspend fun save(tokens: Tokens?) {
        this.tokens = tokens
    }
}
