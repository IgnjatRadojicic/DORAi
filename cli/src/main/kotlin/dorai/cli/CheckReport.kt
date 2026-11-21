package dorai.cli

import dorai.shared.Dorai
import dorai.shared.model.Citation
import dorai.shared.model.DoraiJson
import dorai.shared.model.Finding
import dorai.shared.model.Severity
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class CheckFormat { Text, Markdown, Json }

/** Exit codes of `dorai check`. */
object ExitCodes {
    const val CLEAN = 0
    const val HIGH = 1
    const val INVALID_INPUT = 2
}

/**
 * The one renderer for findings; the Markdown is the PR comment and starts with the marker the GitHub
 * Action looks for to update its comment. Resource names and other text come from the PR author, so every
 * string is cleaned here: no control characters, no HTML, no @mentions, no table breakouts, bounded size.
 */
object CheckReport {
    const val STICKY_MARKER = "<!-- dorai-check -->"

    /** GitHub rejects comments over 65,536 characters. */
    const val MAX_MARKDOWN_BYTES = 60_000

    fun exitCode(findings: List<Finding>) = if (findings.any { it.severity == Severity.High }) ExitCodes.HIGH else ExitCodes.CLEAN

    fun format(findings: List<Finding>, format: CheckFormat): String {
        // Stable order whatever the input order: High first, then resource, then rule.
        val ordered = findings.sortedWith(
            compareByDescending<Finding> { it.severity }
                .thenBy { it.resourceAddress }.thenBy { it.ruleId }.thenBy { it.title }.thenBy { it.detail },
        )
        val text = when (format) {
            CheckFormat.Json -> prettyJson.encodeToString(JsonReport(Summary.of(ordered), ordered))
            CheckFormat.Markdown -> markdown(ordered)
            CheckFormat.Text -> text(ordered)
        }
        return text.replace("\r\n", "\n").trimEnd() + "\n"
    }

    fun headline(count: Int) = when (count) {
        0 -> "${Dorai.NAME}: no register updates required"
        1 -> "${Dorai.NAME}: 1 register update required"
        else -> "${Dorai.NAME}: $count register updates required"
    }

    @Serializable
    private data class Summary(val total: Int, val high: Int, val medium: Int, val low: Int) {
        companion object {
            fun of(f: List<Finding>) = Summary(f.size, f.count(Severity.High), f.count(Severity.Medium), f.count(Severity.Low))
        }
    }

    @Serializable
    private data class JsonReport(val summary: Summary, val findings: List<Finding>)

    private val prettyJson = Json(DoraiJson) { prettyPrint = true }

    private fun List<Finding>.count(severity: Severity) = count { it.severity == severity }

    private fun functionLine(f: Finding): String? = f.functionId?.let { id ->
        listOfNotNull("Function $id", f.functionName).joinToString(" ") + if (f.criticalFunction) " (critical)" else ""
    }

    private fun footer(findings: List<Finding>) =
        if (findings.any { "B_05.01" in it.templates }) "B_05.01 providers change: review the provider list."
        else "No changes to B_05.01 providers."

    private fun text(findings: List<Finding>) = buildString {
        appendLine(headline(findings.size)).appendLine()
        for (f in findings) {
            appendLine("${f.severity.name.uppercase().padEnd(7)} ${display(f.title)}")
            appendLine("        ${display(f.resourceAddress)}${f.location?.let { " -> " + display(it) } ?: ""}")
            functionLine(f)?.let { appendLine("        ${display(it)}") }
            appendLine("        ${display(f.detail)}")
            appendLine("        Register: ${display(f.templates.joinToString(", "))}")
            f.explanation?.takeIf { it.isNotBlank() }?.let { appendLine("        Why: ${display(it)}") }
            f.citation?.let { appendLine("        Ref: ${display(citationLine(it))}") }
            appendLine()
        }
        appendLine(footer(findings))
    }

