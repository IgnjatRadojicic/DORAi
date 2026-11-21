package dorai.server.auth

import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ceil
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * Failed logins per username, in memory (one server per install, so a restart is the only reset besides an
 * admin revoke). After [maxFailures] wrong passwords within [window] the username is refused for [lockout],
 * the right password included, so guessing gives no signal. Unknown usernames are tracked the same way.
 */
class LoginThrottle(
    private val maxFailures: Int,
    private val window: Duration,
    private val lockout: Duration,
    private val clock: Clock,
) {
    private class State {
        val failures = ArrayDeque<Instant>()
        var lockedUntil: Instant? = null
    }

    private val states = ConcurrentHashMap<String, State>()

    /** Throws 429 with the time left while [username] is locked. */
    fun check(username: String) {
        val state = states[keyOf(username)] ?: return
        val until = synchronized(state) { state.lockedUntil } ?: return
        val left = until - clock.now()
        if (left <= Duration.ZERO) return
        val minutes = ceil(left.inWholeSeconds / 60.0).toInt().coerceAtLeast(1)
        throw AuthException(
            429,
            "Too many failed logins for this username. Try again in $minutes minute${if (minutes == 1) "" else "s"}.",
            retryAfterSeconds = left.inWholeSeconds.coerceAtLeast(1),
        )
    }

    fun failed(username: String) {
        val now = clock.now()
        if (states.size > MAX_TRACKED) prune(now)
        val state = states.computeIfAbsent(keyOf(username)) { State() }
        synchronized(state) {
            while (state.failures.isNotEmpty() && state.failures.first() <= now - window) state.failures.removeFirst()
            state.failures.addLast(now)
            if (state.failures.size >= maxFailures) {
                state.lockedUntil = now + lockout
                state.failures.clear()
            }
        }
    }

    /** A correct login, or an admin revoking the user's sessions, starts the count again. */
    fun clear(username: String) {
        states.remove(keyOf(username))
    }

    private fun prune(now: Instant) = states.entries.removeIf { (_, s) ->
        synchronized(s) { (s.lockedUntil ?: Instant.DISTANT_PAST) <= now && s.failures.all { it <= now - window } }
    }

    private fun keyOf(username: String) = username.trim().lowercase().take(64)

    private companion object {
        const val MAX_TRACKED = 10_000
    }
}
