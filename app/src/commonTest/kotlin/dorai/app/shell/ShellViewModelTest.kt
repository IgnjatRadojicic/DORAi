package dorai.app.shell

import dorai.app.components.AppLanguage
import dorai.app.components.runImmediate
import dorai.app.connect.ServerStatus
import dorai.app.theme.ThemeMode
import dorai.shared.api.ApiResult
import dorai.shared.api.DoraiApi
import dorai.shared.model.Health
import dorai.shared.model.LoginResponse
import dorai.shared.model.Problem
import dorai.shared.model.Role
import dorai.shared.model.Tokens
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class ShellViewModelTest {
    private fun tokens(role: Role) = Tokens(
        accessToken = "access", accessExpiresAt = Instant.fromEpochSeconds(0),
        refreshToken = "refresh", refreshExpiresAt = Instant.fromEpochSeconds(0),
        username = "luka", role = role,
    )

    // DoraiApi only builds a client here; health and login are fakes, and logout has no tokens to send.
    private fun shell(answer: ApiResult<LoginResponse> = ApiResult.Success(LoginResponse(false, tokens(Role.Reviewer)))) =
        ShellViewModel(
            address = "http://localhost:8080",
            api = DoraiApi("http://localhost:8080"),
            probe = { ApiResult.Success(Health("ok", "DORAi")) },
            gateway = { _, _ -> answer },
        )

    private fun ShellViewModel.signIn() {
        login.onUsernameChange("luka")
        login.onPasswordChange("correct horse battery")
        runImmediate { login() }
    }

    @Test
    fun startsOnTheLoginPage() {
        val shell = shell()
        assertNull(shell.workspace)
        assertEquals("http://localhost:8080", shell.connection.address)
        assertEquals(ServerStatus.CHECKING, shell.connection.status)
    }

    @Test
    fun theConnectionMarkTurnsGreenWhenTheServerAnswers() {
        val shell = shell()
        runImmediate { shell.connection.check() }
        assertEquals(ServerStatus.CONNECTED, shell.connection.status)
    }

    @Test
    fun aSuccessfulLoginOpensThePlatformOnCloudInventory() {
        val shell = shell()
        shell.select(Sections.EXPORT)
        shell.signIn()
        assertEquals("luka", shell.workspace?.session?.username)
        assertEquals(Sections.INVENTORY, shell.state.section)
    }

    @Test
    fun aRefusedLoginStaysOnTheLoginPage() {
        val shell = shell(ApiResult.Failure(Problem(title = "Unauthorized", status = 401, detail = "Wrong username or password.")))
        shell.signIn()
        assertNull(shell.workspace)
        assertNotNull(shell.login.state.error)
    }

    @Test
    fun logoutReturnsToLoginAndTheNextLoginStartsClean() {
        val shell = shell()
        shell.signIn()
        val first = shell.workspace
        runImmediate { shell.logout() }
        assertNull(shell.workspace)
        assertEquals("", shell.login.state.password)
        shell.signIn()
        assertNotSame(first, shell.workspace)
    }

    @Test
    fun reviewersAndAdminsMayCheckAndExportViewersMayNot() {
        assertFalse(Workspace(tokens(Role.Viewer), DoraiApi("http://localhost:8080")).canReview)
        assertTrue(Workspace(tokens(Role.Reviewer), DoraiApi("http://localhost:8080")).canReview)
        assertTrue(Workspace(tokens(Role.Admin), DoraiApi("http://localhost:8080")).canReview)
    }

    @Test
    fun selectsOnlyKnownSections() {
        val shell = shell()
        listOf(Sections.REVIEW, Sections.FINDINGS, Sections.EXPORT, Sections.INVENTORY).forEach {
            shell.select(it)
            assertEquals(it, shell.state.section)
        }
        shell.select("admin")
        assertEquals(Sections.INVENTORY, shell.state.section)
    }

    @Test
    fun theFrameShowsTheFourSectionsInOrder() {
        assertEquals(listOf("01", "02", "03", "04"), shellSections.map { it.number })
        assertEquals(
            listOf(Sections.INVENTORY, Sections.REVIEW, Sections.FINDINGS, Sections.EXPORT),
            shellSections.map { it.id },
        )
    }

    @Test
    fun themeAndLanguageChange() {
        val shell = shell()
        shell.setTheme(ThemeMode.LIGHT)
        shell.setLanguage(AppLanguage.GERMAN)
        assertEquals(ThemeMode.LIGHT, shell.state.theme)
        assertEquals(AppLanguage.GERMAN, shell.state.language)
    }
}
