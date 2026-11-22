package dorai.server.auth

import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * Auth is on whenever the server has a database (every real install) and off on fixtures in memory
 * (tests, quick local runs, app development).
 */
data class AuthConfig(
    val jwtSecret: String,
    /** Address the apps use; goes into invite links. */
    val publicUrl: String,
    /** Name of the admin created on the first start of an empty install. */
    val adminUsername: String = "admin",
    val accessTtl: Duration = 15.minutes,
    val refreshTtl: Duration = 30.days,
    val inviteTtl: Duration = 72.hours,
    /** Calls per minute from one address to redeem, login and password change. */
    val authCallsPerMinute: Int = 10,
    /** Wrong passwords for one username within [failedLoginWindow] before it is refused for [lockout]. */
    val maxFailedLogins: Int = 5,
    val failedLoginWindow: Duration = 15.minutes,
    val lockout: Duration = 15.minutes,
    /** What stands between the apps and the server; decides the invite link and the client address. */
    val proxy: Proxy = Proxy.NONE,
) {
    override fun toString() = "AuthConfig(publicUrl=$publicUrl, adminUsername=$adminUsername, proxy=$proxy)"

    companion object {
        const val MIN_SECRET_LENGTH = 32

        fun fromEnv(env: Map<String, String> = System.getenv(), defaultPublicUrl: String): AuthConfig {
            fun get(name: String) = env[name]?.trim()?.takeIf { it.isNotEmpty() }
            val secret = get("DORAI_JWT_SECRET")
                ?: error("DORAI_JWT_SECRET is required when the server has a database (auth is on).")
            require(secret.length >= MIN_SECRET_LENGTH) { "DORAI_JWT_SECRET must be at least $MIN_SECRET_LENGTH characters." }
            val proxy = Proxy.parse(get("DORAI_PROXY"))
            val publicUrl = (get("DORAI_PUBLIC_URL") ?: defaultPublicUrl).trimEnd('/')
            require(proxy == Proxy.NONE || publicUrl.startsWith("https://")) {
                "DORAI_PROXY=${proxy.name.lowercase()} needs DORAI_PUBLIC_URL to be the https address the proxy serves."
            }
            return AuthConfig(
                jwtSecret = secret,
                publicUrl = publicUrl,
                adminUsername = Usernames.normalize(get("DORAI_ADMIN_USERNAME") ?: "admin"),
                proxy = proxy,
            )
        }
    }
}

/**
 * `NONE`: the apps reach the server directly and invite links pin its own certificate.
 * `CLOUDFLARE`: the apps reach it through a Cloudflare Tunnel. They see Cloudflare's public certificate,
 * so links carry no pin and the apps check the certificate like a browser does; the client address
 * comes from Cloudflare's `CF-Connecting-IP` header.
 */
enum class Proxy {
    NONE, CLOUDFLARE;

    companion object {
        fun parse(value: String?): Proxy = when (value?.lowercase()) {
            null, "none" -> NONE
            "cloudflare" -> CLOUDFLARE
            else -> throw IllegalArgumentException("DORAI_PROXY must be none or cloudflare, got '$value'.")
        }
    }
}

object Usernames {
    private val VALID = Regex("^[a-z0-9][a-z0-9._-]{2,63}$")

    /** Usernames are case-insensitive: stored and compared in lowercase. */
    fun normalize(raw: String): String {
        val name = raw.trim().lowercase()
        require(VALID.matches(name)) { "A username has 3 to 64 characters: letters, digits, dot, dash or underscore." }
        return name
    }
}
