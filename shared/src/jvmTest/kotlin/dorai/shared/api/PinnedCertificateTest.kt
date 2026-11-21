package dorai.shared.api

import com.sun.net.httpserver.HttpsConfigurator
import com.sun.net.httpserver.HttpsServer
import io.ktor.network.tls.certificates.buildKeyStore
import kotlinx.coroutines.test.runTest
import java.net.InetSocketAddress
import java.security.KeyStore
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** A local HTTPS server with a self-signed certificate for another host name, as a firm's server would have. */
class PinnedCertificateTest {
    private val password = "test-only"
    private val store: KeyStore = buildKeyStore {
        certificate("dorai") {
            password = this@PinnedCertificateTest.password
            domains = listOf("dorai.firm.example")
        }
    }
    private val fingerprint = sha256Hex(store.getCertificate("dorai").encoded)

    private val server = HttpsServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        val keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(store, password.toCharArray()) }
        httpsConfigurator = HttpsConfigurator(SSLContext.getInstance("TLS").apply { init(keys.keyManagers, null, null) })
        createContext("/api/health") { exchange ->
            val body = """{"status":"ok","app":"DORAi"}""".toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        start()
    }
    private val url = "https://127.0.0.1:${server.address.port}"

    @AfterTest
    fun stop() = server.stop(0)

    @Test
    fun thePinnedCertificateIsTrustedAtAnAddressItDoesNotName() = runTest {
        DoraiApi(url, fingerprint = fingerprint).use { api ->
            assertEquals("ok", api.health().getOrNull()?.status)
        }
    }

    @Test
    fun theLinkFormatWithPrefixAndColonsWorksToo() = runTest {
        val linkStyle = "sha256:" + fingerprint.lowercase().chunked(2).joinToString(":")
        DoraiApi(url, fingerprint = linkStyle).use { assertIs<ApiResult.Success<*>>(it.health()) }
    }

    @Test
    fun anotherCertificateFailsAsCertificateChanged() = runTest {
        DoraiApi(url, fingerprint = "AB".repeat(32)).use { api ->
            val problem = assertIs<ApiResult.Failure>(api.health()).problem
            assertEquals(CertificateProblem.CHANGED, problem.type)
        }
    }

    @Test
    fun withoutAFingerprintTheSelfSignedCertificateIsRefused() = runTest {
        DoraiApi(url).use { api ->
            val problem = assertIs<ApiResult.Failure>(api.health()).problem
            assertEquals("Network error", problem.title)
        }
    }
}
