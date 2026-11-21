package dorai.shared.model

import kotlinx.serialization.Serializable
import kotlin.time.Instant

/** Ordered: every role can do what the roles before it can. */
@Serializable
enum class Role { Viewer, Reviewer, Admin }

// Connecting and logging in

@Serializable
data class RedeemRequest(val code: String)

/** What an invite code is for; redeeming does not log in. */
@Serializable
data class RedeemResponse(val app: String, val username: String, val role: Role)

@Serializable
data class LoginRequest(val username: String, val password: String)

/** Either tokens, or (after a temporary password) the instruction to set a new password first. */
@Serializable
data class LoginResponse(val passwordChangeRequired: Boolean, val tokens: Tokens? = null)

/** Proves the current (or temporary) password; answers with tokens for the new one. */
@Serializable
data class PasswordChangeRequest(val username: String, val currentPassword: String, val newPassword: String)

@Serializable
data class RefreshRequest(val refreshToken: String)

/** The access token is a short-lived JWT; the refresh token is single use and replaced on every refresh. */
@Serializable
data class Tokens(
    val accessToken: String,
    val accessExpiresAt: Instant,
    val refreshToken: String,
    val refreshExpiresAt: Instant,
    val username: String,
    val role: Role,
)

// Administration

@Serializable
data class User(
    val username: String,
    val role: Role,
    val passwordChangeRequired: Boolean,
    val disabled: Boolean,
    val createdAt: Instant,
)

/** Without [temporaryPassword] the server generates one. */
@Serializable
data class CreateUserRequest(val username: String, val role: Role, val temporaryPassword: String? = null)

/** The temporary password and the invite code are shown this once; the server keeps only hashes. */
@Serializable
data class CreatedUser(val user: User, val temporaryPassword: String, val invite: Invite)

@Serializable
data class CreateInviteRequest(val username: String)

/**
 * [code] and [link] are present only in the answer that creates the invite.
 * The link is `dorai://connect?server=...&code=...&fp=sha256:...`.
 */
@Serializable
data class Invite(
    val id: String,
    val username: String,
    val expiresAt: Instant,
    val redeemedAt: Instant? = null,
    val code: String? = null,
    val link: String? = null,
)
