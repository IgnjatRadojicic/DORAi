package dorai.server.export

import dorai.shared.roi.RoiColumn
import dorai.shared.roi.RoiReport
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
            put("reports/report.json", reportJson(report))
            put("reports/parameters.csv", csv(listOf(
                listOf("name", "value"),
                listOf("entityID", "rs:$entityLei.CON"),
                listOf("refPeriod", report.referenceDate.toString()),
                listOf("baseCurrency", "iso4217:$baseCurrency"),
                listOf("decimalsMonetary", TaxonomyCodes.decimalsMonetary.toString()),
            )))
            put("reports/FilingIndicators.csv", csv(listOf(listOf("templateID", "reported")) + report.tables.map { listOf(it.template.code, "true") }))
            report.tables.forEach { t ->
                val columns = t.template.columns
                val rows = t.rows.map { row -> columns.map { col -> row.value(col.code)?.let { encode(col, it) }.orEmpty() } }
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

    private fun reportJson(report: RoiReport): String {
        val tables = report.tables.joinToString(",") { t ->
            val code = t.template.code.lowercase()
            """"$code":{"url":"$code.csv"}"""
        }
        return """{"documentInfo":{"documentType":"${TaxonomyCodes.xbrlCsvType}","extends":["${TaxonomyCodes.moduleEntryPoint}"]},"tables":{$tables}}"""
    }

    /** A cell as the taxonomy wants it: closed lists by code, amounts in units with no grouping. */
    fun encode(col: RoiColumn, value: String): String = when (col.type) {
        RoiType.Choice, RoiType.YesNo -> TaxonomyCodes.encode(col, value)
        RoiType.Monetary -> value.toBigDecimalOrNull()?.setScale(TaxonomyCodes.decimalsMonetary, java.math.RoundingMode.HALF_UP)?.toPlainString() ?: value
        else -> value
    }

    /** RFC 4180: quote a field when it holds a comma, quote or line break; lines end with CRLF. */
    fun csv(rows: List<List<String>>): String = rows.joinToString("") { row ->
        row.joinToString(",") { f -> if (f.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + f.replace("\"", "\"\"") + "\"" else f } + "\r\n"
    }
}
