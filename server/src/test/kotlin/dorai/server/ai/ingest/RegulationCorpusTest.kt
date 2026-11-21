package dorai.server.ai.ingest

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Chunks the real bundled PDFs through PDFBox: offline, but exercises the actual layout. */
class RegulationCorpusTest {
    private val chunks = RegulationCorpus.chunks()
    private val ids = chunks.map { it.citationId }

    @Test
    fun everyDoraRecitalAndArticleIsCitable() {
        val recitals = ids.filter { it.startsWith("DORA/Recital/") }.map { it.removePrefix("DORA/Recital/").substringBefore('#') }.toSet()
        val articles = ids.filter { it.startsWith("DORA/Art-") }.map { it.removePrefix("DORA/Art-").substringBefore('/').substringBefore('#') }.toSet()

        assertEquals((1..106).map { it.toString() }.toSet(), recitals)
        assertEquals((1..64).map { it.toString() }.toSet(), articles)
        assertTrue("DORA/Art-28/4" in ids)
    }

    @Test
    fun everyServiceTypeOfAnnexIIIIsCitable() {
        val services = ids.filter { it.startsWith("CIR-2024-2956/AnnexIII/S") }.map { it.substringAfterLast('/') }.toSet()

        assertEquals((1..19).map { "S%02d".format(it) }.toSet(), services)
    }

    @Test
    fun registerTemplateColumnsAreCitable() {
        val columns = ids.filter { it.startsWith("CIR-2024-2956/AnnexI/B_") }

        assertTrue(columns.size >= 90, "only ${columns.size} template columns")
        assertTrue("CIR-2024-2956/AnnexI/B_02.02.0150" in ids)
    }

    @Test
    fun noFootnoteOrPageHeaderBecomesText() {
        // Footnotes cite other acts with an ELI link; the regulation text itself never does.
        assertFalse(chunks.any { "ELI: http" in it.text }, chunks.firstOrNull { "ELI: http" in it.text }?.citationId)
        assertFalse(chunks.any { "EN Official Journal" in it.text })
        assertEquals(ids.size, ids.toSet().size)
    }
}
