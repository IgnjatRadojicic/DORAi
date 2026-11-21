package dorai.app.shell

import dorai.app.auth.FakeLoginGateway
import dorai.app.auth.MemoryTokenVault
import dorai.app.components.AppLanguage
import dorai.app.components.runImmediate
import dorai.app.connect.MemorySettingsStore
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

    private fun session(refreshUntil: Instant) =
        Tokens("access", now, "refresh", refreshUntil, "luka", Role.Reviewer)

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
        val shell = shell(settings, MemoryTokenVault(session(now + 1.days)), opened)
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
        val vault = MemoryTokenVault(session(now))
        val shell = shell(MemorySettingsStore(SavedSettings(server, "luka")), vault)
        runImmediate { shell.restore(now) }
        assertNull(shell.workspace)
        assertEquals("luka", shell.login.state.username)
        runImmediate { assertNull(vault.read()) }
    }

    @Test
    fun nothingSavedStartsOnTheLoginPageAtTheDefaultServer() {
        val shell = shell(MemorySettingsStore(), MemoryTokenVault())
        runImmediate { shell.restore(now) }
        assertNull(shell.workspace)
        assertEquals("http://localhost:8080", shell.connection.address)
    }

    @Test
    fun aRefusedRefreshReturnsToTheLoginPage() {
        val opened = Opened()
        val shell = shell(MemorySettingsStore(SavedSettings(server, "luka")), MemoryTokenVault(session(Instant.DISTANT_FUTURE)), opened)
        runImmediate { shell.restore(now) }
        assertNotNull(shell.workspace)
        // What DoraiApi does when the server refuses the refresh token.
        runImmediate { opened.tokens!!.save(null) }
        assertNull(shell.workspace)
    }

    @Test
    fun loginThemeAndLanguageAreSavedForTheNextStart() {
        val settings = MemorySettingsStore()
        val shell = shell(settings, MemoryTokenVault())
        runImmediate { shell.restore(now) }
        shell.login.onUsernameChange("luka")
        shell.login.onPasswordChange("correct horse battery")
        runImmediate { shell.login() }
        shell.setTheme(ThemeMode.LIGHT)
        shell.setLanguage(AppLanguage.FRENCH)
        runImmediate {
            assertEquals(SavedSettings(ServerLink("http://localhost:8080"), "luka", ThemeMode.LIGHT, AppLanguage.FRENCH), settings.load())
        }
    }

    @Test
    fun logoutKeepsTheServerAndUsername() {
        val settings = MemorySettingsStore(SavedSettings(server, "luka"))
        val shell = shell(settings, MemoryTokenVault(session(Instant.DISTANT_FUTURE)))
        runImmediate { shell.restore(now) }
        runImmediate { shell.logout() }
        assertNull(shell.workspace)
        runImmediate { assertEquals(SavedSettings(server, "luka"), settings.load()) }
    }
}
