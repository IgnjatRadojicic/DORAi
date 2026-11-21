package dorai.app.review

import dorai.app.components.EvidenceCitation
import dorai.app.components.FrontendFailure
import dorai.app.components.runImmediate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReviewViewModelTest {
    private fun suggestion(id: String, status: ReviewStatus = ReviewStatus.PENDING) = ReviewSuggestion(
        id = id, resourceName = "kv-$id", field = SuggestionField.SERVICE_TYPE, value = "S17", valueLabel = null,
        reason = "Key Vault is managed infrastructure.",
        citations = listOf(EvidenceCitation("CIR-2024-2956/AnnexIII/S17", "CIR (EU) 2024/2956", "Annex III", "S17", "ICT service type S17")),
        confidence = 0.9, status = status,
    )

    private inner class FakeSource(var stored: List<ReviewSuggestion>) : ReviewSource {
        var generated = 0
        var decisions = mutableListOf<Pair<String, Boolean>>()
        var alreadyDecided = false
        override suspend fun suggestions() = stored
        override suspend fun generate() {
            generated++
            stored = stored + suggestion("new")
        }
        override suspend fun decide(id: String, accept: Boolean): ReviewSuggestion {
            if (alreadyDecided) {
                stored = stored.map { if (it.id == id) it.copy(status = ReviewStatus.REJECTED) else it }
                throw FrontendFailure("Someone else decided this suggestion.")
            }
            decisions += id to accept
            val decided = stored.first { it.id == id }.copy(status = if (accept) ReviewStatus.ACCEPTED else ReviewStatus.REJECTED)
            stored = stored.map { if (it.id == id) decided else it }
            return decided
        }
    }

    @Test
    fun pendingAndDecidedAreKeptApart() {
        val model = ReviewViewModel(FakeSource(listOf(suggestion("a"), suggestion("b", ReviewStatus.ACCEPTED))))
        runImmediate { model.refresh() }

        assertEquals(listOf("a"), model.state.pending.map { it.id })
        assertEquals(listOf("b"), model.state.decided.map { it.id })
    }

    @Test
    fun acceptingAndRejectingGoToTheServerAndUpdateTheList() {
        val source = FakeSource(listOf(suggestion("a"), suggestion("b")))
        val model = ReviewViewModel(source)
        runImmediate { model.refresh() }
        runImmediate { model.accept("a") }
        runImmediate { model.reject("b") }

        assertEquals(listOf("a" to true, "b" to false), source.decisions)
        assertEquals(ReviewStatus.ACCEPTED, model.state.suggestions!!.first { it.id == "a" }.status)
        assertEquals(ReviewStatus.REJECTED, model.state.suggestions!!.first { it.id == "b" }.status)
        assertNull(model.state.deciding)
    }

    @Test
    fun aDecidedSuggestionIsNotSentAgain() {
        val source = FakeSource(listOf(suggestion("a", ReviewStatus.ACCEPTED)))
        val model = ReviewViewModel(source)
        runImmediate { model.refresh() }
        runImmediate { model.reject("a") }

        assertTrue(source.decisions.isEmpty())
    }

    @Test
    fun aConflictShowsTheServerStateAndTheMessage() {
        val source = FakeSource(listOf(suggestion("a"))).apply { alreadyDecided = true }
        val model = ReviewViewModel(source)
        runImmediate { model.refresh() }
        runImmediate { model.accept("a") }

        assertEquals(ReviewStatus.REJECTED, model.state.suggestions!!.single().status)
        assertEquals("Someone else decided this suggestion.", model.state.error)
        assertFalse(model.state.busy)
    }

    @Test
    fun generatingAsksTheAiThenReloads() {
        val source = FakeSource(emptyList())
        val model = ReviewViewModel(source)
        runImmediate { model.generate() }

        assertEquals(1, source.generated)
        assertEquals(listOf("new"), model.state.pending.map { it.id })
        assertFalse(model.state.generating)
    }
}
