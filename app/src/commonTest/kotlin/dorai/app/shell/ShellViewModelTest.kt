package dorai.app.shell

import dorai.app.components.AppLanguage
import dorai.app.components.runImmediate
import dorai.app.connect.ServerProbe
import dorai.app.theme.ThemeMode
import dorai.shared.api.ApiResult
import dorai.shared.api.DoraiApi
import dorai.shared.model.Health
import dorai.shared.model.Problem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull

class ShellViewModelTest {
    private val healthy = ServerProbe { ApiResult.Success(Health("ok", "DORAi")) }

    // DoraiApi only builds a client here; no test makes a request.
    private fun shell(probe: ServerProbe = healthy) = ShellViewModel(probe) { DoraiApi(it) }

    @Test
    fun startsOnTheConnectScreenWithTheDefaultAddress() {
        val shell = shell()
        assertNull(shell.workspace)
        assertEquals(dorai.app.connect.defaultServerAddress, shell.connect.state.address)
    }

    @Test
    fun aHealthyServerOpensTheWorkspaceOnCloudInventory() {
        val shell = shell()
        shell.select(Sections.EXPORT)
        shell.connect.onAddressChange("http://localhost:8080/")
        runImmediate { shell.connect() }
        assertEquals("http://localhost:8080", shell.workspace?.address)
        assertEquals(Sections.INVENTORY, shell.state.section)
    }

    @Test
    fun anUnreachableServerKeepsTheConnectScreen() {
        val shell = shell { ApiResult.Failure(Problem(title = "Network error", status = 0)) }
        runImmediate { shell.connect() }
        assertNull(shell.workspace)
        assertNotNull(shell.connect.state.error)
    }

    @Test
    fun changingServerDropsTheWorkspaceAndANewConnectionStartsClean() {
        val shell = shell()
        runImmediate { shell.connect() }
        val first = shell.workspace
        shell.changeServer()
        assertNull(shell.workspace)
        runImmediate { shell.connect() }
        assertNotSame(first, shell.workspace)
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
