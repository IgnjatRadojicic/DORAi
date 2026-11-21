package dorai.server.ai.ingest

/** The regulation documents shipped with the server, under `resources/ai/regulations`. */
object RegulationCorpus {
    data class Document(val resource: String, val source: RegulationSource)

    val documents = listOf(
        Document("ai/regulations/dora-2022-2554.pdf", RegulationSource("DORA", "Regulation (EU) 2022/2554 (DORA)")),
        Document("ai/regulations/cir-2024-2956.pdf", RegulationSource("CIR-2024-2956", "CIR (EU) 2024/2956")),
    )

    /** Reads and chunks every document. Fails if a document is missing from the classpath. */
    fun chunks(): List<RegulationChunk> = documents.flatMap { document ->
        val pdf = RegulationCorpus::class.java.classLoader.getResourceAsStream(document.resource)
            ?.use { it.readBytes() }
            ?: error("Regulation document ${document.resource} is missing")
        RegulationChunker.chunk(document.source, PdfPageReader.read(pdf))
    }
}
