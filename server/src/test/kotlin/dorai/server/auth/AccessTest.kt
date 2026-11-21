package dorai.server.auth

import dorai.shared.model.Role
import io.ktor.http.HttpMethod
import io.ktor.http.HttpMethod.Companion.Get
import io.ktor.http.HttpMethod.Companion.Post
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The role table of plan section 4, row by row. */
class AccessTest {

    private fun role(method: HttpMethod, path: String) = Access.required(method, "/api$path")

    @Test
    fun publicRoutes() {
        assertNull(role(Get, "/health"))
        listOf("redeem", "login", "password", "refresh", "logout").forEach { assertNull(role(Post, "/auth/$it"), it) }
    }

    @Test
    fun viewerRoutes() = listOf("/resources", "/register", "/validation", "/concentration", "/suggestions").forEach {
        assertEquals(Role.Viewer, role(Get, it), it)
    }

    @Test
    fun reviewerRoutes() {
        listOf("/scan", "/check", "/suggestions/generate", "/suggestions/abc/accept", "/suggestions/abc/reject").forEach {
            assertEquals(Role.Reviewer, role(Post, it), it)
        }
        assertEquals(Role.Reviewer, role(Get, "/export/csv"))
        assertEquals(Role.Reviewer, role(Get, "/export/pdf"))
    }

    @Test
    fun adminRoutesAndUnknownWrites() {
        assertEquals(Role.Admin, role(Get, "/admin/users"))
        assertEquals(Role.Admin, role(Post, "/admin/users/bob/disable"))
        assertEquals(Role.Admin, role(Post, "/admin/invites"))
        // A write nobody added to the table is closed by default; reads are for viewers.
        assertEquals(Role.Admin, role(Post, "/something-new"))
        assertEquals(Role.Admin, role(HttpMethod.Delete, "/register"))
        assertEquals(Role.Viewer, role(Get, "/something-new"))
    }

    @Test
    fun rolesAreOrdered() {
        assertTrue(Role.Admin > Role.Reviewer && Role.Reviewer > Role.Viewer)
    }
}

class SecretsTest {

    @Test
    fun inviteCodesLookLikeThePlan() {
        val codes = List(200) { Secrets.inviteCode() }
        assertTrue(codes.all { Regex("^[A-Z2-9]{4}-[A-Z2-9]{4}$").matches(it) }, codes.first())
        assertTrue(codes.none { c -> c.any { it in "01ILOU" } })
        assertEquals(200, codes.toSet().size)
        assertEquals("K7Q29XPA", Secrets.normalizeCode(" k7q2-9xpa "))
    }

    @Test
    fun passwordsHashAndVerify() {
        val hash = Passwords.hash("correct horse battery")
        assertTrue(hash.startsWith("\$argon2id\$"))
        assertNotEquals(hash, Passwords.hash("correct horse battery"))
        assertTrue(Passwords.verify("correct horse battery", hash))
        assertFalse(Passwords.verify("wrong horse battery", hash))
        assertFalse(Passwords.verify("anything at all", null))
    }

    @Test
    fun passwordRules() {
        assertNull(Passwords.problem("long enough pass", "alice"))
        assertTrue(Passwords.problem("short", "alice")!!.contains("12"))
        assertTrue(Passwords.problem("Alice-the-user", "alice-the-user")!!.contains("username"))
        assertTrue(Passwords.problem("same old password", "alice", current = "same old password")!!.contains("differ"))
    }

    @Test
    fun usernames() {
        assertEquals("alice.k", Usernames.normalize(" Alice.K "))
        listOf("ab", "has space", "-starts", "x".repeat(65)).forEach {
            kotlin.test.assertFailsWith<IllegalArgumentException>(it) { Usernames.normalize(it) }
        }
    }
}
