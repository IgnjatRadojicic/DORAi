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
    private val server = "https://dorai.firm.local:8443"

    private class CountingVault(var saved: Map<String, Tokens> = emptyMap()) : TokenVault {
        var reads = 0
        override suspend fun read(server: String): Tokens? { reads++; return saved[server] }
        override suspend fun write(server: String, tokens: Tokens?) {
            saved = if (tokens == null) saved - server else saved + (server to tokens)
        }
    }

    private class BrokenVault : TokenVault {
        override suspend fun read(server: String): Tokens? = error("no keychain")
        override suspend fun write(server: String, tokens: Tokens?) = error("no keychain")
    }

    @Test
    fun theVaultIsReadOnceThenEveryRequestUsesMemory() = runImmediate {
        val vault = CountingVault(mapOf(server to tokens))
        val store = SavedTokenStore(vault, server)
        repeat(3) { assertEquals(tokens, store.load()) }
        assertEquals(1, vault.reads)
    }

    @Test
    fun newTokensAreWrittenThroughSoTheNextStartHasThem() = runImmediate {
        val vault = CountingVault()
        SavedTokenStore(vault, server).save(tokens)
        assertEquals(tokens, SavedTokenStore(vault, server).load())
    }

    @Test
    fun eachServerKeepsItsOwnSession() = runImmediate {
        val vault = CountingVault()
        SavedTokenStore(vault, server).save(tokens)
        assertNull(SavedTokenStore(vault, "https://other:8443").load())
        SavedTokenStore(vault, "https://other:8443").save(null)
        assertEquals(tokens, SavedTokenStore(vault, server).load())
    }

    @Test
    fun forgettingTheTokensClearsTheVaultAndTellsTheShell() = runImmediate {
        val vault = CountingVault(mapOf(server to tokens))
        var cleared = 0
        val store = SavedTokenStore(vault, server).apply { onCleared = { cleared++ } }
        store.save(null)
        assertNull(vault.saved[server])
        assertNull(store.load())
        assertEquals(1, cleared)
    }

    @Test
    fun withoutAWorkingVaultTheTokensStayInMemory() = runImmediate {
        val store = SavedTokenStore(BrokenVault(), server)
        assertNull(store.load())
        store.save(tokens)
        assertEquals(tokens, store.load())
    }
}
