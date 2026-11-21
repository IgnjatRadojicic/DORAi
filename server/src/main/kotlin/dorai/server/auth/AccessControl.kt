package dorai.server.auth

import dorai.server.ApiProblem
import dorai.shared.Dorai
import dorai.shared.model.Role
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.util.AttributeKey

/**
 * Who may call what: the role table of plan section 4 in one place. Null means public.
 * Every read is open to viewers; any write not listed needs an admin, so a new write route is closed
 * until someone adds it here.
 */
object Access {
    fun required(method: HttpMethod, path: String): Role? {
        val p = path.removePrefix(Dorai.API_PREFIX).trimEnd('/')
        return when {
            method == HttpMethod.Get && p == "/health" -> null
            method == HttpMethod.Post && p in PUBLIC_AUTH -> null
            p.startsWith("/admin/") || p == "/admin" -> Role.Admin
            method == HttpMethod.Post && (p == "/scan" || p == "/check" || p.startsWith("/suggestions/")) -> Role.Reviewer
            method == HttpMethod.Get && p.startsWith("/export/") -> Role.Reviewer
            method == HttpMethod.Get -> Role.Viewer
            else -> Role.Admin
        }
    }

    private val PUBLIC_AUTH = setOf("/auth/redeem", "/auth/login", "/auth/password", "/auth/refresh", "/auth/logout")
}

private val PrincipalKey = AttributeKey<Principal>("dorai.principal")

/** The verified caller, or null on a public route or a server without auth. */
val ApplicationCall.principal: Principal? get() = attributes.getOrNull(PrincipalKey)

/**
 * Checks every API call against [Access]: 401 without a valid access token or with a revoked session,
 * 403 when the role is too low.
 * Only API paths are checked; the health check and static files stay open.
 */
fun accessControl(auth: AuthService) = createApplicationPlugin("DoraiAccess") {
    onCall { call ->
        val path = call.request.path()
        if (!path.startsWith(Dorai.API_PREFIX)) return@onCall
        val required = Access.required(call.request.httpMethod, path) ?: return@onCall

        val token = call.request.headers["Authorization"]?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }?.substring(7)?.trim()
        val principal = token?.let { auth.authorize(it) }
            ?: throw ApiProblem(HttpStatusCode.Unauthorized, "Not logged in", "Log in to use this server.")
        if (principal.role < required) {
            throw ApiProblem(HttpStatusCode.Forbidden, "Not allowed", "This needs the ${required.name.lowercase()} role.")
        }
        call.attributes.put(PrincipalKey, principal)
    }
}
