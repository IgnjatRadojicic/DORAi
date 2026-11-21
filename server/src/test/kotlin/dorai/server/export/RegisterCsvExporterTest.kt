package dorai.server.export

import dorai.server.ApiProblem
import dorai.server.fixtures.FixtureData
import dorai.shared.model.ProvenanceLabel
import dorai.shared.model.Register
import dorai.shared.model.RegisterField
import java.io.ByteArrayInputStream
import java.io.StringReader
import java.util.Locale
import java.util.TimeZone
import java.util.zip.ZipInputStream
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import dorai.shared.model.DoraiJson
import org.apache.commons.csv.CSVFormat
import kotlin.test.*

class RegisterCsvExporterTest {
    private val base = FixtureData.load().register
    private val exporter = RegisterCsvExporter()

    @Test
    fun fiveTemplatesUseTheSpecifiedHeadersAndExpectedRecordCounts() = runTest {
        val files = unzip(exporter.csvZip(base))
        val expected = mapOf(
            "B_01.01.csv" to (listOf(10, 20, 30, 40, 50, 60) to 1),
            "B_02.01.csv" to (listOf(10, 20, 30, 40, 50) to 2),
            "B_02.02.csv" to ((10..180 step 10).toList() to 15),
            "B_05.01.csv" to ((10..120 step 10).toList() to 2),
            "B_06.01.csv" to (listOf(10, 20, 30, 40, 60, 70, 80, 90, 100, 110) to 5),
        )
        assertEquals(expected.keys + setOf("README.txt", "manifest.json"), files.keys)
        expected.forEach { (name, spec) ->
            val rows = rows(files, name)
            val code = name.removeSuffix(".csv")
            assertEquals(spec.first.map { "$code.${it.toString().padStart(4, '0')}" }, rows.first(), name)
            assertEquals(spec.second + 1, rows.size, name)
            rows.forEach { assertEquals(spec.first.size, it.size, name) }
        }
        assertEquals(listOf(base.entity.lei, base.entity.name, base.entity.country, "", "", ""), rows(files, "B_01.01.csv")[1])
    }

    @Test
    fun relatedTablesUseProviderLeisAndKnownFunctionKeys() = runTest {
        val files = unzip(exporter.csvZip(base))
        val services = rows(files, "B_02.02.csv").drop(1)
        val providerLeis = rows(files, "B_05.01.csv").drop(1).map { it[0] }.toSet()
        val functionIds = rows(files, "B_06.01.csv").drop(1).map { it[0] }.toSet()
        services.forEach { row ->
            assertTrue(row[2] in providerLeis)
            assertEquals("LEI", row[3])
            assertTrue(row[4] in functionIds)
            assertEquals(base.entity.lei, row[1])
        }
        assertEquals("2025-01-01", services[0][6])
        assertEquals("9999-12-31", services[0][7])
        assertEquals("Yes", services[0][13])
        assertEquals("IT", services[0][14])
        assertEquals("Yes", rows(files, "B_06.01.csv")[1][4])
    }

