package dorai.server

import dorai.shared.Dorai
import dorai.shared.Version
import dorai.shared.VersionProblem
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.request.path
import io.ktor.server.response.header

/**
 * Puts the server's version on every response and refuses every API call from a client with another
 * major, or one that does not say its version. `/api/health` stays open, so a client can always read
 * the server's version.
 */
val VersionCheck = createApplicationPlugin("DoraiVersionCheck") {
    onCall { call ->
        call.response.header(Version.SERVER_HEADER, Version.CURRENT.toString())
        val path = call.request.path()
        if (!path.startsWith(Dorai.API_PREFIX) || path.trimEnd('/') == "${Dorai.API_PREFIX}/health") return@onCall
        versionProblem(call.request.headers[Version.CLIENT_HEADER], Version.CURRENT)?.let { throw it }
    }
}

/** Why a client sending [clientHeader] may not talk to [server], or null when it may. */
internal fun versionProblem(clientHeader: String?, server: Version): ApiProblem? {
    val client = clientHeader?.let(Version::parse)
        ?: return ApiProblem(
            HttpStatusCode.BadRequest,
            "Client version missing",
            "Send the ${Version.CLIENT_HEADER} header with the client's version as X.Y.Z.",
            type = VersionProblem.CLIENT_VERSION_MISSING,
        )
    return when {
        client.major < server.major -> ApiProblem(
            HttpStatusCode.UpgradeRequired,
            "Update DORAi",
            "This app is version $client and the server runs $server. Install DORAi ${server.major} to use this server.",
            type = VersionProblem.CLIENT_OUTDATED,
        )
        client.major > server.major -> ApiProblem(
            HttpStatusCode.UpgradeRequired,
            "Server needs an update",
            "This app is version $client and the server runs $server. Ask your admin to update the server to " +
                "version ${client.major}, or use DORAi ${server.major}.",
            type = VersionProblem.SERVER_OUTDATED,
        )
        else -> null
    }
}
