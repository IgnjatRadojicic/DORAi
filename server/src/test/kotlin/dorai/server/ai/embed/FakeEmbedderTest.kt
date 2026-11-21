package dorai.server.ai.embed

import kotlinx.coroutines.test.runTest
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FakeEmbedderTest {
    private val embedder = FakeEmbedder()

    private fun cosine(a: FloatArray, b: FloatArray) = a.indices.sumOf { (a[it] * b[it]).toDouble() }

    @Test
    fun sameTextGivesSameNormalisedVector() = runTest {
        val (first, second) = embedder.embed(listOf("ICT third-party risk", "ICT third-party risk"))

        assertContentEquals(first, second)
        assertEquals(TextEmbedder.DIMENSIONS, first.size)
        assertEquals(1.0, sqrt(cosine(first, first)), 1e-6)
    }

    @Test
    fun textsSharingWordsAreCloserThanUnrelatedText() = runTest {
        val (query, related, unrelated) = embedder.embed(
            listOf("cloud platform as a service", "Cloud services: PaaS platform as a service", "audit of the management body"),
        )

        assertTrue(cosine(query, related) > cosine(query, unrelated))
    }

    @Test
    fun emptyTextGivesAZeroVector() = runTest {
        assertTrue(embedder.embed(listOf("")).single().all { it == 0f })
    }
}
