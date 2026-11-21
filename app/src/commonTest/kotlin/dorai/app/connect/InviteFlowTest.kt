package dorai.app.connect

import dorai.app.auth.FakeLoginGateway
import dorai.app.auth.failure
import dorai.app.components.runImmediate
import dorai.app.shell.ShellViewModel
import dorai.shared.api.ApiResult
import dorai.shared.api.DoraiApi
import dorai.shared.model.Health
import dorai.shared.model.RedeemResponse
import dorai.shared.model.Role
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Opening an invite link: switch server, redeem silently, leave an empty login page. */
class InviteFlowTest {
    private val link = "dorai://connect?server=http%3A%2F%2F10.0.2.2%3A8080&code=VZ8W-PW4W"
    private val ok: ApiResult<RedeemResponse> = ApiResult.Success(RedeemResponse("DORAi", "tester", Role.Reviewer))

    private class Recorder {
        val opened = mutableListOf<String>()
        val redeemed = mutableListOf<String>()
    }

    // DoraiApi only builds a client here; every server call is a fake.
    private fun shell(recorder: Recorder = Recorder(), redeem: ApiResult<RedeemResponse> = ok) = ShellViewModel(
        address = "http://localhost:8080",
        openApi = { recorder.opened += it; DoraiApi(it) },
        probe = { ApiResult.Success(Health("ok", "DORAi")) },
        gateway = FakeLoginGateway(),
        invites = { code -> recorder.redeemed += code; redeem },
    )

    @Test
    fun aValidLinkSwitchesServerRedeemsTheCodeAndLeavesAnEmptyLoginPage() {
        val recorder = Recorder()
        val shell = shell(recorder)
        shell.login.onUsernameChange("someone")
        runImmediate { shell.openInvite(link) }
        assertEquals("http://10.0.2.2:8080", shell.connection.address)
        assertEquals(listOf("http://localhost:8080", "http://10.0.2.2:8080"), recorder.opened)
        assertEquals(listOf("VZ8W-PW4W"), recorder.redeemed)
        assertEquals("", shell.login.state.username)
        assertNull(shell.invite.state.error)
        assertFalse(shell.invite.state.open)
    }

    @Test
    fun aLinkForTheSameServerKeepsTheClient() {
        val recorder = Recorder()
        val shell = shell(recorder)
        runImmediate { shell.openInvite("dorai://connect?server=http%3A%2F%2Flocalhost%3A8080&code=C") }
        assertEquals(listOf("http://localhost:8080"), recorder.opened)
        assertEquals(listOf("C"), recorder.redeemed)
    }

    @Test
    fun aUsedLinkShowsTheServerMessage() {
        val shell = shell(redeem = failure(404, "This invite code is not valid. Ask your administrator for a new link."))
        runImmediate { shell.openInvite(link) }
        assertEquals("This invite code is not valid. Ask your administrator for a new link.", shell.invite.state.error)
        assertTrue(shell.invite.state.open)
    }

    @Test
    fun aServerThatDoesNotAnswerIsReported() {
        val shell = shell(redeem = failure(0))
        runImmediate { shell.openInvite(link) }
        assertEquals(InviteViewModel.UNREACHABLE, shell.invite.state.error)
    }

    @Test
    fun aBrokenLinkChangesNothing() {
        val recorder = Recorder()
        val shell = shell(recorder)
        runImmediate { shell.openInvite("dorai://connect?code=only") }
        assertEquals(INCOMPLETE, shell.invite.state.error)
        assertEquals("http://localhost:8080", shell.connection.address)
        assertTrue(recorder.redeemed.isEmpty())
    }

    @Test
    fun aSecureLinkWaitsForCertificatePinning() {
        val recorder = Recorder()
        val shell = shell(recorder)
        val fp = "AB".repeat(32)
        runImmediate { shell.openInvite("dorai://connect?server=https%3A%2F%2Fdorai.firm.local%3A8443&code=C&fp=sha256:$fp") }
        assertEquals(InviteViewModel.SECURE_NOT_READY, shell.invite.state.error)
        assertEquals("http://localhost:8080", shell.connection.address)
        assertTrue(recorder.redeemed.isEmpty())
    }

    @Test
    fun aLinkOpenedWhileSignedInLogsOutFirst() {
        val shell = shell()
        shell.login.onUsernameChange("luka")
        shell.login.onPasswordChange("correct horse battery")
        runImmediate { shell.login() }
        assertTrue(shell.workspace != null)
        runImmediate { shell.openInvite(link) }
        assertNull(shell.workspace)
    }

    @Test
    fun theFieldOpensClosesAndKeepsTypedText() {
        val shell = shell()
        shell.invite.toggle()
        assertTrue(shell.invite.state.open)
        shell.invite.onTextChange("dorai://")
        assertTrue(shell.invite.state.canUse)
        shell.invite.toggle()
        assertFalse(shell.invite.state.open)
    }
}
