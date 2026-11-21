package dorai.server.ai.ingest

import dorai.server.ai.embed.TextEmbedder
import dorai.server.ai.search.RegulationChunksTable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.notInList
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/**
 * Keeps `regulation_chunks` in step with the corpus and the embedding model. A chunk is stored under
 * the hash of model id plus text, so running twice inserts nothing, and a new model or new chunking
 * replaces the old rows instead of mixing incompatible vectors. Rows the corpus no longer produces
 * are deleted so they can never be cited.
 */
class RegulationIngestor(private val database: Database, private val embedder: TextEmbedder) {
    data class Result(val inserted: Int, val removed: Int, val total: Int)

    private val running = Mutex()

    suspend fun ingest(chunks: List<RegulationChunk>): Result = running.withLock {
        val stored = chunks
            .map { it to RegulationChunker.sha256("${embedder.modelId}\n${it.contentHash}") }
            .distinctBy { it.second }
        val hashes = stored.map { it.second }
        val table = RegulationChunksTable

        val (removed, existing) = withContext(Dispatchers.IO) {
            transaction(database) {
                val removed = if (hashes.isEmpty()) {
                    table.deleteAll()
                } else {
                    table.deleteWhere { table.contentHash notInList hashes }
                }
                removed to table.select(table.contentHash).map { it[table.contentHash] }.toSet()
            }
        }

        var inserted = 0
        for (batch in stored.filter { it.second !in existing }.chunked(BATCH_SIZE)) {
            val vectors = embedder.embed(batch.map { it.first.text })
            check(vectors.size == batch.size) { "Embedding returned ${vectors.size} vectors for ${batch.size} chunks" }
            vectors.forEach { vector ->
                check(vector.size == TextEmbedder.DIMENSIONS) {
                    "Embedding has ${vector.size} dimensions, ${TextEmbedder.DIMENSIONS} required"
                }
                check(vector.all { it.isFinite() }) { "Embedding contains a value that is not finite" }
            }
            withContext(Dispatchers.IO) {
                transaction(database) {
                    table.batchInsert(batch.zip(vectors)) { (entry, vector) ->
                        val (chunk, hash) = entry
                        this[table.citationId] = chunk.citationId
                        this[table.regulation] = chunk.regulation
                        this[table.article] = chunk.article
                        this[table.paragraph] = chunk.paragraph
                        this[table.text] = chunk.text
                        this[table.page] = chunk.page
                        this[table.contentHash] = hash
                        this[table.embeddingModel] = embedder.modelId
                        this[table.embedding] = vector
                    }
                }
            }
            inserted += batch.size
        }
        Result(inserted = inserted, removed = removed, total = stored.size)
    }

    private companion object {
        /** Small batches keep each embedding request well under the provider's limits. */
        const val BATCH_SIZE = 32
    }
}
