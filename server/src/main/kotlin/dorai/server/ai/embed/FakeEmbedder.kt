package dorai.server.ai.embed

import kotlin.math.sqrt

/**
 * Offline stand-in for an embedding model: each word is hashed to a position and the vector is
 * normalised. Same text gives the same vector and texts sharing words land close together, which
 * is enough for tests and local runs. It understands no language.
 */
class FakeEmbedder : TextEmbedder {
    override val modelId = "fake/hashed-words/${TextEmbedder.DIMENSIONS}"
    override val dimensions = TextEmbedder.DIMENSIONS

    override suspend fun embed(texts: List<String>): List<FloatArray> = texts.map(::vectorOf)

    private fun vectorOf(text: String): FloatArray {
        val vector = FloatArray(dimensions)
        WORD.findAll(text.lowercase()).forEach { word ->
            vector[Math.floorMod(word.value.hashCode(), dimensions)] += 1f
        }
        val length = sqrt(vector.sumOf { (it * it).toDouble() }).toFloat()
        if (length > 0f) for (i in vector.indices) vector[i] /= length
        return vector
    }

    private companion object {
        val WORD = Regex("""[\p{L}\p{N}_.]+""")
    }
}
