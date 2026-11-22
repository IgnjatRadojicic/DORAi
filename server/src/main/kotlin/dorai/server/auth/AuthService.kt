package dorai.server.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import dorai.server.db.tx
import dorai.shared.Dorai
import dorai.shared.model.CreatedUser
import dorai.shared.model.Invite
import dorai.shared.model.LoginResponse
import dorai.shared.model.RedeemResponse
import dorai.shared.model.Role
import dorai.shared.model.Tokens
import dorai.shared.model.User
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.slf4j.LoggerFactory
import java.net.URLEncoder
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.toJavaInstant

/** Who made a request: read from a verified access token. */
data class Principal(val username: String, val role: Role)

/** An auth failure the routes turn into a problem response. Messages are safe to show to the user. */
class AuthException(
    val status: Int,
    override val message: String,
    val retryAfterSeconds: Long? = null,
) : RuntimeException(message)

/**
 * Users, logins, tokens and invites (plan section 5). Passwords are Argon2id hashes; refresh tokens and
 * invite codes are stored as SHA-256 hashes; access tokens are HMAC-signed JWTs that are never stored.
 */
class AuthService(
    private val db: Database,
    val config: AuthConfig,
    /** SHA-256 of the server certificate for invite links; null when the server has no TLS. */
    private val fingerprint: String?,
    private val clock: Clock = Clock.System,
) {
    private val algorithm = Algorithm.HMAC256(config.jwtSecret)
    private val verifier = JWT.require(algorithm).withIssuer(ISSUER).acceptLeeway(5).build()
    private val throttle = LoginThrottle(config.maxFailedLogins, config.failedLoginWindow, config.lockout, clock)

    // Access tokens

    /**
     * The caller of a request, or null when the token is forged or expired, its session was revoked or
     * the user is disabled. The role is read from the database, so it is always current.
     */
    suspend fun authorize(accessToken: String): Principal? {
        val (username, session) = decode(accessToken) ?: return null
        return db.tx {
            val user = user(username)?.takeUnless { it[Users.disabled] } ?: return@tx null
            val live = RefreshTokens.selectAll()
                .where { (RefreshTokens.family eq session) and (RefreshTokens.username eq username) and RefreshTokens.revokedAt.isNull() }
                .limit(1).any()
            if (live) Principal(username, user[Users.role]) else null
        }
    }

    /** Username and session id of a correctly signed, unexpired token. */
    private fun decode(accessToken: String): Pair<String, String>? = try {
        val jwt = verifier.verify(accessToken)
        val session = jwt.getClaim(SESSION_CLAIM).asString()
        if (jwt.subject == null || session == null) null else jwt.subject to session
    } catch (_: JWTVerificationException) {
        null
    }

    // Connecting and logging in

    suspend fun redeem(code: String): RedeemResponse = db.tx {
        val now = clock.now()
        val invite = Invites.selectAll().where { Invites.codeHash eq Secrets.sha256(Secrets.normalizeCode(code)) }.forUpdate().singleOrNull()
        if (invite == null || invite[Invites.redeemedAt] != null || invite[Invites.expiresAt] <= now) {
            throw AuthException(404, "This invite code is not valid. Ask your administrator for a new link.")
        }
        // A disabled user gets the same answer as a wrong code: the link must not say the account exists.
        val user = user(invite[Invites.username])?.takeUnless { it[Users.disabled] }
            ?: throw AuthException(404, "This invite code is not valid. Ask your administrator for a new link.")
        Invites.update({ Invites.id eq invite[Invites.id] }) { it[redeemedAt] = now }
        RedeemResponse(Dorai.NAME, user[Users.username], user[Users.role])
    }

    suspend fun login(username: String, password: String): LoginResponse = throttled(username) {
        db.tx {
            val user = checkPassword(username, password)
            if (user[Users.passwordChangeRequired]) LoginResponse(passwordChangeRequired = true)
            else LoginResponse(passwordChangeRequired = false, tokens = startSession(user))
        }
    }

    /** Sets a new password and ends every other session of the user. */
    suspend fun changePassword(username: String, current: String, new: String): Tokens = throttled(username) {
        db.tx {
            val user = checkPassword(username, current)
            Passwords.problem(new, user[Users.username], current)?.let { throw AuthException(400, it) }
            Users.update({ Users.username eq user[Users.username] }) {
                it[passwordHash] = Passwords.hash(new)
                it[passwordChangeRequired] = false
            }
            revokeAll(user[Users.username])
            startSession(user(user[Users.username])!!)
        }
    }

    /** Refuses a locked username, counts a wrong password, and clears the count on success. */
    private suspend fun <T> throttled(username: String, block: suspend () -> T): T {
        throttle.check(username)
        return try {
            block().also { throttle.clear(username) }
        } catch (e: AuthException) {
            if (e.status == 401) throttle.failed(username)
            throw e
        }
    }

    /**
     * Spends a refresh token and returns the next. A token used a second time means a copy exists, so the
     * whole family is revoked and the user must log in again.
     */
    suspend fun refresh(refreshToken: String): Tokens {
        // The revocation on reuse must be committed, so the failure is returned from the transaction and
        // thrown after it; throwing inside would roll the revocation back.
        val outcome: Any = db.tx {
            val now = clock.now()
            val row = RefreshTokens.selectAll().where { RefreshTokens.tokenHash eq Secrets.sha256(refreshToken) }.forUpdate().singleOrNull()
                ?: return@tx sessionEnded()
            val family = row[RefreshTokens.family]
            when {
                row[RefreshTokens.revokedAt] != null -> return@tx sessionEnded()
                row[RefreshTokens.usedAt] != null -> {
                    RefreshTokens.update({ (RefreshTokens.family eq family) and RefreshTokens.revokedAt.isNull() }) { it[revokedAt] = now }
                    log.warn("Refresh token reused for {}: session revoked.", row[RefreshTokens.username])
                    return@tx sessionEnded()
                }
                row[RefreshTokens.expiresAt] <= now -> return@tx sessionEnded()
            }
            val user = user(row[RefreshTokens.username])
            if (user == null || user[Users.disabled]) return@tx sessionEnded()
            RefreshTokens.update({ RefreshTokens.tokenHash eq row[RefreshTokens.tokenHash] }) { it[usedAt] = now }
            issue(user, family)
        }
        return outcome as? Tokens ?: throw outcome as AuthException
    }

    private fun sessionEnded() = AuthException(401, SESSION_ENDED)

    /** Ends the session the refresh token belongs to. Unknown tokens are ignored. */
    suspend fun logout(refreshToken: String): Unit = db.tx {
        val row = RefreshTokens.selectAll().where { RefreshTokens.tokenHash eq Secrets.sha256(refreshToken) }.singleOrNull() ?: return@tx
        RefreshTokens.update({ (RefreshTokens.family eq row[RefreshTokens.family]) and RefreshTokens.revokedAt.isNull() }) {
            it[revokedAt] = clock.now()
        }
    }

    // Administration

    suspend fun users(): List<User> = db.tx { Users.selectAll().orderBy(Users.createdAt).map { it.toUser() } }

    suspend fun createUser(username: String, role: Role, temporaryPassword: String?): CreatedUser = db.tx { create(username, role, temporaryPassword) }

    /** Blocks the user and ends every session at once, including access tokens already issued. */
    suspend fun disable(username: String): User = db.tx {
        val name = Usernames.normalize(username)
        if (Users.update({ Users.username eq name }) { it[disabled] = true } == 0) throw AuthException(404, "No user '$name'.")
        revokeAll(name)
        user(name)!!.toUser()
    }

    /** Lets a disabled user sign in again with their password; sessions ended by the disable stay ended. */
    suspend fun enable(username: String): User = db.tx {
        val name = Usernames.normalize(username)
        if (Users.update({ Users.username eq name }) { it[disabled] = false } == 0) throw AuthException(404, "No user '$name'.")
        throttle.clear(name)
        user(name)!!.toUser()
    }

    /** Signs the user out everywhere at once and lifts a login lockout; they can log in again. */
    suspend fun revokeSessions(username: String): Unit = db.tx {
        val name = Usernames.normalize(username)
        if (user(name) == null) throw AuthException(404, "No user '$name'.")
        revokeAll(name)
        throttle.clear(name)
    }

    suspend fun invites(): List<Invite> = db.tx { Invites.selectAll().orderBy(Invites.createdAt).map { it.toInvite() } }

    /** A new invite for an existing user; earlier unused invites stay valid until they expire. */
    suspend fun createInvite(username: String): Invite = db.tx {
        val name = Usernames.normalize(username)
        val user = user(name) ?: throw AuthException(404, "No user '$name'.")
        if (user[Users.disabled]) throw AuthException(409, "'$name' is disabled. Enable the user before sending an invite.")
        invite(name)
    }

    /**
     * First start of an empty install: creates the admin with a temporary password and an invite, and logs
     * both once. Returns null when users already exist.
     */
    fun bootstrap(): CreatedUser? = transaction(db) {
        if (Users.selectAll().limit(1).any()) return@transaction null
        create(config.adminUsername, Role.Admin, null)
    }?.also {
        log.warn(
            "First start: created admin '{}'. Temporary password (shown once): {}  Invite link: {}  " +
                "Log in and set a new password; this message is not repeated.",
            it.user.username, it.temporaryPassword, it.invite.link,
        )
    }

    // Internals, inside a transaction

    private fun JdbcTransaction.create(username: String, role: Role, temporaryPassword: String?): CreatedUser {
        val name = Usernames.normalize(username)
        if (user(name) != null) throw AuthException(409, "User '$name' already exists.")
        val password = temporaryPassword ?: Secrets.temporaryPassword()
        Passwords.problem(password, name)?.let { throw AuthException(400, it) }
        Users.insert {
            it[Users.username] = name
            it[Users.role] = role
            it[passwordHash] = Passwords.hash(password)
            it[passwordChangeRequired] = true
            it[disabled] = false
            it[createdAt] = clock.now()
        }
        return CreatedUser(user(name)!!.toUser(), password, invite(name))
    }

    private fun JdbcTransaction.invite(username: String): Invite {
        val code = Secrets.inviteCode()
        val id = UUID.randomUUID().toString()
        val now = clock.now()
        Invites.insert {
            it[Invites.id] = id
            it[codeHash] = Secrets.sha256(Secrets.normalizeCode(code))
            it[Invites.username] = username
            it[createdAt] = now
            it[expiresAt] = now + config.inviteTtl
        }
        return Invite(id, username, now + config.inviteTtl, code = code, link = link(code))
    }

    /**
     * `dorai://connect?server=...&code=...&fp=sha256:...` (plan section 5). Behind a proxy the apps never
     * see this server's certificate, so the link carries no pin and they check the proxy's public one.
     */
    private fun link(code: String) = buildString {
        append("dorai://connect?server=").append(URLEncoder.encode(config.publicUrl, Charsets.UTF_8))
        append("&code=").append(code)
        if (config.proxy == Proxy.NONE) fingerprint?.let { append("&fp=sha256:").append(it) }
    }

    private fun JdbcTransaction.checkPassword(username: String, password: String): ResultRow {
        val user = runCatching { Usernames.normalize(username) }.getOrNull()?.let { user(it) }
        // Always verify, so an unknown username takes as long as a wrong password.
        val ok = Passwords.verify(password, user?.get(Users.passwordHash))
        if (user == null || !ok || user[Users.disabled]) throw AuthException(401, "Wrong username or password.")
        return user
    }

    private fun JdbcTransaction.startSession(user: ResultRow) = issue(user, UUID.randomUUID().toString())

    private fun JdbcTransaction.issue(user: ResultRow, family: String): Tokens {
        val now = clock.now()
        val refresh = Secrets.token()
        RefreshTokens.insert {
            it[tokenHash] = Secrets.sha256(refresh)
            it[RefreshTokens.family] = family
            it[username] = user[Users.username]
            it[createdAt] = now
            it[expiresAt] = now + config.refreshTtl
        }
        val accessExpires = now + config.accessTtl
        val access = JWT.create()
            .withIssuer(ISSUER)
            .withSubject(user[Users.username])
            .withClaim("role", user[Users.role].name)
            .withClaim(SESSION_CLAIM, family)
            .withIssuedAt(now.toJavaInstant())
            .withExpiresAt(accessExpires.toJavaInstant())
            .sign(algorithm)
        return Tokens(access, accessExpires, refresh, now + config.refreshTtl, user[Users.username], user[Users.role])
    }

    private fun JdbcTransaction.revokeAll(username: String) {
        RefreshTokens.update({ (RefreshTokens.username eq username) and RefreshTokens.revokedAt.isNull() }) { it[revokedAt] = clock.now() }
    }

    private fun user(username: String): ResultRow? = Users.selectAll().where { Users.username eq username }.singleOrNull()

    private fun ResultRow.toUser() =
        User(this[Users.username], this[Users.role], this[Users.passwordChangeRequired], this[Users.disabled], this[Users.createdAt])

    private fun ResultRow.toInvite() =
        Invite(this[Invites.id], this[Invites.username], this[Invites.expiresAt], this[Invites.redeemedAt])

    private companion object {
        const val ISSUER = "dorai"
        const val SESSION_CLAIM = "sid"
        const val SESSION_ENDED = "Your session has ended. Log in again."
        val log = LoggerFactory.getLogger(AuthService::class.java)
    }
}
