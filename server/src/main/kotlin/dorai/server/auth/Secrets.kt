package dorai.server.auth

import com.password4j.Argon2Function
import com.password4j.Password
import com.password4j.types.Argon2
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/** Random secrets and their hashes. Only hashes are stored. */
object Secrets {
    private val random = SecureRandom()

    // Crockford-style alphabet without 0/O, 1/I/L, U: easy to read out and type.
    private const val CODE_ALPHABET = "ABCDEFGHJKMNPQRSTVWXYZ23456789"

    /** Refresh tokens: 256 random bits, URL-safe. */
    fun token(): String = ByteArray(32).also(random::nextBytes).let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }

    /** Invite codes like `K7Q2-9XPA`: 8 characters from a 30-letter alphabet (about 39 bits), single use, 72 hours. */
    fun inviteCode(): String = (1..8).map { CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)] }
        .joinToString("").let { it.take(4) + "-" + it.drop(4) }

    /** Temporary passwords: 16 characters from the code alphabet in lower and upper case plus digits. */
    fun temporaryPassword(): String {
        val alphabet = CODE_ALPHABET + CODE_ALPHABET.lowercase()
        return (1..16).map { alphabet[random.nextInt(alphabet.length)] }.joinToString("")
    }

    /** Codes are compared without dashes, spaces or case, so a code typed by hand still matches. */
    fun normalizeCode(code: String) = code.uppercase().filter { it.isLetterOrDigit() }

    fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
}

/** Argon2id with the OWASP minimum (19 MiB, 2 iterations, 1 lane). The parameters travel inside each hash. */
object Passwords {
    const val MIN_LENGTH = 12
    private val argon2 = Argon2Function.getInstance(19_456, 2, 1, 32, Argon2.ID, 19)

    /** Verified against for unknown users, so a wrong username takes as long as a wrong password. */
    private val dummyHash = hash(Secrets.temporaryPassword())

    fun hash(password: String): String = Password.hash(password).addRandomSalt(16).with(argon2).result

    fun verify(password: String, hash: String?): Boolean {
        val stored = hash ?: dummyHash
        val ok = Password.check(password, stored).with(Argon2Function.getInstanceFromHash(stored))
        return ok && hash != null
    }

    /** Null when the password is acceptable, otherwise the reason. */
    fun problem(password: String, username: String, current: String? = null): String? = when {
        password.length < MIN_LENGTH -> "The password needs at least $MIN_LENGTH characters."
        password.equals(username, ignoreCase = true) -> "The password cannot be the username."
        current != null && password == current -> "The new password must differ from the current one."
        else -> null
    }
}
