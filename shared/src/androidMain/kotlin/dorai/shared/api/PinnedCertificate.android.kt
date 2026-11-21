package dorai.shared.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

// Same code as PinnedCertificate.jvm.kt: desktop and Android both use OkHttp, but share no source set.

actual fun pinnedHttpClient(fingerprint: String): HttpClient {
    val trust = PinnedTrustManager(normalizeFingerprint(fingerprint))
    val tls = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trust), null) }
    return HttpClient(OkHttp) {
        engine {
            config {
                sslSocketFactory(tls.socketFactory, trust)
                // The host name is not in question once the certificate itself is the pinned one.
                hostnameVerifier { _, session -> trust.matches(session.peerCertificates.firstOrNull()?.encoded) }
            }
        }
    }
}

internal class PinnedTrustManager(private val expected: String) : X509TrustManager {
    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        val actual = chain?.firstOrNull()?.encoded?.let(::sha256Hex) ?: throw CertificateException("The server sent no certificate.")
        if (actual != expected) throw CertificateException("Pinned certificate mismatch", CertificateChanged(expected, actual))
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) =
        throw CertificateException("Client certificates are not used.")

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()

    fun matches(der: ByteArray?): Boolean = der != null && sha256Hex(der) == expected
}

internal fun sha256Hex(der: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(der).joinToString("") { "%02X".format(it) }
