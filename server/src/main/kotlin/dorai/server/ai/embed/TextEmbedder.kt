package dorai.server.ai.embed

/** Turns text into vectors. Query and corpus must be embedded by the same model. */
interface TextEmbedder {
    /** Identifies model and shape; stored with every chunk so a model change re-embeds the corpus. */
    val modelId: String

    val dimensions: Int

    /** One vector per text, in the same order. */
    suspend fun embed(texts: List<String>): List<FloatArray>

    companion object {
        /** The `regulation_chunks.embedding` column holds vectors of this size. */
        const val DIMENSIONS = 1536
    }
}