    private fun markdown(findings: List<Finding>): String {
        val head = StringBuilder().appendLine(STICKY_MARKER).appendLine("### ${headline(findings.size)}").appendLine()
        val high = findings.count(Severity.High)
        val tail = StringBuilder().appendLine().appendLine(footer(findings)).appendLine()
            .appendLine(
                if (high > 0) "**$high High finding(s): this check fails until the register is updated or the change is approved.**"
                else "No High findings: this check passes.",
            )
            .appendLine()
            .appendLine("<sub>Severity comes from fixed rules, never from AI. AI explanations are marked and cite the regulation.</sub>")
        if (findings.isNotEmpty()) {
            head.appendLine("| Severity | Finding | Resource | Function | Register |").appendLine("|---|---|---|---|---|")
            var budget = MAX_MARKDOWN_BYTES - head.utf8Size() - tail.utf8Size() - 300
            var included = 0
            for (f in findings) {
                val row = row(f)
                val size = row.encodeToByteArray().size
                if (size > budget) break
                head.append(row)
                budget -= size
                included++
            }
            if (included < findings.size) {
                head.appendLine().appendLine(
                    "**${findings.size - included} more finding(s) omitted to fit this comment.** Use `--format json` for the complete report.",
                )
            }
        }
        return head.append(tail).toString()
    }

    private fun CharSequence.utf8Size() = toString().encodeToByteArray().size

    private fun row(f: Finding): String {
        val severity = when (f.severity) {
            Severity.High -> "🔴 HIGH"
            Severity.Medium -> "🟠 MEDIUM"
            Severity.Low -> "⚪ LOW"
        }
        val resource = code(f.resourceAddress) + (f.location?.let { " → " + code(it) } ?: "")
        var finding = "**${cell(f.title, 160)}**<br>${cell(f.detail)}"
        f.explanation?.takeIf { it.isNotBlank() }?.let { finding += "<br>_AI explanation:_ ${cell(it)}" }
        f.citation?.let { finding += "<br>_Ref:_ ${cell(citationLine(it), 300)}" }
        return "| $severity | $finding | $resource | ${cell(functionLine(f).orEmpty())} | ${cell(f.templates.joinToString(", "))} |\n"
    }

    private fun citationLine(c: Citation) =
        "${c.regulation}, ${c.article}" + (c.paragraph?.takeIf { it.isNotBlank() }?.let { "($it)" } ?: "") + " [${c.id}]"

    /**
     * Plain-text cleaning shared by every format: control, format and separator characters become spaces, so
     * untrusted text cannot add lines, terminal escapes or workflow annotations. Long text is cut with an ellipsis.
     */
    internal fun display(value: String, limit: Int = 2000): String {
        val cleaned = buildString(value.length) {
            for (ch in value) {
                val type = Character.getType(ch).toByte()
                val unsafe = ch.isISOControl() || type == Character.FORMAT || type == Character.LINE_SEPARATOR ||
                    type == Character.PARAGRAPH_SEPARATOR
                append(if (unsafe) ' ' else ch)
            }
        }
        if (cleaned.length <= limit) return cleaned
        val cut = if (cleaned[limit - 1].isHighSurrogate()) limit - 1 else limit
        return cleaned.substring(0, cut) + "…"
    }

    /** Table cell: HTML-encoded, no @mentions, no pipe breakouts, Markdown emphasis and links neutralised. */
    internal fun cell(value: String, limit: Int = 2000) = buildString {
        for (ch in display(value, limit)) {
            when (ch) {
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '&' -> append("&amp;")
                '@' -> append("&#64;")
                '|' -> append("\\|")
                '\\', '`', '*', '_', '[', ']', '~' -> append('\\').append(ch)
                else -> append(ch)
            }
        }
    }

    /** Inline code is shown literally, so only what ends the span or the cell, or starts a mention, is replaced. */
    internal fun code(value: String) = "`" + display(value, 1024).replace("`", "'").replace("|", "\\|")
        .replace("<", "‹").replace(">", "›").replace("@", "＠") + "`"
}
