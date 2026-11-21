package dorai.server.ai.search

import dorai.server.ai.embed.TextEmbedder
import dorai.server.core.RegulationSearch
import dorai.shared.model.Citation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/**
 * Regulation search on pgvector: embeds the question with the corpus model and returns the closest
 * chunks with cosine similarity of at least [minSimilarity], best first. Below the threshold the
 * list is empty and the AI services answer "insufficient evidence". Embedding or database errors
 * are thrown; the services turn them into a failed answer.
 */
class PgRegulationSearch(
    private val database: Database,
    private val embedder: TextEmbedder,
    private val minSimilarity: Double = DEFAULT_MIN_SIMILARITY,
    private val maxResults: Int = 5,
) : RegulationSearch {
    override suspend fun search(query: String, limit: Int): List<Citation> {
        if (query.isBlank()) return emptyList()
        val vector = embedder.embed(listOf(query)).single()
        val table = RegulationChunksTable
        return withContext(Dispatchers.IO) {
            transaction(database) {
                val distance = CosineDistance(table.embedding, vector)
                table.select(table.citationId, table.regulation, table.article, table.paragraph, table.text, distance)
                    .where { (table.embeddingModel eq embedder.modelId) and (distance lessEq 1.0 - minSimilarity) }
                    .orderBy(distance, SortOrder.ASC)
                    .limit(limit.coerceIn(1, maxResults))
                    .map {
                        Citation(
                            id = it[table.citationId],
                            regulation = it[table.regulation],
                            article = it[table.article],
                            paragraph = it[table.paragraph],
                            excerpt = it[table.text],
                            score = 1.0 - it[distance],
                        )
                    }
            }
        }
    }

    companion object {
        /** Measured for `text-embedding-3-small`: relevant chunks score about 0.46 to 0.51. */
        const val DEFAULT_MIN_SIMILARITY = 0.40
    }
}
