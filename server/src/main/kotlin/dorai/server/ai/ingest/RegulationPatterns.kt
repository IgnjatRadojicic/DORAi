package dorai.server.ai.ingest

/** Line patterns of Official Journal texts (DORA and the CIR register templates). */
internal object RegulationPatterns {
    val ARTICLE_HEADING = Regex("""^Article (\d+[a-z]?)$""")
    val ANNEX_HEADING = Regex("""^ANNEX ([IVX]+)$""")
    val CHAPTER_HEADING = Regex("""^(CHAPTER|Section) [IVX]+$""")

    /** `1. ` at the start of an article paragraph. */
    val PARAGRAPH_START = Regex("""^(\d+)\.\s""")

    /** `(12) ` at the start of a recital, a definition or a footnote. */
    val NUMBERED = Regex("""^\((\d+)\)\s""")

    /** A list marker or service type code alone on its line: `(12)`, `3.` or `S18`. */
    val BARE_MARKER = Regex("""^(\(\d+\)|\d+\.|S\d{2})$""")

    /** A footnote that is only an Official Journal reference: `(1) OJ C 343, 26.8.2021, p. 1.` */
    val OJ_FOOTNOTE = Regex("""^\(\d+\)\s+OJ [LC] """)

    /** `(OJ L 337, 23.12.2015, p. 35, ELI: http...`: the way footnotes cite other acts. */
    val FOOTNOTE_CITATION = Regex("""\bOJ [LC] \d+, \d{1,2}\.\d{1,2}\.\d{4}, p\. \d+, ELI:""")

    /** Annex I column row, for example `B_02.02.0050 Function identifier`. */
    val TEMPLATE_COLUMN = Regex("""^(B_\d{2}\.\d{2}\.\d{4})( [A-Z]|$)""")

    /** Annex I template overview row, for example `B_02.02 Contractual arrangements`. */
    val TEMPLATE_OVERVIEW = Regex("""^(B_\d{2}\.\d{2})\s""")

    val TEMPLATE_CODE = Regex("""B_\d{2}\.\d{2}""")

    /** Annex III service type row, for example `S18 18. Platform as a service ...`. */
    val SERVICE_TYPE_ROW = Regex("""^(S\d{2})\s\d+\.\s""")

    private val PAGE_NOISE = Regex(
        """(^EN Official Journal of the European Union( |$)|^(\d+/\d+ )?ELI: http|^L \d+/\d+|^\d+/\d+$|^\d{4}/\d+ \d{1,2}\.\d{1,2}\.\d{4}$|^\d{1,2}\.\d{1,2}\.\d{4}$)""",
    )

    private val NOISE_LINES = setOf("EN", "Official Journal", "of the European Union", "L series")

    /** Page headers, footers and repeated table headers: never part of a citation. */
    fun isPageNoise(line: String): Boolean =
        line in NOISE_LINES ||
            PAGE_NOISE.containsMatchIn(line) ||
            line.startsWith("Column Code Column Name") ||
            line.startsWith("Template Code Template Name")

    /** Chapter and section titles are written in capitals; they would only add noise to a chunk. */
    fun isUpperCaseTitle(line: String): Boolean =
        line.length > 3 &&
            line.any { it.isLetter() } &&
            line.none { it.isLowerCase() } &&
            !TEMPLATE_CODE.containsMatchIn(line) &&
            !SERVICE_TYPE_ROW.containsMatchIn(line)

    /** The citation key an annex line opens: a template column, a template or a service type. */
    fun annexKey(line: String): String? =
        (TEMPLATE_COLUMN.find(line) ?: TEMPLATE_OVERVIEW.find(line) ?: SERVICE_TYPE_ROW.find(line))
            ?.groupValues?.get(1)
}