    @Test
    fun decimalsAndProviderTotalsDoNotDependOnMachineLocale() = runTest {
        val contract = base.contracts[0]
        val changed = base.copy(contracts = listOf(
            contract.copy(annualCost = contract.annualCost.copy(value = "1234.5600")), base.contracts[1],
            contract.copy(id = "C3", annualCost = contract.annualCost.copy(value = "10.250"), services = emptyList()),
        ))
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            val files = unzip(exporter.csvZip(changed))
            assertEquals("1234.56", rows(files, "B_02.01.csv")[1][4])
            assertEquals("EUR", rows(files, "B_05.01.csv")[1][8])
            assertEquals("1244.81", rows(files, "B_05.01.csv")[1][9])
            assertEquals("7000", rows(files, "B_05.01.csv")[2][9])
        } finally { Locale.setDefault(previous) }
    }

    @Test
    fun mixedCurrenciesAndUnconfirmedAmountsNeverProducePartialTotals() = runTest {
        val contract = base.contracts[0]
        listOf(
            contract.copy(id = "C3", currency = "USD", services = emptyList()),
            contract.copy(id = "C3", annualCost = ai(contract.annualCost), services = emptyList()),
        ).forEach { extra ->
            val files = unzip(exporter.csvZip(base.copy(contracts = base.contracts + extra)))
            assertEquals(listOf("", ""), rows(files, "B_05.01.csv")[1].slice(8..9))
        }
    }

    @Test
    fun allUnconfirmedAiFieldsAreOmittedAndAudited() = runTest {
        listOf("Lei", "HeadquartersCountry", "AnnualCost", "ServiceType", "FunctionId", "DataLocation").forEach { field ->
            val files = unzip(exporter.csvZip(withAi(field)))
            val (name, index) = when (field) {
                "Lei" -> "B_05.01.csv" to 0
                "HeadquartersCountry" -> "B_05.01.csv" to 7
                "AnnualCost" -> "B_02.01.csv" to 4
                "ServiceType" -> "B_02.02.csv" to 5
                "FunctionId" -> "B_02.02.csv" to 4
                else -> "B_02.02.csv" to 14
            }
            assertEquals("", rows(files, name)[1][index], field)
            val audit = manifest(files)
            assertTrue(audit.getValue("omittedAiSuggestedFields").jsonArray.any { it.jsonPrimitive.content.endsWith("/$field") }, field)
            assertTrue(audit.getValue("missingKnownFields").jsonArray.isNotEmpty())
            if (field == "Lei") {
                assertEquals("", rows(files, "B_02.02.csv")[1][2])
                assertEquals("", rows(files, "B_02.02.csv")[1][3])
            }
            if (field == "AnnualCost") assertEquals("", rows(files, "B_05.01.csv")[1][9])
        }
    }

    @Test
    fun commasQuotesLineBreaksAndUnicodeRoundTripAsUtf8WithoutBom() = runTest {
        listOf("FinPay, Payments", "FinPay \"Payments\"", "FinPay\r\nPayments", "FinPay\nPayments", "FinPay Žiro računi").forEach { name ->
            val files = unzip(exporter.csvZip(base.copy(entity = base.entity.copy(name = name))))
            assertEquals(name, rows(files, "B_01.01.csv")[1][1])
            assertFalse(files.getValue("B_01.01.csv").startsWith("\uFEFF"))
        }
        val raw = unzip(exporter.csvZip(base)).getValue("B_01.01.csv")
        assertTrue(raw.endsWith("\r\n"))
        assertEquals(2, raw.split("\r\n").count { it.isNotEmpty() })
    }

    @Test
    fun formulaLikeTextIsEscapedBeforeCsvQuoting() = runTest {
        listOf("=1+1", "+SUM(1,2)", "-1+2", "@SUM(1,2)", "  =1+1", "\t=1+1", "\r\n=1+1").forEach { name ->
            val files = unzip(exporter.csvZip(base.copy(entity = base.entity.copy(name = name))))
            assertEquals("'$name", rows(files, "B_01.01.csv")[1][1])
        }
    }

    @Test
    fun unavailableAndMissingFieldsAreNotInvented() = runTest {
        val provider = base.providers[0].copy(name = "Банк", lei = base.providers[0].lei.copy(value = null))
        val files = unzip(exporter.csvZip(base.copy(providers = listOf(provider) + base.providers.drop(1))))
        val row = rows(files, "B_05.01.csv")[1]
        assertEquals("Банк", row[4])
        assertEquals("", row[0])
        assertEquals("", row[1])
        assertEquals("", row[5])
        val audit = manifest(files)
        assertFalse(audit.getValue("submissionReady").jsonPrimitive.boolean)
        assertEquals(base.asOf.toString(), audit.getValue("snapshotAsOf").jsonPrimitive.content)
        assertTrue(audit.getValue("templates").jsonArray[0].jsonObject.getValue("unavailableColumns").jsonArray.any { it.jsonPrimitive.content == "B_01.01.0040" })
        val missing = audit.getValue("missingKnownFields").jsonArray.map { it.jsonPrimitive.content }
        assertTrue("B_05.01/${provider.id}/B_05.01.0010" in missing)
        assertTrue("B_05.01/${provider.id}/B_05.01.0060" in missing)
    }

    @Test
    fun orderingAndTimeZoneDoNotChangeTheArchiveBytes() = runTest {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            val first = exporter.csvZip(base)
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
            val reversed = base.copy(providers = base.providers.reversed(), functions = base.functions.reversed(),
                contracts = base.contracts.reversed().map { it.copy(services = it.services.reversed()) })
            assertTrue(first.contentEquals(exporter.csvZip(reversed)), "Archive bytes must not depend on input order or the machine timezone")
        } finally { TimeZone.setDefault(original) }
    }

    @Test
    fun emptyCollectionsStillProduceAllHeaders() = runTest {
        val files = unzip(exporter.csvZip(base.copy(providers = emptyList(), contracts = emptyList(), functions = emptyList())))
        listOf("B_02.01.csv", "B_02.02.csv", "B_05.01.csv", "B_06.01.csv").forEach { assertEquals(1, rows(files, it).size) }
    }

    @Test
    fun invalidRegisterFailsBeforeAnArchiveIsProduced() = runTest {
        val error = assertFailsWith<ApiProblem> { exporter.csvZip(base.copy(entity = base.entity.copy(name = ""))) }
        assertEquals(422, error.status.value)
    }

    @Test
    fun pdfIsExplicitlyUnavailable() = runTest {
        assertEquals(501, assertFailsWith<ApiProblem> { exporter.pdf(base, emptyList()) }.status.value)
    }

    private fun ai(field: RegisterField) = field.copy(provenance = field.provenance.copy(label = ProvenanceLabel.AiSuggested))

    private fun withAi(field: String): Register {
        val provider = base.providers[0]
        val contract = base.contracts[0]
        val service = contract.services[0]
        return when (field) {
            "Lei" -> base.copy(providers = listOf(provider.copy(lei = ai(provider.lei))) + base.providers.drop(1))
            "HeadquartersCountry" -> base.copy(providers = listOf(provider.copy(headquartersCountry = ai(provider.headquartersCountry))) + base.providers.drop(1))
            "AnnualCost" -> base.copy(contracts = listOf(contract.copy(annualCost = ai(contract.annualCost))) + base.contracts.drop(1))
            else -> base.copy(contracts = listOf(contract.copy(services = listOf(when (field) {
                "ServiceType" -> service.copy(serviceType = ai(service.serviceType))
                "FunctionId" -> service.copy(functionId = ai(service.functionId))
                else -> service.copy(dataLocation = ai(service.dataLocation))
            }) + contract.services.drop(1))) + base.contracts.drop(1))
        }
    }

    private fun unzip(bytes: ByteArray): Map<String, String> = buildMap {
        ZipInputStream(ByteArrayInputStream(bytes), Charsets.UTF_8).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                put(entry.name, zip.readBytes().toString(Charsets.UTF_8))
            }
        }
    }

    private fun rows(files: Map<String, String>, name: String): List<List<String>> =
        CSVFormat.RFC4180.parse(StringReader(files.getValue(name))).use { csv -> csv.records.map { it.toList() } }

    private fun manifest(files: Map<String, String>) = DoraiJson.parseToJsonElement(files.getValue("manifest.json")).jsonObject
}
