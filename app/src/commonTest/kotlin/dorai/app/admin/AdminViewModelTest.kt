package dorai.app.admin

import dorai.app.components.runImmediate
import dorai.shared.model.CreatedUser
import dorai.shared.model.Invite
import dorai.shared.model.Role
import dorai.shared.model.User
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class AdminViewModelTest {
    private val at = Instant.fromEpochSeconds(1_800_000_000)

    private inner class FakeAdmin : AdminSource {
        val users = mutableListOf(User("admin", Role.Admin, false, false, at), User("luka", Role.Reviewer, false, false, at))
        val calls = mutableListOf<String>()
        override suspend fun users() = users.toList()
        override suspend fun invites() = emptyList<Invite>()
        override suspend fun createUser(username: String, role: Role, temporaryPassword: String?): CreatedUser {
            calls += "create $username $role ${temporaryPassword ?: "generated"}"
            val user = User(username, role, true, false, at).also { users += it }
            return CreatedUser(user, temporaryPassword ?: "Gen-Pass-123", Invite("i1", username, at, link = "dorai://connect?server=x&code=c&fp=sha256:ab"))
        }
        override suspend fun disable(username: String) {
            calls += "disable $username"
            users.replaceAll { if (it.username == username) it.copy(disabled = true) else it }
        }
        override suspend fun enable(username: String) {
            calls += "enable $username"
            users.replaceAll { if (it.username == username) it.copy(disabled = false) else it }
        }
        override suspend fun revoke(username: String) { calls += "revoke $username" }
        override suspend fun invite(username: String): Invite {
            calls += "invite $username"
            return Invite("i2", username, at, link = "dorai://connect?server=x&code=d&fp=sha256:ab")
        }
    }

    private fun model(source: FakeAdmin) = AdminViewModel(source, me = "admin").also { runImmediate { it.refresh() } }

    @Test
    fun creatingAUserShowsThePasswordAndLinkOnce() {
        val source = FakeAdmin()
        val model = model(source)
        model.onUsername("Matija")
        model.onRole(Role.Viewer)
        runImmediate { model.create() }
        assertEquals(listOf("create matija Viewer generated"), source.calls)
        val handover = model.state.handover!!
        assertEquals("Gen-Pass-123", handover.temporaryPassword)
        assertTrue(handover.link!!.startsWith("dorai://connect"))
        assertEquals("", model.state.newUsername)
        model.closeHandover()
        assertNull(model.state.handover)
    }

    @Test
    fun aBadUsernameIsNotSent() {
        val source = FakeAdmin()
        val model = model(source)
        model.onUsername("a")
        assertFalse(model.state.canCreate)
        runImmediate { model.create() }
        assertTrue(source.calls.isEmpty())
        assertTrue(model.state.message!!.contains("3 to 64"))
    }

    @Test
    fun disablingAsksForASecondClick() {
        val source = FakeAdmin()
        val model = model(source)
        runImmediate { model.act(AdminAction.DISABLE, "luka") }
        assertTrue(source.calls.isEmpty())
        runImmediate { model.act(AdminAction.DISABLE, "luka") }
        assertEquals(listOf("disable luka"), source.calls)
        assertTrue(model.state.users.first { it.username == "luka" }.disabled)
        assertNull(model.state.confirm)
    }

    @Test
    fun aDisabledUserCanBeEnabledAgain() {
        val source = FakeAdmin()
        val model = model(source)
        runImmediate { model.act(AdminAction.DISABLE, "luka") }
        runImmediate { model.act(AdminAction.DISABLE, "luka") }
        runImmediate { model.enable("luka") }
        assertEquals(listOf("disable luka", "enable luka"), source.calls)
        assertFalse(model.state.users.first { it.username == "luka" }.disabled)
        // Enabling someone who is not disabled does nothing.
        runImmediate { model.enable("luka") }
        assertEquals(2, source.calls.size)
    }

    @Test
    fun theAdminCannotLockThemselvesOut() {
        val source = FakeAdmin()
        val model = model(source)
        repeat(2) { runImmediate { model.act(AdminAction.REVOKE, "admin") } }
        assertTrue(source.calls.isEmpty())
    }

    @Test
    fun aNewInviteShowsOnlyTheLink() {
        val source = FakeAdmin()
        val model = model(source)
        runImmediate { model.invite("luka") }
        assertNull(model.state.handover!!.temporaryPassword)
        assertEquals(listOf("invite luka"), source.calls)
    }
}
