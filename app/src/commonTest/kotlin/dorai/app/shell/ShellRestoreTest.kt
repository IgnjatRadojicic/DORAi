package dorai.app.shell

import dorai.app.auth.FakeLoginGateway
import dorai.app.auth.LoginViewModel
import dorai.app.auth.MemoryTokenVault
import dorai.app.components.AppLanguage
import dorai.app.components.runImmediate
import dorai.app.connect.MemorySettingsStore
import dorai.app.connect.SavedServer
import dorai.app.connect.SavedSettings
import dorai.app.connect.ServerLink
import dorai.app.theme.ThemeMode
import dorai.shared.api.ApiResult
import dorai.shared.api.DoraiApi
import dorai.shared.api.TokenStore
import dorai.shared.model.Health
import dorai.shared.model.LoginResponse
import dorai.shared.model.Role
import dorai.shared.model.Tokens
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/** What survives a restart: server, settings and a session that can still be refreshed. */
class ShellRestoreTest {
    private val now = Instant.fromEpochSeconds(1_800_000_000)
    private val server = ServerLink("https://dorai.firm.local:8443", "AB".repeat(32))

    private fun session(refreshUntil: Instant, username: String = "luka") =
        Tokens("access", now, "refresh", refreshUntil, username, Role.Reviewer)

    /** A vault holding [tokens] for [server]. */
    private fun vaultWith(tokens: Tokens? = null) = MemoryTokenVault(listOfNotNull(tokens?.let { server.address to it }).toMap())

    private class Opened {
        val links = mutableListOf<ServerLink>()
        var tokens: TokenStore? = null
    }

    private fun shell(settings: MemorySettingsStore, vault: MemoryTokenVault, opened: Opened = Opened()) = ShellViewModel(
        address = "http://localhost:8080",
        storage = AppStorage(settings, vault),
        openApi = { link, tokens -> opened.links += link; opened.tokens = tokens; DoraiApi(link.address) },
        probe = { ApiResult.Success(Health("ok", "DORAi")) },
        gateway = FakeLoginGateway(loginAnswer = ApiResult.Success(LoginResponse(false, session(Instant.DISTANT_FUTURE)))),
    )

    @Test
    fun aValidSavedSessionOpensTheWorkspaceOnTheSavedServer() {
        val opened = Opened()
        val settings = MemorySettingsStore(SavedSettings(server, "luka", ThemeMode.LIGHT, AppLanguage.GERMAN))
        val shell = shell(settings, vaultWith(session(now + 1.days)), opened)
        assertFalse(shell.ready)
        runImmediate { shell.restore(now) }
        assertTrue(shell.ready)
        assertEquals("luka", shell.workspace?.session?.username)
        assertEquals(server, opened.links.last())
        assertEquals(server.address, shell.connection.address)
        assertEquals(ThemeMode.LIGHT, shell.state.theme)
        assertEquals(AppLanguage.GERMAN, shell.state.language)
    }

    @Test
    fun anExpiredSessionIsForgottenAndTheLoginPageKeepsTheUsername() {
        val vault = vaultWith(session(now))
        val shell = shell(MemorySettingsStore(SavedSettings(server, "luka")), vault)
        runImmediate { shell.restore(now) }
        assertNull(shell.workspace)
        assertEquals("luka", shell.login.state.username)
        runImmediate { assertNull(vault.read(server.address)) }
    }

    @Test
    fun nothingSavedStartsOnTheLoginPageAtTheDefaultServer() {
        val shell = shell(MemorySettingsStore(), vaultWith())
        runImmediate { shell.restore(now) }
        assertNull(shell.workspace)
        assertEquals("http://localhost:8080", shell.connection.address)
    }

