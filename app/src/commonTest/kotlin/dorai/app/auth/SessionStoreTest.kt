package dorai.app.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SessionStoreTest {
    @Test
    fun nothingSavedMeansNoSession() {
        assertNull(SessionStore(MemoryVault()).load())
    }

    @Test
    fun aSavedSessionComesBackTheSame() {
        val store = SessionStore(MemoryVault())
        val session = SavedSession("https://dorai.firm.local:8443", "AB".repeat(32), testTokens)
        store.save(session)
        assertEquals(session, store.load())
    }

    @Test
    fun aSignedOutSessionKeepsTheServer() {
        val store = SessionStore(MemoryVault())
        store.save(SavedSession("http://localhost:8080", null, testTokens))
        store.save(SavedSession("http://localhost:8080", null, null))
        assertEquals(SavedSession("http://localhost:8080", null, null), store.load())
    }

    @Test
    fun damagedTokensCountAsSignedOut() {
        val vault = MemoryVault()
        val store = SessionStore(vault)
        store.save(SavedSession("http://localhost:8080", null, testTokens))
        vault.write("tokens", "{not json")
        assertEquals(SavedSession("http://localhost:8080", null, null), store.load())
    }

    @Test
    fun savingNothingForgetsEverything() {
        val vault = MemoryVault()
        val store = SessionStore(vault)
        store.save(SavedSession("http://localhost:8080", "AB".repeat(32), testTokens))
        store.save(null)
        assertNull(store.load())
        assertNull(vault.read("tokens"))
        assertNull(vault.read("fingerprint"))
    }
}
