package dorai.app.components

import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

/** Fakes return immediately: no dispatcher, network or virtual clock is needed. */
fun runImmediate(block: suspend () -> Unit) {
    var outcome: Result<Unit>? = null
    block.startCoroutine(object : Continuation<Unit> {
        override val context = EmptyCoroutineContext
        override fun resumeWith(result: Result<Unit>) { outcome = result }
    })
    checkNotNull(outcome) { "Test unexpectedly suspended; use a controlled continuation for this case." }.getOrThrow()
}
