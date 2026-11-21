package dorai.server.export

import dorai.shared.roi.RoiColumn
import dorai.shared.roi.RoiReport
import dorai.shared.roi.RoiTable
import dorai.shared.roi.RoiType
import dorai.shared.roi.TaxonomyCodes
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Writes the RoI as an xBRL-CSV report package: one top-level folder with `META-INF/reportPackage.json`,
 * `reports/report.json`, `reports/parameters.csv`, `reports/FilingIndicators.csv` and one CSV per template
 * (`b_01.01.csv`, ...) whose headers are the column codes (`c0010`, ...). Every template is listed as
 * reported, also when it has no rows. Pure: the same report always gives the same bytes.
 */
object RoiPackage {

    data class Written(val fileName: String, val bytes: ByteArray)

    fun write(report: RoiReport, entityLei: String, entityCountry: String, baseCurrency: String, createdAt: String, draft: Boolean): Written {
        val name = TaxonomyCodes.packageName(entityLei, entityCountry, report.referenceDate.toString(), createdAt)
        val files = buildMap {
            put("META-INF/reportPackage.json", """{"documentInfo":{"documentType":"${TaxonomyCodes.reportPackageType}"}}""")
            put("reports/report.json", reportJson())
            put("reports/parameters.csv", csv(listOf(
                listOf("name", "value"),
                listOf("entityID", "rs:$entityLei.CON"),
                listOf("refPeriod", report.referenceDate.toString()),
                listOf("baseCurrency", "iso4217:$baseCurrency"),
                listOf("decimalsInteger", TaxonomyCodes.decimalsInteger.toString()),
                listOf("decimalsMonetary", TaxonomyCodes.decimalsMonetary.toString()),
            )))
            put("reports/FilingIndicators.csv", csv(listOf(listOf("templateID", "reported")) + report.tables.map { listOf(it.template.code, "true") }))
            report.tables.forEach { t ->
                if (t.template.code == "B_99.01") {
                    put("reports/b_99.01.csv", csv(definitions(t)))
                    return@forEach
                }
                val columns = t.template.columns
                // Rows the register keeps apart (one per resource) can be the same RoI row: report it once.
                val rows = t.rows.map { row -> columns.map { col -> row.value(col.code)?.let { encode(col, it) }.orEmpty() } }.distinct()
                put("reports/${t.template.code.lowercase()}.csv", csv(listOf(columns.map { it.header }) + rows))
            }
        }
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            files.forEach { (path, text) ->
                // Fixed timestamps keep the package byte-for-byte reproducible.
                zip.putNextEntry(ZipEntry("$name/$path").apply { time = 0 })
                zip.write(text.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return Written((if (draft) "DRAFT_" else "") + "$name.zip", out.toByteArray())
    }

    /**
     * B_99.01 in the taxonomy is one row with a column per defined option (c0010 to c0190); we keep it as one
     * row per option (column code, option key, definition) and turn it here. Options with no taxonomy column,
     * such as "Assessment not performed", are left out.
     */
    fun definitions(t: RoiTable): List<List<String>> {
        val cells = t.rows.mapNotNull { row ->
            val column = row.value("B_99.01.C0010") ?: return@mapNotNull null
            val key = row.value("B_99.01.C0030") ?: return@mapNotNull null
            val text = row.value("B_99.01.C0040") ?: return@mapNotNull null
            TaxonomyCodes.definitionColumn(column, key)?.let { it to text }
        }.toMap().toSortedMap()
        return if (cells.isEmpty()) listOf(emptyList()) else listOf(cells.keys.toList(), cells.values.toList())
    }

    /** The module fixes the tables, their file names and the parameters file, so report.json only extends it. */
    private fun reportJson(): String =
        """{"documentInfo":{"documentType":"${TaxonomyCodes.xbrlCsvType}","extends":["${TaxonomyCodes.moduleEntryPoint}"]}}"""

    /** A cell as the taxonomy wants it: closed lists by code, amounts in units with no grouping. */
    fun encode(col: RoiColumn, value: String): String = when (col.type) {
        RoiType.Choice, RoiType.YesNo, RoiType.Country, RoiType.Currency, RoiType.CodeType -> TaxonomyCodes.encode(col, value)
        RoiType.Monetary -> value.toBigDecimalOrNull()?.setScale(TaxonomyCodes.decimalsMonetary, java.math.RoundingMode.HALF_UP)?.toPlainString() ?: value
        else -> value
    }

    /** RFC 4180: quote a field when it holds a comma, quote or line break; lines end with CRLF. */
    fun csv(rows: List<List<String>>): String = rows.joinToString("") { row ->
        row.joinToString(",") { f -> if (f.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + f.replace("\"", "\"\"") + "\"" else f } + "\r\n"
    }
}
