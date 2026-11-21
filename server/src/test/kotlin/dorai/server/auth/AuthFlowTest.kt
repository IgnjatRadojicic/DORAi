package dorai.server.auth

import dorai.server.db.TestPostgres
import dorai.server.db.postgresServices
import dorai.server.fixtures.FixtureData
import dorai.server.module
import dorai.shared.api.ApiResult
import dorai.shared.api.DoraiApi
import dorai.shared.api.MemoryTokenStore
import dorai.shared.model.CreateUserRequest
import dorai.shared.model.Role
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/** The whole auth story of plan section 5 against PostgreSQL, through the real shared client. */
class AuthFlowTest {

    private val data = FixtureData.load()
    private val config = AuthConfig(jwtSecret = "x".repeat(40), publicUrl = "https://dorai.firm.local:8443")

    private fun <T> ApiResult<T>.ok(): T = when (this) {
        is ApiResult.Success -> value
        is ApiResult.Failure -> fail("Expected success, got $problem")
    }

    private fun ApiResult<*>.status() = (this as? ApiResult.Failure)?.problem?.status ?: 200

    private fun flow(block: suspend ApplicationTestBuilder.(AuthService) -> Unit) {
        val db = TestPostgres.fresh()
        val auth = AuthService(db, config, fingerprint = "AB12CD")
        testApplication {
            application { module(postgresServices(db, data).copy(auth = auth)) }
            block(auth)
        }
    }

    private fun ApplicationTestBuilder.api(store: MemoryTokenStore = MemoryTokenStore()) = DoraiApi("http://localhost", client, store)

    /** Bootstraps the admin and logs in with a new password. */
    private suspend fun ApplicationTestBuilder.admin(auth: AuthService): DoraiApi {
        val created = assertNotNull(auth.bootstrap())
        val api = api()
        api.changePassword("admin", created.temporaryPassword, "admin password 2026").ok()
        return api
    }

    @Test
    fun firstStartToFirstReview() = flow { auth ->
        val anonymous = api()
        assertEquals("ok", anonymous.health().ok().status)
        assertEquals(401, anonymous.register().status())

        // The first start creates the admin once, with an invite link that pins the certificate.
        val created = assertNotNull(auth.bootstrap())
        assertNull(auth.bootstrap())
        assertEquals(Role.Admin to true, created.user.role to created.user.passwordChangeRequired)
        val link = created.invite.link!!
        assertTrue(link.startsWith("dorai://connect?server=https%3A%2F%2Fdorai.firm.local%3A8443&code="), link)
        assertTrue(link.endsWith("&fp=sha256:AB12CD"), link)

        // Redeeming says who the code is for, once.
        assertEquals("admin" to Role.Admin, anonymous.redeem(created.invite.code!!.lowercase()).ok().let { it.username to it.role })
        assertEquals(404, anonymous.redeem(created.invite.code!!).status())

        // The temporary password only allows setting a new one.
        val admin = api()
        val first = admin.login("Admin", created.temporaryPassword).ok()
        assertTrue(first.passwordChangeRequired)
        assertNull(first.tokens)
        assertEquals(400, admin.changePassword("admin", created.temporaryPassword, "short").status())
        assertEquals(Role.Admin, admin.changePassword("admin", created.temporaryPassword, "admin password 2026").ok().role)

        // The admin invites a reviewer, who connects and sets a password.
        val bob = admin.createUser(CreateUserRequest("bob", Role.Reviewer)).ok()
        assertEquals(listOf("admin", "bob"), admin.users().ok().map { it.username })
        val reviewer = api()
        assertEquals("bob", reviewer.redeem(bob.invite.code!!).ok().username)
        assertTrue(reviewer.login("bob", bob.temporaryPassword).ok().passwordChangeRequired)
        reviewer.changePassword("bob", bob.temporaryPassword, "bob password 2026").ok()

        assertEquals(data.register, reviewer.register().ok())
        assertEquals(4, reviewer.check().ok().size)
        assertEquals(403, reviewer.users().status())
    }

    @Test
    fun rolesAreCheckedOnTheServer() = flow { auth ->
        val admin = admin(auth)
        val viewer = admin.createUser(CreateUserRequest("vera", Role.Viewer, temporaryPassword = "temporary pass 1")).ok()
        assertEquals(409, admin.createUser(CreateUserRequest("VERA", Role.Viewer)).status())
        val api = api()
        api.changePassword("vera", viewer.temporaryPassword, "vera password 2026").ok()

        assertTrue(api.validation().ok().isValid)
        assertEquals(403, api.scan().status())
        assertEquals(403, api.check().status())
        assertEquals(403, api.acceptSuggestion("x").status())
        assertEquals(403, api.createInvite("vera").status())
    }

    @Test
    fun anExpiredAccessTokenIsRefreshedOnce() = flow { auth ->
        val store = MemoryTokenStore()
        val admin = admin(auth)
        val user = admin.createUser(CreateUserRequest("rita", Role.Reviewer, temporaryPassword = "temporary pass 1")).ok()
        val api = api(store)
        api.changePassword("rita", user.temporaryPassword, "rita password 2026").ok()
        val before = store.load()!!

        // The server rejects the access token; the client refreshes and repeats the call.
        store.save(before.copy(accessToken = "expired.or.forged"))
        assertTrue(api.validation().ok().isValid)
        val after = store.load()!!
        assertFalse(after.refreshToken == before.refreshToken)

        // The spent refresh token is useless now, and using it revokes the session it came from.
        assertEquals(401, assertIs<AuthException>(runCatching { auth.refresh(before.refreshToken) }.exceptionOrNull()).status)
        assertEquals(401, assertIs<AuthException>(runCatching { auth.refresh(after.refreshToken) }.exceptionOrNull()).status)

        // With no way to refresh, the client forgets the tokens and the caller sees 401.
        store.save(store.load()!!.copy(accessToken = "expired.or.forged"))
        assertEquals(401, api.validation().status())
        assertNull(store.load())
    }

