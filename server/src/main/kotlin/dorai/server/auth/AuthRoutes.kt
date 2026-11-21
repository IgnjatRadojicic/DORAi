package dorai.server.auth

import dorai.server.ApiProblem
import dorai.shared.model.CreateInviteRequest
import dorai.shared.model.CreateUserRequest
import dorai.shared.model.LoginRequest
import dorai.shared.model.PasswordChangeRequest
import dorai.shared.model.RedeemRequest
import dorai.shared.model.RefreshRequest
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlin.time.Duration.Companion.minutes

val AUTH_RATE_LIMIT = RateLimitName("dorai-auth")

/** Installs the per-address limit used by [authRoutes]: [AuthConfig.authCallsPerMinute] from one address. */
fun Application.installAuthRateLimit(auth: AuthService) = install(RateLimit) {
    register(AUTH_RATE_LIMIT) {
        rateLimiter(limit = auth.config.authCallsPerMinute, refillPeriod = 1.minutes)
        requestKey { call -> call.request.origin.remoteAddress }
    }
}

/** /auth (public) and /admin (admin role, enforced by [accessControl]). */
fun Route.authRoutes(auth: AuthService) {
    // The calls that take a guess (a code or a password) are limited per address; refresh and logout take
    // a 256-bit token, which can't be guessed.
    rateLimit(AUTH_RATE_LIMIT) {
        post("auth/redeem") { handle { call.respond(auth.redeem(call.receive<RedeemRequest>().code)) } }
        post("auth/login") { handle { val r = call.receive<LoginRequest>(); call.respond(auth.login(r.username, r.password)) } }
        post("auth/password") {
            handle {
                val r = call.receive<PasswordChangeRequest>()
                call.respond(auth.changePassword(r.username, r.currentPassword, r.newPassword))
            }
        }
    }
    post("auth/refresh") { handle { call.respond(auth.refresh(call.receive<RefreshRequest>().refreshToken)) } }
    post("auth/logout") { handle { auth.logout(call.receive<RefreshRequest>().refreshToken); call.respond(HttpStatusCode.NoContent) } }

    get("admin/users") { call.respond(auth.users()) }
    post("admin/users") {
        handle {
            val r = call.receive<CreateUserRequest>()
            call.respond(HttpStatusCode.Created, auth.createUser(r.username, r.role, r.temporaryPassword))
        }
    }
    post("admin/users/{username}/disable") { handle { call.respond(auth.disable(call.parameters["username"].orEmpty())) } }
    post("admin/users/{username}/revoke") {
        handle { auth.revokeSessions(call.parameters["username"].orEmpty()); call.respond(HttpStatusCode.NoContent) }
    }
    get("admin/invites") { call.respond(auth.invites()) }
    post("admin/invites") {
        handle { call.respond(HttpStatusCode.Created, auth.createInvite(call.receive<CreateInviteRequest>().username)) }
    }
}

/** Turns auth and input errors into problem responses with a message the app can show. */
private suspend fun RoutingContext.handle(block: suspend RoutingContext.() -> Unit) = try {
    block()
} catch (e: AuthException) {
    throw ApiProblem(HttpStatusCode.fromValue(e.status), HttpStatusCode.fromValue(e.status).description, e.message, e.retryAfterSeconds)
} catch (e: IllegalArgumentException) {
    throw ApiProblem(HttpStatusCode.BadRequest, "Invalid request", e.message)
}
