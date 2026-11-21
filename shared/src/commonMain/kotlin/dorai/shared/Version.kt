package dorai.shared

/**
 * A product version, `X.Y.Z`. A client and a server work together when their majors match;
 * across majors the server refuses every API call. Rules: `docs/plan-releases.md`.
 *
 * Comparable, so a feature that needs a newer server within the same major can check
 * `serverVersion >= Version(1, 7, 0)`.
 */
data class Version(val major: Int, val minor: Int, val patch: Int) : Comparable<Version> {

    fun isCompatibleWith(other: Version): Boolean = major == other.major

    override fun compareTo(other: Version): Int =
        compareValuesBy(this, other, Version::major, Version::minor, Version::patch)

    override fun toString(): String = "$major.$minor.$patch"

    companion object {
        // Before CURRENT: companion properties initialize in order, and CURRENT parses with it.
        private val FORMAT = Regex("""(\d{1,9})\.(\d{1,9})\.(\d{1,9})""")

        /** The version this build of the server, app or CLI was made as. */
        val CURRENT: Version = requireNotNull(parse(DoraiBuild.VERSION)) { "Bad build version ${DoraiBuild.VERSION}" }

        /** Sent by every client on every API request. */
        const val CLIENT_HEADER = "Dorai-Client-Version"

        /** Sent by the server on every response. */
        const val SERVER_HEADER = "Dorai-Server-Version"

        /** The version in [text], or null when it is not plain `X.Y.Z`. */
        fun parse(text: String): Version? =
            FORMAT.matchEntire(text.trim())?.destructured?.let { (major, minor, patch) ->
                Version(major.toInt(), minor.toInt(), patch.toInt())
            }
    }
}

/** Problem types (RFC 9457 `type`) the server uses when client and server versions do not fit. */
object VersionProblem {
    /** The client's major is lower than the server's: the user must update the app. */
    const val CLIENT_OUTDATED = "urn:dorai:problem:client-outdated"

    /** The client's major is higher than the server's: the admin must update the server. */
    const val SERVER_OUTDATED = "urn:dorai:problem:server-outdated"

    /** The request did not say which version sent it, or not as `X.Y.Z`. */
    const val CLIENT_VERSION_MISSING = "urn:dorai:problem:client-version-missing"
}
