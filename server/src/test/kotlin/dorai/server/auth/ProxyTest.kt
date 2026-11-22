package dorai.server.auth

import dorai.server.db.TestPostgres
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** A server behind a Cloudflare Tunnel (`DORAI_PROXY=cloudflare`). */
class ProxyTest {
    private val secret = "x".repeat(40)

    @Test
    fun theSettingIsReadAndChecked() {
        val env = mapOf("DORAI_JWT_SECRET" to secret, "DORAI_PUBLIC_URL" to "https://dorai.example.com/")
        assertEquals(Proxy.NONE, AuthConfig.fromEnv(env, "http://localhost:8080").proxy)
        val behind = AuthConfig.fromEnv(env + ("DORAI_PROXY" to "Cloudflare"), "http://localhost:8080")
        assertEquals(Proxy.CLOUDFLARE to "https://dorai.example.com", behind.proxy to behind.publicUrl)
        // A proxy serves https; an http public address is a mistake that would break every invite.
        assertFailsWith<IllegalArgumentException> {
            AuthConfig.fromEnv(env + mapOf("DORAI_PROXY" to "cloudflare", "DORAI_PUBLIC_URL" to "http://dorai.example.com"), "x")
        }
        assertFailsWith<IllegalArgumentException> { AuthConfig.fromEnv(env + ("DORAI_PROXY" to "nginx"), "x") }
    }

    @Test
    fun invitesBehindATunnelCarryNoPin() {
        val db = TestPostgres.fresh()
        val config = AuthConfig(secret, "https://dorai.example.com", proxy = Proxy.CLOUDFLARE)
        val link = assertNotNull(AuthService(db, config, fingerprint = "AB12CD").bootstrap()).invite.link!!
        assertTrue(link.startsWith("dorai://connect?server=https%3A%2F%2Fdorai.example.com&code="), link)
        assertFalse("fp=" in link, link)
    }

    @Test
    fun theVisitorAddressComesFromCloudflareOnlyThroughTheTunnel() {
        // cloudflared on the host reaches the container through Docker's gateway or loopback.
        assertEquals("203.0.113.7", clientAddress("172.18.0.1", "203.0.113.7", Proxy.CLOUDFLARE))
        assertEquals("2001:db8::7", clientAddress("127.0.0.1", " 2001:db8::7 ", Proxy.CLOUDFLARE))
        // Anyone reaching the server directly can't pick their own address with the header.
        assertEquals("198.51.100.4", clientAddress("198.51.100.4", "203.0.113.7", Proxy.CLOUDFLARE))
        // Without the setting the header means nothing, and a missing header keeps the peer.
        assertEquals("172.18.0.1", clientAddress("172.18.0.1", "203.0.113.7", Proxy.NONE))
        assertEquals("172.18.0.1", clientAddress("172.18.0.1", null, Proxy.CLOUDFLARE))
    }

    @Test
    fun localPeers() {
        listOf("127.0.0.1", "::1", "0:0:0:0:0:0:0:1", "localhost", "10.1.2.3", "172.16.0.1", "172.31.255.1",
            "192.168.1.10", "fd00::1", "::ffff:172.17.0.1").forEach { assertTrue(isLocalPeer(it), it) }
        listOf("172.32.0.1", "172.15.0.1", "8.8.8.8", "203.0.113.7", "2001:db8::1").forEach { assertFalse(isLocalPeer(it), it) }
    }
}
