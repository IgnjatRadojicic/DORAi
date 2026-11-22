package dorai.app.connect

import io.ktor.http.decodeURLQueryComponent

/**
 * An invite from the admin: `dorai://connect?server=...&code=...&fp=sha256:...` (plan section 5).
 * [fingerprint] is the SHA-256 of the server certificate as 64 uppercase hex characters, or null for a local http
 * server and for an https server behind a proxy with a public certificate.
 */
data class InviteLink(val server: String, val code: String, val fingerprint: String?)

sealed interface InviteLinkResult {
    data class Valid(val link: InviteLink) : InviteLinkResult
    data class Invalid(val message: String) : InviteLinkResult
}

/** Hosts a development server runs on; only these may use plain http. */
private val LOCAL_HOSTS = setOf("localhost", "127.0.0.1", "10.0.2.2")
private val SHA256_HEX = Regex("[0-9A-F]{64}")

/** Reads a pasted or opened invite link. Anything broken or unsafe is refused with a message the user can act on. */
fun parseInviteLink(text: String): InviteLinkResult {
    val link = text.trim()
    if (!link.startsWith("dorai://connect?", ignoreCase = true)) return InviteLinkResult.Invalid(NOT_AN_INVITE)
    val params = link.substringAfter('?').split('&')
        .filter { it.contains('=') }
        .associate { it.substringBefore('=').lowercase() to it.substringAfter('=').decodeURLQueryComponent().trim() }

    val server = params["server"]?.trimEnd('/').orEmpty()
    val code = params["code"].orEmpty()
    if (server.isEmpty() || code.isEmpty()) return InviteLinkResult.Invalid(INCOMPLETE)

    val https = server.startsWith("https://", ignoreCase = true)
    val http = server.startsWith("http://", ignoreCase = true)
    val host = server.substringAfter("://").substringBefore('/').substringBefore(':').lowercase()
    if ((!https && !http) || host.isEmpty() || server.any { it.isWhitespace() }) return InviteLinkResult.Invalid(INCOMPLETE)

    val fingerprint = params["fp"]?.takeIf { it.isNotEmpty() }?.let { fp ->
        val hex = fp.removePrefix("sha256:").removePrefix("SHA256:").replace(":", "").uppercase()
        if (!SHA256_HEX.matches(hex)) return InviteLinkResult.Invalid(INCOMPLETE)
        hex
    }
    // An https link without a pin is a server behind a proxy with a public certificate (a Cloudflare
    // Tunnel): the connection is checked against the system's certificate authorities, like a browser does.
    if (http && host !in LOCAL_HOSTS) return InviteLinkResult.Invalid(INSECURE)
    return InviteLinkResult.Valid(InviteLink(server, code, fingerprint))
}

const val NOT_AN_INVITE = "This is not a DORAi invite link. It starts with dorai://connect"
const val INCOMPLETE = "This invite link is incomplete. Copy the whole link from the email, or ask your administrator for a new one."
const val INSECURE = "This invite link points to a server without encryption. Ask your administrator for a new link."
