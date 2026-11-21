package dorai.server.ai.ingest

import dorai.server.ai.ingest.RegulationPatterns.ANNEX_HEADING
import dorai.server.ai.ingest.RegulationPatterns.ARTICLE_HEADING
import dorai.server.ai.ingest.RegulationPatterns.CHAPTER_HEADING
import dorai.server.ai.ingest.RegulationPatterns.NUMBERED
import dorai.server.ai.ingest.RegulationPatterns.OJ_FOOTNOTE
import dorai.server.ai.ingest.RegulationPatterns.PARAGRAPH_START
import dorai.server.ai.ingest.RegulationPatterns.TEMPLATE_CODE
import java.security.MessageDigest

/**
 * Splits the text of an Official Journal regulation into citable chunks that follow its structure:
 * recitals, article paragraphs, Annex I template columns (`B_xx.xx.nnnn`) and Annex III service
 * types (`S01` to `S19`). Page headers, footers and footnotes are dropped so they never become citations.
 */
object RegulationChunker {
    const val MAX_CHUNK_LENGTH = 2000
    const val MIN_CHUNK_LENGTH = 40

    fun chunk(source: RegulationSource, pages: List<PageText>): List<RegulationChunk> {
        val builder = ChunkBuilder(source)
        for (page in pages) {
            val lines = textLines(page.lines)
            // Footnotes sit at the bottom of a page: skip the rest of it.
            for (line in lines.subList(0, footnoteBlockStart(lines))) {
                if (builder.isFootnote(line)) break
                builder.accept(line, page.number)
            }
        }
        builder.flush()
        return builder.chunks
    }

    /** Trimmed lines without page noise; markers such as `(1)`, `2.` or `S01` on lines of their own join the next line. */
    private fun textLines(raw: List<String>): List<String> {
        val lines = mutableListOf<String>()
        var marker: String? = null
        for (line in raw) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || RegulationPatterns.isPageNoise(trimmed)) continue
            if (RegulationPatterns.BARE_MARKER.matches(trimmed)) {
                marker = marker?.let { "$it $trimmed" } ?: trimmed
                continue
            }
            lines += marker?.let { "$it $trimmed" } ?: trimmed
            marker = null
        }
        return lines
    }

    /**
     * Index of the first numbered block that cites another act with its Official Journal reference
     * and ELI link, which only footnotes do; the line count when the page has none.
     */
    private fun footnoteBlockStart(lines: List<String>): Int {
        val starts = lines.indices.filter { RegulationPatterns.NUMBERED.containsMatchIn(lines[it]) }
        starts.forEachIndexed { i, start ->
            val end = starts.getOrNull(i + 1) ?: lines.size
            if (RegulationPatterns.FOOTNOTE_CITATION.containsMatchIn(lines.subList(start, end).joinToString(" "))) return start
        }
        return lines.size
    }

    /** Splits [text] at word boundaries into parts of at most [MAX_CHUNK_LENGTH] characters. */
    internal fun splitLong(text: String): List<String> {
        if (text.length <= MAX_CHUNK_LENGTH) return listOf(text)
        val parts = mutableListOf<String>()
        val current = StringBuilder()
        for (word in text.split(' ')) {
            if (current.isNotEmpty() && current.length + word.length + 1 > MAX_CHUNK_LENGTH) {
                parts += current.toString()
                current.clear()
            }
            if (current.isNotEmpty()) current.append(' ')
            current.append(word)
        }
        if (current.isNotEmpty()) parts += current.toString()
        return parts
    }

    internal fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}

/** Walks the lines of one document and collects chunks; one instance per document. */
private class ChunkBuilder(private val source: RegulationSource) {
    val chunks = mutableListOf<RegulationChunk>()

    private val ids = HashSet<String>()
    private val hashes = HashSet<String>()
    private val text = StringBuilder()
    private var section = PREAMBLE
    private var idSection = "Recital"
    private var title: String? = null
    private var key: String? = null
    private var page = 0
    private var lastNumbered = 0
    private var awaitingTitle = false
    private var skipChapterTitle = false

    /** A numbered line that does not continue the current list, or that is only an Official Journal reference. */
    fun isFootnote(line: String): Boolean {
        val number = NUMBERED.find(line)?.groupValues?.get(1)?.toInt() ?: return false
        return number != lastNumbered + 1 || OJ_FOOTNOTE.containsMatchIn(line)
    }

    fun accept(line: String, pageNumber: Int) {
        ARTICLE_HEADING.matchEntire(line)?.let {
            startSection("Article ${it.groupValues[1]}", "Art-${it.groupValues[1]}", pageNumber)
            return
        }
        ANNEX_HEADING.matchEntire(line)?.let {
            startSection("Annex ${it.groupValues[1]}", "Annex${it.groupValues[1]}", pageNumber)
            return
        }
        if (CHAPTER_HEADING.matches(line)) {
            skipChapterTitle = true
            return
        }
        if (skipChapterTitle) {
            skipChapterTitle = false
            return
        }
        if (RegulationPatterns.isUpperCaseTitle(line)) return
        if (awaitingTitle) {
            awaitingTitle = false
            if (!PARAGRAPH_START.containsMatchIn(line) && line.length < MAX_TITLE_LENGTH) {
                title = line
                return
            }
        }

        val inAnnex = section.startsWith("Annex")
        val annexKey = if (inAnnex) RegulationPatterns.annexKey(line) else null
        val paragraph = if (!inAnnex && section != PREAMBLE) PARAGRAPH_START.find(line) else null
        when {
            annexKey != null -> startKey(annexKey, pageNumber)
            paragraph != null -> startKey(paragraph.groupValues[1], pageNumber)
            else -> NUMBERED.find(line)?.let {
                val number = it.groupValues[1]
                lastNumbered = number.toInt()
                // Recitals are chunks of their own; numbered points stay inside their paragraph.
                if (section == PREAMBLE) startKey(number, pageNumber)
            }
        }

        if (text.isEmpty()) page = pageNumber else text.append(' ')
        text.append(line)
    }

    private fun startSection(name: String, id: String, pageNumber: Int) {
        flush()
        section = name
        idSection = id
        title = null
        key = null
        page = pageNumber
        lastNumbered = 0
        awaitingTitle = true
    }

    private fun startKey(newKey: String, pageNumber: Int) {
        flush()
        key = newKey
        page = pageNumber
    }

    fun flush() {
        val body = text.split(WHITESPACE).filter { it.isNotEmpty() }.joinToString(" ")
        text.clear()
        if (body.length < RegulationChunker.MIN_CHUNK_LENGTH) return

        val header = title?.let { "${source.regulation}, $section ($it)" } ?: "${source.regulation}, $section"
        val parts = RegulationChunker.splitLong(body)
        val baseId = listOfNotNull(source.idPrefix, idSection, key).joinToString("/")
        parts.forEachIndexed { index, part ->
            val chunkText = "$header: $part"
            val hash = RegulationChunker.sha256(chunkText)
            if (!hashes.add(hash)) return@forEachIndexed

            val partId = if (parts.size > 1) "$baseId#${index + 1}" else baseId
            var id = partId
            var copy = 2
            while (!ids.add(id)) id = "$partId~${copy++}"

            chunks += RegulationChunk(id, source.regulation, articleLabel(), key, chunkText, hash, page)
        }
    }

    private fun articleLabel(): String {
        val template = key?.let { TEMPLATE_CODE.find(it)?.value }
        return if (template != null) "$section, template $template" else section
    }

    private companion object {
        const val PREAMBLE = "Preamble"
        const val MAX_TITLE_LENGTH = 120
        val WHITESPACE = Regex("""\s+""")
    }
}
