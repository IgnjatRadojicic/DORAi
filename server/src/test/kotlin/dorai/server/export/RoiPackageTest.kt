package dorai.server.export

import dorai.server.fixtures.FixtureData
import dorai.shared.roi.RoiSpec
import dorai.shared.roi.RoiTables
import kotlinx.datetime.LocalDate
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RoiPackageTest {

    private val data = FixtureData.load()
    private val date = LocalDate(2026, 12, 31)

    private fun written(draft: Boolean = false) = RoiPackage.write(
        RoiTables.build(data.register, data.resources, date),
        data.register.entity.lei, data.register.entity.country, "EUR", "20270115120000000", draft,
    )

    private fun files(bytes: ByteArray): Map<String, String> = buildMap {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            generateSequence { zip.nextEntry }.forEach { put(it.name, zip.readBytes().decodeToString()) }
        }
    }

    @Test
    fun `package has the report files and one csv per template under one folder`() {
        val w = written()
        val files = files(w.bytes)
        val root = w.fileName.removeSuffix(".zip")
        assertTrue(files.keys.all { it.startsWith("$root/") }, files.keys.toString())
        val names = files.keys.map { it.removePrefix("$root/") }.toSet()
        val expected = setOf("META-INF/reportPackage.json", "reports/report.json", "reports/parameters.csv", "reports/FilingIndicators.csv") +
            RoiSpec.templates.map { "reports/${it.code.lowercase()}.csv" }
        assertEquals(expected, names)
    }

    @Test
    fun `every template is a filing indicator, in capitals`() {
        val text = files(written().bytes).entries.single { it.key.endsWith("FilingIndicators.csv") }.value
        val lines = text.trimEnd().split("\r\n")
        assertEquals("templateID,reported", lines.first())
        assertEquals(RoiSpec.templates.map { "${it.code},true" }, lines.drop(1))
    }

    @Test
    fun `template csv headers are the column codes`() {
        val files = files(written().bytes)
        val b0202 = files.entries.single { it.key.endsWith("b_02.02.csv") }.value.split("\r\n")
        assertEquals(RoiSpec.template("B_02.02").columns.joinToString(",") { it.header }, b0202.first())
        assertTrue(b0202.size > 2, "has rows")
        val params = files.entries.single { it.key.endsWith("parameters.csv") }.value
        assertTrue("entityID,rs:${data.register.entity.lei}.CON" in params)
        assertTrue("refPeriod,2026-12-31" in params)
    }

    @Test
    fun `same register gives the same bytes`() {
        assertContentEquals(written().bytes, written().bytes)
    }

    @Test
    fun `a draft is named as one`() {
        assertTrue(written(draft = true).fileName.startsWith("DRAFT_"))
        assertTrue(!written().fileName.startsWith("DRAFT_"))
    }

    @Test
    fun `csv quotes commas, quotes and line breaks`() {
        assertEquals("a,\"b,c\",\"d\"\"e\"\r\n", RoiPackage.csv(listOf(listOf("a", "b,c", "d\"e"))))
    }

    @Test
    fun `amounts keep two decimals with no grouping`() {
        assertEquals("40000.00", RoiPackage.encode(RoiSpec.column("B_02.01.0050")!!, "40000"))
    }

    @Test
    fun `closed lists, countries, currencies and code types are reported as taxonomy members`() {
        fun enc(column: String, value: String) = RoiPackage.encode(RoiSpec.column(column)!!, value)
        assertEquals("eba_BT:x28", enc("B_02.02.0140", "true"))
        assertEquals("eba_ZZ:x791", enc("B_02.02.0170", "1"))
        assertEquals("eba_TA:S18", enc("B_02.02.0060", "S18"))
        assertEquals("eba_TA:x28", enc("B_06.01.0020", "x28"))
        assertEquals("eba_GA:IE", enc("B_05.01.0080", "IE"))
        assertEquals("eba_CU:EUR", enc("B_02.01.0040", "EUR"))
        assertEquals("eba_qCO:qx2000", enc("B_05.01.0020", "LEI"))
        assertEquals("eba_qCO:qx2003", enc("B_05.01.0020", "NL_CRN"))
        assertEquals("true", enc("B_03.01.0030", "true"))
    }

    @Test
    fun `report json only extends the dora module and parameters give the decimals`() {
        val files = files(written().bytes)
        val report = files.entries.single { it.key.endsWith("report.json") }.value
        assertTrue("fws/dora/4.0/mod/dora.json" in report, report)
        assertTrue("\"tables\"" !in report, "the module fixes the tables")
        val params = files.entries.single { it.key.endsWith("parameters.csv") }.value
        assertTrue("decimalsInteger,0" in params && "decimalsMonetary,2" in params, params)
    }

    @Test
    fun `B_99_01 is one row with a column per defined option`() {
        val lines = files(written().bytes).entries.single { it.key.endsWith("b_99.01.csv") }.value.trimEnd().split("\r\n")
        assertEquals(2, lines.size)
        val header = lines.first().split(",")
        assertEquals((1..19).map { "c" + (it * 10).toString().padStart(4, '0') }, header)
    }

    @Test
    fun `rows that end up the same are reported once`() {
        val lines = files(written().bytes).entries.single { it.key.endsWith("b_02.02.csv") }.value.trimEnd().split("\r\n")
        assertEquals(lines.size, lines.toSet().size)
    }
}
