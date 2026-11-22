package dorai.app.shell

import dorai.app.auth.FakeLoginGateway
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
            openApi = { server, _ -> DoraiApi(server.address) },
            probe = { ApiResult.Success(Health("ok", "DORAi")) },
            gateway = FakeLoginGateway(loginAnswer = answer, changeAnswer = ApiResult.Success(tokens(Role.Viewer))),
        )

    @Test
    fun aChangedTemporaryPasswordOpensThePlatformWithoutASecondLogin() {
        val shell = shell(ApiResult.Success(LoginResponse(passwordChangeRequired = true)))
        shell.signIn()
        assertNull(shell.workspace)
        shell.login.onNewPasswordChange("my-new-password")
        shell.login.onRepeatPasswordChange("my-new-password")
        runImmediate { shell.changePassword() }
        assertEquals(Role.Viewer, shell.workspace?.session?.role)
        assertEquals(Sections.INVENTORY, shell.state.section)
    }

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
    fun theNextSignInOpensTheLastSection() {
        val shell = shell()
        shell.signIn()
        shell.select(Sections.REVIEW)
        runImmediate { shell.logout() }
        shell.signIn()
        assertEquals(Sections.REVIEW, shell.state.section)
    }

    @Test
    fun backReturnsToTheSectionBefore() {
        val shell = shell()
        shell.signIn()
        shell.select(Sections.REVIEW)
        shell.select(Sections.EXPORT)
        assertTrue(shell.canGoBack)
        shell.back()
        assertEquals(Sections.REVIEW, shell.state.section)
        shell.back()
        assertEquals(Sections.INVENTORY, shell.state.section)
        assertFalse(shell.canGoBack)
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
        assertNull(shell.login.state.error)
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
        shell.select("unknown")
        assertEquals(Sections.INVENTORY, shell.state.section)
    }

    @Test
    fun onlyAdminsSeeTheAdministration() {
        val reviewer = shell()
        reviewer.signIn()
        reviewer.select(Sections.ADMIN)
        assertEquals(Sections.INVENTORY, reviewer.state.section)
        assertFalse(reviewer.workspace!!.sections.any { it.id == Sections.ADMIN })

        val admin = shell(ApiResult.Success(LoginResponse(false, tokens(Role.Admin))))
        admin.signIn()
        admin.select(Sections.ADMIN)
        assertEquals(Sections.ADMIN, admin.state.section)
    }

    @Test
    fun theFrameShowsTheSevenSectionsInOrder() {
        assertEquals(listOf("01", "02", "03", "04", "05", "06", "07"), shellSections.map { it.number })
        assertEquals(
            listOf(Sections.INVENTORY, Sections.REVIEW, Sections.FINDINGS, Sections.EXPORT, Sections.ROI, Sections.REGISTER, Sections.ADMIN),
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
