package dorai.app.connect

import kotlin.test.Test
import kotlin.test.assertEquals

class InviteLinkTest {
    private val fp = "AB12CD34EF56AB12CD34EF56AB12CD34EF56AB12CD34EF56AB12CD34EF56AB12"

    private fun valid(text: String) = (parseInviteLink(text) as InviteLinkResult.Valid).link
    private fun invalid(text: String) = (parseInviteLink(text) as InviteLinkResult.Invalid).message

    @Test
    fun readsTheLinkTheLocalServerPrints() {
        assertEquals(
            InviteLink("http://localhost:8080", "VZ8W-PW4W", null),
            valid("dorai://connect?server=http%3A%2F%2Flocalhost%3A8080&code=VZ8W-PW4W"),
        )
    }

    @Test
    fun readsAnHttpsLinkWithItsFingerprint() {
        val link = valid("  dorai://connect?server=https%3A%2F%2Fdorai.firm.local%3A8443&code=K7Q2-9XPA&fp=sha256:$fp \n")
        assertEquals("https://dorai.firm.local:8443", link.server)
        assertEquals("K7Q2-9XPA", link.code)
        assertEquals(fp, link.fingerprint)
    }

    @Test
    fun fingerprintCaseAndColonsDoNotMatter() {
        val colons = fp.chunked(2).joinToString(":").lowercase()
        assertEquals(fp, valid("dorai://connect?server=https%3A%2F%2Fh&code=C&fp=sha256:$colons").fingerprint)
    }

    @Test
    fun theEmulatorAddressMayUseHttp() {
        assertEquals("http://10.0.2.2:8080", valid("dorai://connect?server=http%3A%2F%2F10.0.2.2%3A8080&code=C").server)
    }

    @Test
    fun otherTextIsNotAnInvite() {
        assertEquals(NOT_AN_INVITE, invalid("https://dorai.firm.local:8443"))
        assertEquals(NOT_AN_INVITE, invalid(""))
    }

    @Test
    fun missingPartsAreIncomplete() {
        assertEquals(INCOMPLETE, invalid("dorai://connect?server=http%3A%2F%2Flocalhost%3A8080"))
        assertEquals(INCOMPLETE, invalid("dorai://connect?code=VZ8W-PW4W"))
        assertEquals(INCOMPLETE, invalid("dorai://connect?server=ftp%3A%2F%2Fhost&code=C"))
    }

    @Test
    fun aBrokenFingerprintIsRefused() {
        assertEquals(INCOMPLETE, invalid("dorai://connect?server=https%3A%2F%2Fdorai.firm.local&code=C&fp=sha256:ABC"))
    }

    @Test
    fun httpsWithoutAFingerprintIsAServerBehindATunnel() {
        // Behind a Cloudflare Tunnel the link has no pin; the app then checks the public certificate.
        val link = valid("dorai://connect?server=https%3A%2F%2Fdorai.example.com&code=K7Q2-9XPA")
        assertEquals("https://dorai.example.com", link.server)
        assertEquals(null, link.fingerprint)
    }

    @Test
    fun plainHttpToARealServerIsRefused() {
        assertEquals(INSECURE, invalid("dorai://connect?server=http%3A%2F%2Fdorai.firm.local%3A8080&code=C"))
    }
}
