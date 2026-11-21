package dorai.server.ai.search

import dorai.server.ai.embed.FakeEmbedder
import dorai.server.ai.embed.TextEmbedder
import dorai.server.ai.ingest.RegulationChunk
import dorai.server.ai.ingest.RegulationChunker
import dorai.server.ai.ingest.RegulationCorpus
import dorai.server.ai.ingest.RegulationIngestor
import dorai.server.db.DatabaseConfig
import dorai.server.db.connectDatabase
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.testcontainers.DockerClientFactory
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Ingestion and search on a real PostgreSQL with pgvector in Docker; skipped without Docker. */
class RegulationDatabaseTest {
    private val embedder = FakeEmbedder()

    private class OtherModel(inner: TextEmbedder = FakeEmbedder()) : TextEmbedder by inner {
        override val modelId = "fake/other-model/${TextEmbedder.DIMENSIONS}"
    }

    private class WrongSize : TextEmbedder {
        override val modelId = "fake/wrong-size"
        override val dimensions = 3
        override suspend fun embed(texts: List<String>) = texts.map { FloatArray(3) { 1f } }
    }

    private fun chunk(id: String, text: String) =
        RegulationChunk(id, "Regulation (EU) 2022/2554 (DORA)", "Article 28", null, text, RegulationChunker.sha256(text), 1)

    private val register = chunk("DORA/Art-28/3", "Financial entities shall maintain a register of information on all contractual arrangements.")
    private val exit = chunk("DORA/Art-28/8", "Financial entities shall put in place exit strategies for ICT services supporting critical functions.")
    private val audit = chunk("DORA/Art-28/6", "In exercising access, inspection and audit rights over the ICT third-party service provider.")

    private fun rows() = transaction(database) { RegulationChunksTable.selectAll().count() }

    @BeforeTest
    fun emptyTable() {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable, "Docker is not available")
        transaction(database) { RegulationChunksTable.deleteAll() }
    }

    @Test
    fun ingestingTwiceStoresEveryChunkOnce() = runTest {
        val ingestor = RegulationIngestor(database, embedder)

        assertEquals(RegulationIngestor.Result(inserted = 3, removed = 0, total = 3), ingestor.ingest(listOf(register, exit, audit)))
        assertEquals(RegulationIngestor.Result(inserted = 0, removed = 0, total = 3), ingestor.ingest(listOf(register, exit, audit)))
        assertEquals(3, rows())
    }

    @Test
    fun changedOrDroppedChunksReplaceTheirOldRows() = runTest {
        val ingestor = RegulationIngestor(database, embedder)
        ingestor.ingest(listOf(register, exit, audit))

        val reworded = chunk(exit.citationId, exit.text + " Those strategies shall be tested.")
        val result = ingestor.ingest(listOf(register, reworded))

        assertEquals(RegulationIngestor.Result(inserted = 1, removed = 2, total = 2), result)
        assertEquals(2, rows())
    }

    @Test
    fun aNewEmbeddingModelReembedsTheCorpus() = runTest {
        RegulationIngestor(database, embedder).ingest(listOf(register, exit))

        val result = RegulationIngestor(database, OtherModel()).ingest(listOf(register, exit))

        assertEquals(RegulationIngestor.Result(inserted = 2, removed = 2, total = 2), result)
        assertEquals(emptyList(), PgRegulationSearch(database, embedder).search(register.text))
    }

    @Test
    fun aVectorOfTheWrongSizeIsRejectedBeforeStoring() = runTest {
        assertFailsWith<IllegalStateException> { RegulationIngestor(database, WrongSize()).ingest(listOf(register)) }
        assertEquals(0, rows())
    }

    @Test
    fun searchReturnsTheClosestChunkAsACitation() = runTest {
        RegulationIngestor(database, embedder).ingest(listOf(register, exit, audit))

        val best = PgRegulationSearch(database, embedder).search("exit strategies for ICT services supporting critical functions").first()

        assertEquals("DORA/Art-28/8", best.id)
        assertEquals("Article 28", best.article)
        assertEquals(exit.text, best.excerpt)
        assertTrue(best.score in 0.40..1.0001, "score ${best.score}")
    }

    @Test
    fun searchBelowTheThresholdFindsNothing() = runTest {
        RegulationIngestor(database, embedder).ingest(listOf(register, exit, audit))
        val search = PgRegulationSearch(database, embedder)

        assertEquals(emptyList(), search.search("zebra marathon weather forecast"))
        assertEquals(emptyList(), search.search("   "))
    }

    @Test
    fun searchKeepsResultsBestFirstAndWithinTheLimit() = runTest {
        RegulationIngestor(database, embedder).ingest(listOf(register, exit, audit))
        val search = PgRegulationSearch(database, embedder, minSimilarity = 0.0, maxResults = 2)

        val all = search.search("financial entities ICT", limit = 10)

        assertEquals(2, all.size)
        assertTrue(all[0].score >= all[1].score)
        assertEquals(1, search.search("financial entities ICT", limit = 1).size)
    }

    @Test
    fun theWholeCorpusIngestsAndAnswersAServiceTypeQuestion() = runTest {
        val chunks = RegulationCorpus.chunks()
        val result = RegulationIngestor(database, embedder).ingest(chunks)

        assertEquals(chunks.size, result.inserted)
        val best = PgRegulationSearch(database, embedder).search("Cloud services: PaaS Platform-as-a-Service").first()
        assertEquals("CIR-2024-2956/AnnexIII/S18", best.id)
    }

    companion object {
        private val database: Database by lazy {
            val image = DockerImageName.parse("pgvector/pgvector:0.8.7-pg16").asCompatibleSubstituteFor("postgres")
            val postgres = PostgreSQLContainer(image).apply { start() }
            connectDatabase(DatabaseConfig(postgres.jdbcUrl, postgres.username, postgres.password))
        }
    }
}
