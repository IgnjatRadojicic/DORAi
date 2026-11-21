package dorai.server.ai.ingest

/** A regulation document: the prefix of its citation ids and the name shown in citations. */
data class RegulationSource(val idPrefix: String, val regulation: String)

/** The text lines of one PDF page, in reading order. */
data class PageText(val number: Int, val lines: List<String>)

/**
 * One citable piece of a regulation.
 *
 * [citationId] is stable across runs, for example `DORA/Art-28/4` or `CIR-2024-2956/AnnexIII/S18`.
 * [text] starts with the regulation and article so the chunk reads on its own in a prompt.
 * [contentHash] is the SHA-256 of [text] in lowercase hex.
 */
data class RegulationChunk(
    val citationId: String,
    val regulation: String,
    val article: String,
    val paragraph: String?,
    val text: String,
    val contentHash: String,
    val page: Int,
)
