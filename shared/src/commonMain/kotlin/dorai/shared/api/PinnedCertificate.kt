package dorai.shared.api

import io.ktor.client.HttpClient

/** Problem type (RFC 9457 `type`) when the server shows another certificate than the invite link pinned. */
object CertificateProblem {
    const val CHANGED = "urn:dorai:problem:certificate-changed"
}

/** Thrown inside the TLS handshake when the server's certificate does not match the pinned fingerprint. */
class CertificateChanged(val expected: String, val actual: String) :
    Exception("The server's certificate is $actual, the invite link pinned $expected.")

/**
 * HTTP client that trusts only the server certificate whose SHA-256 is [fingerprint]
 * (64 hex characters, the `fp` of an invite link). The server's certificate is self-signed, so the usual
 * certificate authorities and host names are not checked: the exact certificate is.
 */
expect fun pinnedHttpClient(fingerprint: String): HttpClient

/** Upper-case hex without separators, as the server prints it and invite links carry it. */
internal fun normalizeFingerprint(fingerprint: String): String =
    fingerprint.removePrefix("sha256:").removePrefix("SHA256:").replace(":", "").uppercase()
