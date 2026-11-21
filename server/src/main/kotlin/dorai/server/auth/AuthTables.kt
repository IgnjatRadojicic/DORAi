package dorai.server.auth

import dorai.shared.model.Role
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestamp

// Mirrors db/migration/V2__auth.sql.

object Users : Table("users") {
    val username = text("username")
    val role = enumerationByName<Role>("role", 16)
    val passwordHash = text("password_hash")
    val passwordChangeRequired = bool("password_change_required")
    val disabled = bool("disabled")
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(username)
}

object RefreshTokens : Table("refresh_tokens") {
    val tokenHash = text("token_hash")
    val family = text("family")
    val username = text("username")
    val createdAt = timestamp("created_at")
    val expiresAt = timestamp("expires_at")
    val usedAt = timestamp("used_at").nullable()
    val revokedAt = timestamp("revoked_at").nullable()
    override val primaryKey = PrimaryKey(tokenHash)
}

object Invites : Table("invites") {
    val id = text("id")
    val codeHash = text("code_hash")
    val username = text("username")
    val createdAt = timestamp("created_at")
    val expiresAt = timestamp("expires_at")
    val redeemedAt = timestamp("redeemed_at").nullable()
    override val primaryKey = PrimaryKey(id)
}