    @Test
    fun disablingAndLoggingOutEndSessions() = flow { auth ->
        val admin = admin(auth)
        val created = admin.createUser(CreateUserRequest("dan", Role.Viewer, temporaryPassword = "temporary pass 1")).ok()
        val store = MemoryTokenStore()
        val dan = api(store)
        dan.changePassword("dan", created.temporaryPassword, "dan password 2026").ok()
        val refresh = store.load()!!.refreshToken

        assertTrue(dan.validation().ok().isValid)
        assertTrue(admin.disableUser("dan").ok().disabled)
        // The access token dan already holds stops working at once, not after 15 minutes.
        assertEquals(401, dan.validation().status())
        assertNull(store.load())
        assertEquals(401, dan.login("dan", "dan password 2026").status())
        assertEquals(401, runCatching { auth.refresh(refresh) }.exceptionOrNull().let { (it as AuthException).status })

        val adminStore = MemoryTokenStore()
        val again = api(adminStore)
        again.login("admin", "admin password 2026").ok()
        val adminTokens = adminStore.load()!!
        again.logout().ok()
        assertNull(adminStore.load())
        assertEquals(401, runCatching { auth.refresh(adminTokens.refreshToken) }.exceptionOrNull().let { (it as AuthException).status })
        // A copy of the access token from the ended session is useless too; the admin's other session is not.
        assertNull(auth.authorize(adminTokens.accessToken))
        assertTrue(admin.users().ok().isNotEmpty())
    }

    @Test
    fun anAdminCanRevokeEverySessionOfAUser() = flow { auth ->
        val admin = admin(auth)
        val created = admin.createUser(CreateUserRequest("eve", Role.Reviewer, temporaryPassword = "temporary pass 1")).ok()
        val laptop = MemoryTokenStore()
        api(laptop).changePassword("eve", created.temporaryPassword, "eve password 2026").ok()
        val phone = MemoryTokenStore()
        api(phone).login("eve", "eve password 2026").ok()
        val tokens = listOf(laptop.load()!!, phone.load()!!)
        tokens.forEach { assertNotNull(auth.authorize(it.accessToken)) }

        admin.revokeSessions("eve").ok()
        tokens.forEach { assertNull(auth.authorize(it.accessToken)) }
        assertEquals(401, api(laptop).validation().status())
        assertEquals(404, admin.revokeSessions("nobody").status())

        // Unlike disabling, eve can simply log in again.
        val again = MemoryTokenStore()
        assertFalse(api(again).login("eve", "eve password 2026").ok().passwordChangeRequired)
        assertTrue(api(again).validation().ok().isValid)
    }

    @Test
    fun tokensWithoutASessionOrSignedElsewhereAreRejected() = flow { auth ->
        admin(auth)
        val forged = com.auth0.jwt.JWT.create().withIssuer("dorai").withSubject("admin").withClaim("role", "Admin")
            .withExpiresAt(java.time.Instant.now().plusSeconds(600))
        assertNull(auth.authorize(forged.sign(com.auth0.jwt.algorithms.Algorithm.HMAC256(config.jwtSecret))))
        assertNull(auth.authorize(forged.withClaim("sid", "x").sign(com.auth0.jwt.algorithms.Algorithm.HMAC256("y".repeat(40)))))
        assertNull(auth.authorize("not a token"))
    }

    @Test
    fun fiveWrongPasswordsLockTheUsername() = flow { auth ->
        val admin = admin(auth)
        val created = admin.createUser(CreateUserRequest("lee", Role.Viewer, temporaryPassword = "temporary pass 1")).ok()
        api().changePassword("lee", created.temporaryPassword, "lee password 2026").ok()
        val lee = api()
        repeat(5) { assertEquals(401, lee.login("lee", "a wrong guess $it").status()) }

        // Locked: even the right password is refused, with the time to wait.
        val response = client.post("/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"lee","password":"lee password 2026"}""")
        }
        assertEquals(HttpStatusCode.TooManyRequests, response.status)
        assertTrue(response.headers[HttpHeaders.RetryAfter]!!.toLong() in 1..900)
        assertTrue("Try again in 15 minutes" in response.bodyAsText())

        // Revoking the user's sessions lifts the lockout.
        admin.revokeSessions("lee").ok()
        assertFalse(lee.login("lee", "lee password 2026").ok().passwordChangeRequired)
    }

    @Test
    fun guessingCallsAreLimitedPerAddress() = flow { _ ->
        val api = api()
        repeat(10) { assertEquals(404, api.redeem("AAAA-AAA$it").status()) }
        val response = client.post("/api/auth/redeem") {
            contentType(ContentType.Application.Json)
            setBody("""{"code":"AAAA-AAAA"}""")
        }
        assertEquals(HttpStatusCode.TooManyRequests, response.status)
        assertTrue(response.headers[HttpHeaders.RetryAfter]!!.toLong() in 1..60)
        assertTrue(response.contentType()!!.match(ContentType("application", "problem+json")))
        // Health and the session calls are not limited.
        assertEquals("ok", api.health().ok().status)
        assertEquals(401, api.register().status())
    }

    @Test
    fun wrongPasswordsAndUnknownUsersLookTheSame() = flow { auth ->
        admin(auth)
        val api = api()
        val wrong = (api.login("admin", "not the password") as ApiResult.Failure).problem
        val unknown = (api.login("nobody", "not the password") as ApiResult.Failure).problem
        assertEquals(401, wrong.status)
        assertEquals(wrong.detail, unknown.detail)
    }
}
