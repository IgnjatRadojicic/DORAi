package dorai.server.ai.ingest

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RegulationChunkerTest {
    private val dora = RegulationSource("DORA", "Regulation (EU) 2022/2554 (DORA)")
    private val cir = RegulationSource("CIR-2024-2956", "CIR (EU) 2024/2956")

    private fun chunk(source: RegulationSource, vararg pages: List<String>) =
        RegulationChunker.chunk(source, pages.mapIndexed { i, lines -> PageText(i + 1, lines) })

    @Test
    fun recitalsBecomeChunksOfTheirOwn() {
        val chunks = chunk(
            dora,
            listOf(
                "(1) In the digital age, information and communication technology supports complex systems.",
                "(2) Financial entities rely more and more on ICT third-party service providers",
                "for the services they deliver to their clients.",
            ),
        )

        assertEquals(listOf("DORA/Recital/1", "DORA/Recital/2"), chunks.map { it.citationId })
        assertEquals("Preamble", chunks[1].article)
        assertTrue(chunks[1].text.endsWith("providers for the services they deliver to their clients."))
    }

    @Test
    fun articleParagraphsCarryArticleTitleAndParagraph() {
        val chunks = chunk(
            dora,
            listOf(
                "CHAPTER V",
                "MANAGING OF ICT THIRD-PARTY RISK",
                "Article 28",
                "General principles",
                "1. Financial entities shall manage ICT third-party risk as an integral component of ICT risk.",
                "2. Financial entities shall keep a register of information on all contractual arrangements",
                "with ICT third-party service providers.",
            ),
        )

        assertEquals(listOf("DORA/Art-28/1", "DORA/Art-28/2"), chunks.map { it.citationId })
        val second = chunks[1]
        assertEquals("Article 28", second.article)
        assertEquals("2", second.paragraph)
        assertTrue(second.text.startsWith("Regulation (EU) 2022/2554 (DORA), Article 28 (General principles): 2. "))
        assertFalse(chunks.any { "MANAGING" in it.text })
    }

    @Test
    fun pageNoiseAndFootnotesNeverBecomeText() {
        val chunks = chunk(
            dora,
            listOf(
                "EN Official Journal of the European Union L 333/1",
                "Article 3",
                "Definitions",
                "For the purposes of this Regulation, the following definitions apply:",
                "(1) 'digital operational resilience' means the ability of a financial entity to build its integrity;",
                "(1) Directive (EU) 2016/1148 of the European Parliament (OJ L 194, 19.7.2016, p. 1).",
                "This line is still in the footnote area and must be skipped as well.",
            ),
            listOf(
                "27.12.2022",
                "(2) 'ICT risk' means any reasonably identifiable circumstance in relation to the use of networks;",
            ),
        )

        val text = chunks.joinToString(" ") { it.text }
        assertFalse("Official Journal" in text)
        assertFalse("OJ L 194" in text)
        assertFalse("footnote area" in text)
        assertFalse("27.12.2022" in text)
        assertTrue("'ICT risk' means" in text)
    }

    @Test
    fun footnoteListsThatStartAtOneAreDropped() {
        val chunks = chunk(
            cir,
            listOf(
                "ANNEX II",
                "List of activities",
                "Credit institutions carry out the activities listed in the relevant directive.",
                "(1) Directive (EU) 2015/2366 of the European Parliament and of the Council on payment services",
                "(OJ L 337, 23.12.2015, p. 35, ELI: http://data.europa.eu/eli/dir/2015/2366/oj).",
                "(2) Directive 2009/110/EC on electronic money institutions.",
            ),
        )

        assertEquals(1, chunks.size)
        assertFalse("Directive" in chunks.single().text)
    }

    @Test
    fun quotedOfficialJournalReferencesInTheTextAreKept() {
        val chunks = chunk(
            dora,
            listOf(
                "Article 59",
                "Amendments",
                "(1) in Article 4, the reference to Regulation (EU) 2022/2554 (OJ L 333, 27.12.2022, p. 1) is inserted;",
            ),
        )

        assertTrue("(OJ L 333, 27.12.2022, p. 1)" in chunks.single().text)
    }

    @Test
    fun annexTemplateColumnsAndServiceTypesAreKeys() {
        val chunks = chunk(
            cir,
            listOf(
                "ANNEX I",
                "Templates for the register of information",
                "B_02.02.0050 Function identifier A unique identifier of each function, as defined in the register.",
                "ANNEX III",
                "Type of ICT services",
                "S18 18. Platform as a service (PaaS) Provision of a platform to develop and run applications.",
            ),
        )

        val column = chunks.single { it.citationId == "CIR-2024-2956/AnnexI/B_02.02.0050" }
        assertEquals("Annex I, template B_02.02", column.article)
        assertEquals("B_02.02.0050", column.paragraph)
        val service = chunks.single { it.citationId == "CIR-2024-2956/AnnexIII/S18" }
        assertEquals("Annex III", service.article)
        assertTrue(service.text.startsWith("CIR (EU) 2024/2956, Annex III (Type of ICT services): S18 18. "))
    }

    @Test
    fun markersOnTheirOwnLinesJoinTheNextLine() {
        val recitals = chunk(
            dora,
            listOf("(1)", "In the digital age, information and communication technology supports complex systems."),
        )
        val services = chunk(
            cir,
            listOf("ANNEX III", "Type of ICT services", "S01", "1.", "ICT project management", "Provision of PMO services."),
        )

        assertEquals("DORA/Recital/1", recitals.single().citationId)
        val service = services.single { it.citationId == "CIR-2024-2956/AnnexIII/S01" }
        assertTrue("S01 1. ICT project management Provision of PMO services." in service.text)
    }

    @Test
    fun longParagraphsAreSplitWithNumberedIds() {
        val words = List(500) { "resilience" }.joinToString(" ")
        val chunks = chunk(dora, listOf("Article 5", "Governance", "1. $words"))

        assertEquals(listOf("DORA/Art-5/1#1", "DORA/Art-5/1#2", "DORA/Art-5/1#3"), chunks.map { it.citationId })
        val header = "Regulation (EU) 2022/2554 (DORA), Article 5 (Governance): "
        assertTrue(chunks.all { it.text.length <= header.length + RegulationChunker.MAX_CHUNK_LENGTH })
    }

    @Test
    fun shortFragmentsAreDropped() {
        val chunks = chunk(
            dora,
            listOf("Article 5", "Governance", "1. Too short.", "2. The management body shall define the ICT risk framework."),
        )

        assertEquals(listOf("DORA/Art-5/2"), chunks.map { it.citationId })
    }

    @Test
    fun rowsRepeatedOnALaterPageAreDropped() {
        val row = "B_02.02.0050 Function identifier A unique identifier of each function, as defined in the register."
        val chunks = chunk(cir, listOf("ANNEX I", "Templates", row), listOf(row))

        assertEquals(listOf("CIR-2024-2956/AnnexI/B_02.02.0050"), chunks.map { it.citationId })
    }

    @Test
    fun chunksAreDeterministicAndHashTheirText() {
        val lines = listOf("Article 6", "Framework", "1. The management body shall define the ICT risk framework.")
        val first = chunk(dora, lines).single()
        val second = chunk(dora, lines).single()

        assertEquals(first, second)
        assertEquals(RegulationChunker.sha256(first.text), first.contentHash)
        assertEquals(64, first.contentHash.length)
        assertEquals(1, first.page)
    }

    @Test
    fun articleWithoutParagraphsHasNoParagraphKey() {
        val chunk = chunk(
            dora,
            listOf("Article 64", "Entry into force", "This Regulation shall enter into force on the twentieth day."),
        ).single()

        assertEquals("DORA/Art-64", chunk.citationId)
        assertNull(chunk.paragraph)
    }
}