    @Test
    fun aRefusedRefreshReturnsToTheLoginPage() {
        val opened = Opened()
        val shell = shell(MemorySettingsStore(SavedSettings(server, "luka")), vaultWith(session(Instant.DISTANT_FUTURE)), opened)
        runImmediate { shell.restore(now) }
        assertNotNull(shell.workspace)
        // What DoraiApi does when the server refuses the refresh token.
        runImmediate { opened.tokens!!.save(null) }
        assertNull(shell.workspace)
        assertEquals(LoginViewModel.SESSION_ENDED, shell.login.state.error)
        assertEquals("luka", shell.login.state.username)
    }

    @Test
    fun loginThemeAndLanguageAreSavedForTheNextStart() {
        val settings = MemorySettingsStore()
        val shell = shell(settings, vaultWith())
        runImmediate { shell.restore(now) }
        shell.login.onUsernameChange("luka")
        shell.login.onPasswordChange("correct horse battery")
        runImmediate { shell.login() }
        shell.setTheme(ThemeMode.LIGHT)
        shell.setLanguage(AppLanguage.FRENCH)
        runImmediate {
            val local = ServerLink("http://localhost:8080")
            assertEquals(SavedSettings(local, "luka", ThemeMode.LIGHT, AppLanguage.FRENCH, servers = listOf(SavedServer(local, "luka"))), settings.load())
        }
    }

    @Test
    fun logoutKeepsTheServerAndUsername() {
        val settings = MemorySettingsStore(SavedSettings(server, "luka"))
        val shell = shell(settings, vaultWith(session(Instant.DISTANT_FUTURE)))
        runImmediate { shell.restore(now) }
        runImmediate { shell.logout() }
        assertNull(shell.workspace)
        runImmediate { assertEquals(SavedSettings(server, "luka", servers = listOf(SavedServer(server, "luka"))), settings.load()) }
    }

    private val other = ServerLink("https://dorai.example.com")

    /** Two saved servers, each with a session of its own. */
    private fun twoServers(opened: Opened = Opened()): Pair<ShellViewModel, MemoryTokenVault> {
        val settings = MemorySettingsStore(SavedSettings(server, "luka", servers = listOf(SavedServer(server, "luka"), SavedServer(other, "ana"))))
        val vault = MemoryTokenVault(mapOf(server.address to session(Instant.DISTANT_FUTURE), other.address to session(Instant.DISTANT_FUTURE, "ana")))
        return shell(settings, vault, opened) to vault
    }

    @Test
    fun switchingToASavedServerOpensItsOwnSessionWithoutAnInvite() {
        val opened = Opened()
        val (shell, vault) = twoServers(opened)
        runImmediate { shell.restore(now) }
        assertEquals("luka", shell.workspace?.session?.username)

        shell.leave()
        assertNull(shell.workspace)
        runImmediate { shell.useServer(other, now) }
        assertEquals("ana", shell.workspace?.session?.username)
        assertEquals(other, opened.links.last())
        assertEquals(other.address, shell.serverAddress)

        // Back again: the first server's session was kept the whole time.
        runImmediate { shell.useServer(server, now) }
        assertEquals("luka", shell.workspace?.session?.username)
        runImmediate { assertNotNull(vault.read(other.address)) }
    }

    @Test
    fun aSavedServerWithoutASessionAsksForThePasswordWithItsUsername() {
        val (shell, vault) = twoServers()
        runImmediate { vault.write(other.address, null) }
        runImmediate { shell.restore(now) }
        runImmediate { shell.useServer(other, now) }
        assertNull(shell.workspace)
        assertEquals("ana", shell.login.state.username)
    }

    @Test
    fun forgettingAServerRemovesItAndItsSessionButNeverTheOneInUse() {
        val (shell, vault) = twoServers()
        runImmediate { shell.restore(now) }
        runImmediate { shell.forgetServer(server) }
        assertEquals(2, shell.servers.size)
        runImmediate { shell.forgetServer(other) }
        assertEquals(listOf(server), shell.servers.map { it.link })
        runImmediate { assertNull(vault.read(other.address)) }
        assertEquals("luka", shell.workspace?.session?.username)
    }
}
