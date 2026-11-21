package dorai.app.auth

import dorai.app.components.runImmediate
import dorai.shared.model.Role
import dorai.shared.model.Tokens
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class SavedTokenStoreTest {
    private val tokens = Tokens("access", Instant.fromEpochSeconds(0), "refresh", Instant.fromEpochSeconds(0), "luka", Role.Reviewer)

    private class CountingVault(var saved: Tokens? = null) : TokenVault {
        var reads = 0
        override suspend fun read(): Tokens? { reads++; return saved }
        override suspend fun write(tokens: Tokens?) { saved = tokens }
    }

    private class BrokenVault : TokenVault {
        override suspend fun read(): Tokens? = error("no keychain")
        override suspend fun write(tokens: Tokens?) = error("no keychain")
    }

    @Test
    fun theVaultIsReadOnceThenEveryRequestUsesMemory() = runImmediate {
        val vault = CountingVault(tokens)
        val store = SavedTokenStore(vault)
        repeat(3) { assertEquals(tokens, store.load()) }
        assertEquals(1, vault.reads)
    }

    @Test
    fun newTokensAreWrittenThroughSoTheNextStartHasThem() = runImmediate {
        val vault = CountingVault()
        SavedTokenStore(vault).save(tokens)
        assertEquals(tokens, SavedTokenStore(vault).load())
    }

    @Test
    fun forgettingTheTokensClearsTheVaultAndTellsTheShell() = runImmediate {
        val vault = CountingVault(tokens)
        var cleared = 0
        val store = SavedTokenStore(vault).apply { onCleared = { cleared++ } }
        store.save(null)
        assertNull(vault.saved)
        assertNull(store.load())
        assertEquals(1, cleared)
    }

    @Test
    fun withoutAWorkingVaultTheTokensStayInMemory() = runImmediate {
        val store = SavedTokenStore(BrokenVault())
        assertNull(store.load())
        store.save(tokens)
        assertEquals(tokens, store.load())
    }
}
