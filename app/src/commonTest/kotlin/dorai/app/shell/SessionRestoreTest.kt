package dorai.app.shell

import dorai.app.auth.FakeLoginGateway
import dorai.app.auth.LoginViewModel
import dorai.app.auth.MemoryVault
import dorai.app.auth.SavedSession
import dorai.app.auth.SessionStore
import dorai.app.auth.testTokens
import dorai.app.components.runImmediate
import dorai.shared.api.ApiResult
import dorai.shared.api.DoraiApi
import dorai.shared.api.TokenStore
import dorai.shared.model.Health
import dorai.shared.model.RedeemResponse
import dorai.shared.model.Role
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** The app remembers the server and the login between runs. */
@OptIn(ExperimentalTime::class)
class SessionRestoreTest {
    private val validUntil = Instant.fromEpochSeconds(2_000)
    private val session = testTokens.copy(refreshExpiresAt = validUntil)

    private class Opened {
        val targets = mutableListOf<ServerTarget>()
        var lastTokens: TokenStore? = null
    }

    // DoraiApi only builds a client here; every server call is a fake.
    private fun shell(store: SessionStore, opened: Opened = Opened(), clock: Long = 1_000) = ShellViewModel(
        address = "http://localhost:8080",
        sessions = store,
        openApi = { target, tokens -> opened.targets += target; opened.lastTokens = tokens; DoraiApi(target.address, tokens = tokens) },
        probe = { ApiResult.Success(Health("ok", "DORAi")) },
        gateway = FakeLoginGateway(),
        invites = { ApiResult.Success(RedeemResponse("DORAi", "tester", Role.Reviewer)) },
        now = { Instant.fromEpochSeconds(clock) },
    )

    private fun ShellViewModel.signIn() {
        login.onUsernameChange("luka")
        login.onPasswordChange("correct horse battery")
        runImmediate { login() }
    }

    @Test
    fun nothingSavedStaysOnTheLoginPageOfTheDevelopmentServer() {
        val shell = shell(SessionStore(MemoryVault()))
        runImmediate { shell.restore() }
        assertNull(shell.workspace)
        assertEquals("http://localhost:8080", shell.connection.address)
    }

    @Test
    fun aSavedValidLoginOpensThePlatformOnItsServer() {
        val store = SessionStore(MemoryVault())
        store.save(SavedSession("http://10.0.2.2:8080", null, session))
        val opened = Opened()
        val shell = shell(store, opened)
        runImmediate { shell.restore() }
        assertEquals("luka", shell.workspace?.session?.username)
        assertEquals("http://10.0.2.2:8080", shell.connection.address)
        assertEquals(ServerTarget("http://10.0.2.2:8080"), opened.targets.last())
    }

    @Test
    fun anExpiredLoginShowsTheLoginPageAndIsForgotten() {
        val store = SessionStore(MemoryVault())
        store.save(SavedSession("http://10.0.2.2:8080", null, session))
        val shell = shell(store, clock = 3_000)
        runImmediate { shell.restore() }
        assertNull(shell.workspace)
        assertEquals("http://10.0.2.2:8080", shell.connection.address)
        assertEquals(SavedSession("http://10.0.2.2:8080", null, null), store.load())
    }

    @Test
    fun aSavedServerWithoutLoginShowsItsLoginPage() {
        val store = SessionStore(MemoryVault())
        store.save(SavedSession("http://10.0.2.2:8080", null, null))
        val shell = shell(store)
        runImmediate { shell.restore() }
        assertNull(shell.workspace)
        assertEquals("http://10.0.2.2:8080", shell.connection.address)
    }

    @Test
    fun aSavedSecureServerWaitsForCertificatePinning() {
        val store = SessionStore(MemoryVault())
        store.save(SavedSession("https://dorai.firm.local:8443", "AB".repeat(32), session))
        val shell = shell(store)
        runImmediate { shell.restore() }
        assertNull(shell.workspace)
        assertEquals("http://localhost:8080", shell.connection.address)
    }

    @Test
    fun loginIsSavedAndLogoutForgetsItButKeepsTheServer() {
        val store = SessionStore(MemoryVault())
        val shell = shell(store)
        shell.signIn()
        assertEquals(SavedSession("http://localhost:8080", null, testTokens), store.load())
        runImmediate { shell.logout() }
        assertEquals(SavedSession("http://localhost:8080", null, null), store.load())
    }

    @Test
    fun anInviteSavesItsServer() {
        val store = SessionStore(MemoryVault())
        val shell = shell(store)
        runImmediate { shell.openInvite("dorai://connect?server=http%3A%2F%2F10.0.2.2%3A8080&code=C") }
        assertEquals(SavedSession("http://10.0.2.2:8080", null, null), store.load())
    }

    @Test
    fun theServerEndingTheSessionReturnsToTheLoginPage() {
        val store = SessionStore(MemoryVault())
        val opened = Opened()
        val shell = shell(store, opened)
        shell.signIn()
        assertNotNull(shell.workspace)
        // What DoraiApi does when the server refuses the refresh token.
        runImmediate { opened.lastTokens!!.save(null) }
        assertNull(shell.workspace)
        assertEquals(LoginViewModel.SESSION_ENDED, shell.login.state.error)
        assertEquals(SavedSession("http://localhost:8080", null, null), store.load())
    }

    @Test
    fun restoreRunsOnlyOnce() {
        val store = SessionStore(MemoryVault())
        store.save(SavedSession("http://10.0.2.2:8080", null, session))
        val opened = Opened()
        val shell = shell(store, opened)
        runImmediate { shell.restore() }
        runImmediate { shell.logout() }
        runImmediate { shell.restore() }
        assertNull(shell.workspace)
        assertFalse(opened.targets.size > 2)
    }
}
