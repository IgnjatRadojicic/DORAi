package dorai.server.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class LoginThrottleTest {
    private var now = Instant.parse("2026-11-21T10:00:00Z")
    private val clock = object : Clock {
        override fun now() = now
    }
    private val throttle = LoginThrottle(maxFailures = 5, window = 15.minutes, lockout = 15.minutes, clock = clock)

    private fun fail(times: Int, step: Duration = 1.seconds) = repeat(times) {
        throttle.failed("alice")
        now += step
    }

    @Test
    fun fiveFailuresLockTheUsernameForFifteenMinutes() {
        fail(4)
        throttle.check("alice")
        fail(1)
        val locked = assertFailsWith<AuthException> { throttle.check("Alice ") }
        assertEquals(429, locked.status)
        assertEquals(15 * 60 - 1L, locked.retryAfterSeconds)
        assertEquals("Too many failed logins for this username. Try again in 15 minutes.", locked.message)
        throttle.check("bob")

        now += 15.minutes
        throttle.check("alice")
    }

    @Test
    fun failuresOutsideTheWindowDoNotCount() {
        fail(4, step = 4.minutes)
        fail(1)
        throttle.check("alice")
    }

    @Test
    fun clearingStartsTheCountAgain() {
        fail(4)
        throttle.clear("alice")
        fail(4)
        throttle.check("alice")
        fail(1)
        assertFailsWith<AuthException> { throttle.check("alice") }
        throttle.clear("ALICE")
        throttle.check("alice")
    }
}
