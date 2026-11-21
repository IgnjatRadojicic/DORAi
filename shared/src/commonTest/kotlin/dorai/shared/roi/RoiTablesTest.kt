package dorai.shared.roi

import dorai.shared.model.BusinessFunction
import dorai.shared.model.ChangeAction
import dorai.shared.model.Contract
import dorai.shared.model.ContractService
import dorai.shared.model.FinancialEntity
import dorai.shared.model.Provenance
import dorai.shared.model.ProvenanceLabel
import dorai.shared.model.Provider
import dorai.shared.model.Redundancy
import dorai.shared.model.Register
import dorai.shared.model.RegisterField
import dorai.shared.model.Resource
import dorai.shared.model.ResourceSource
import dorai.shared.model.RoiRow
import dorai.shared.model.ServiceType
import dorai.shared.rules.leiFrom
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Builders shared by the RoI tests. */
internal object Roi {
    val at = Instant.parse("2026-01-01T00:00:00Z")
    val date = LocalDate(2026, 3, 31)
    val entityLei = leiFrom("TEST00FINPAY000001")
    val p1Lei = leiFrom("TEST00PROVIDER0001")

    fun field(value: String?, label: ProvenanceLabel = ProvenanceLabel.HumanConfirmed) = RegisterField(value, Provenance(label, at))

    fun service(
        id: String = "C1-S1",
        type: String = "S18",
        function: String = "F1",
        location: String? = null,
        resources: List<String> = emptyList(),
        roi: Map<String, String> = emptyMap(),
    ) = ContractService(id, field(type), field(function), field(location), resources, roi.mapValues { field(it.value) })

    fun contract(services: List<ContractService> = listOf(service()), end: LocalDate? = null, roi: Map<String, String> = emptyMap()) =
        Contract("C1", "P1", LocalDate(2025, 1, 1), end, field("40000"), "EUR", services, roi.mapValues { field(it.value) })

    fun register(
        contracts: List<Contract> = listOf(contract()),
        critical: Boolean = true,
        rows: Map<String, List<RoiRow>> = emptyMap(),
        providerRoi: Map<String, String> = emptyMap(),
    ) = Register(
        entity = FinancialEntity(entityLei, "Test Pay", "DE"),
        providers = listOf(Provider("P1", "Microsoft Ireland Operations Ltd", field(p1Lei), field("IE"), providerRoi.mapValues { field(it.value) })),
        contracts = contracts,
        functions = listOf(BusinessFunction("F1", "Payments", isCritical = critical)),
        asOf = at,
        rows = rows,
    )

    fun resource(id: String, type: String, country: String, replicas: List<String> = emptyList(), monthly: Double? = null) = Resource(
        id = id, name = id, type = type, provider = "Azure", location = "x", country = country,
        source = ResourceSource.Live, changeAction = ChangeAction.None, serviceType = ServiceType.S18,
        redundancy = Redundancy.Unknown, functionId = "F1", contractId = "C1", monthlyCost = monthly, currency = "EUR",
        replicaCountries = replicas,
    )
}

class RoiTablesTest {

    private fun build(register: dorai.shared.model.Register = Roi.register(), resources: List<Resource> = emptyList()) =
        RoiTables.build(register, resources, Roi.date)

    @Test
    fun `spec has every CIR column`() {
        val counts = RoiSpec.templates.associate { it.code to it.columns.size }
        assertEquals(
            mapOf(
                "B_01.01" to 6, "B_01.02" to 11, "B_01.03" to 4, "B_02.01" to 5, "B_02.02" to 18, "B_02.03" to 2,
                "B_03.01" to 2, "B_03.02" to 3, "B_03.03" to 2, "B_04.01" to 4, "B_05.01" to 12, "B_05.02" to 7,
                "B_06.01" to 10, "B_07.01" to 12, "B_99.01" to 4,
            ),
            counts,
        )
        assertNull(RoiSpec.column("B_06.01.0050"), "B_06.01 has no column 0050")
        assertEquals("c0100", RoiSpec.column("B_02.02.0100")!!.header)
    }

    @Test
    fun `every template is built even when it has no rows`() {
        val report = build()
        assertEquals(15, report.tables.size)
        assertTrue(report.table("B_02.03").rows.isEmpty())
        assertTrue(report.table("B_01.03").rows.isEmpty())
    }

    @Test
    fun `a single entity fills the signing, using and in-scope templates from what is stored`() {
        val report = build()
        assertEquals(Roi.entityLei, report.table("B_03.01").rows.single().value("B_03.01.0020"))
        assertEquals(Roi.p1Lei, report.table("B_03.02").rows.single().value("B_03.02.0020"))
        assertEquals("LEI", report.table("B_03.02").rows.single().value("B_03.02.0030"))
        val using = report.table("B_04.01").rows.single()
        assertEquals("2", using.value("B_04.01.0030"), "not a branch")
        val inScope = report.table("B_01.02").rows.single()
        assertEquals(RoiTables.NO_END_DATE, inScope.value("B_01.02.0090"), "not deleted")
        assertEquals("2026-01-01", inScope.value("B_01.02.0070"))
    }

    @Test
    fun `an open-ended contract reports 9999-12-31 as its end date`() {
        val row = build().table("B_02.02").rows.single()
        assertEquals("9999-12-31", row.value("B_02.02.0080"))
        assertNull(row.cells["B_02.02.0080"]!!.label, "a CIR default, not a stored value")
    }

    @Test
    fun `scan fills storage, processing and provision countries, one row per country`() {
        val register = Roi.register(listOf(Roi.contract(listOf(Roi.service(resources = listOf("st", "app"))))))
        val resources = listOf(
            Roi.resource("st", "microsoft.storage/storageaccounts", "NL", replicas = listOf("IE")),
            Roi.resource("app", "microsoft.web/sites", "NL"),
        )
        val rows = build(register, resources).table("B_02.02").rows
        assertEquals(listOf("IE", "NL"), rows.map { it.value("B_02.02.0150") })
        assertEquals(listOf("NL", "NL"), rows.map { it.value("B_02.02.0160") })
        assertEquals("true", rows.first().value("B_02.02.0140"))
        assertEquals("NL", rows.first().value("B_02.02.0130"))
        assertEquals(ProvenanceLabel.Scanned, rows.first().cells["B_02.02.0150"]!!.label)
    }

    @Test
    fun `a value a person set wins over the scan`() {
        val register = Roi.register(listOf(Roi.contract(listOf(Roi.service(resources = listOf("st"), roi = mapOf("B_02.02.0150" to "DE,FR"))))))
        val rows = build(register, listOf(Roi.resource("st", "microsoft.storage/storageaccounts", "NL"))).table("B_02.02").rows
        assertEquals(listOf("DE", "FR"), rows.map { it.value("B_02.02.0150") })
        assertEquals(ProvenanceLabel.HumanConfirmed, rows.first().cells["B_02.02.0150"]!!.label)
    }

    @Test
    fun `contract-level notice period applies to its services`() {
        val register = Roi.register(listOf(Roi.contract(roi = mapOf("B_02.02.0100" to "90"))))
        assertEquals("90", build(register).table("B_02.02").rows.single().value("B_02.02.0100"))
    }

    @Test
    fun `an AI suggestion nobody accepted is left out`() {
        val service = Roi.service().let { it.copy(roi = mapOf("B_02.02.0170" to Roi.field("3", ProvenanceLabel.AiSuggested))) }
        val row = build(Roi.register(listOf(Roi.contract(listOf(service))))).table("B_02.02").rows.single()
        assertNull(row.value("B_02.02.0170"))
    }

    @Test
    fun `provider total is the sum of its contracts and the Latin name repeats the legal name`() {
        val row = build().table("B_05.01").rows.single()
        assertEquals("40000.00", row.value("B_05.01.0100"))
        assertEquals("EUR", row.value("B_05.01.0090"))
        assertEquals("Microsoft Ireland Operations Ltd", row.value("B_05.01.0060"))
    }

    @Test
    fun `a provider that is its own ultimate parent reports the parent columns blank`() {
        val row = build(Roi.register(providerRoi = mapOf("B_05.01.0110" to Roi.p1Lei, "B_05.01.0120" to "LEI"))).table("B_05.01").rows.single()
        assertNull(row.value("B_05.01.0110"))
        assertNull(row.value("B_05.01.0120"))
    }

    @Test
    fun `supply chain has the direct provider at rank 1 and subcontractors from stored rows`() {
        val sub = RoiRow("sub1", mapOf(
            "B_05.02.0010" to Roi.field("C1"), "B_05.02.0020" to Roi.field("S18"), "B_05.02.0030" to Roi.field("X-LEI-OF-SUB"),
            "B_05.02.0050" to Roi.field("2"), "B_05.02.0060" to Roi.field("P1"),
        ))
        val rows = build(Roi.register(rows = mapOf("B_05.02" to listOf(sub)))).table("B_05.02").rows
        assertEquals(listOf("1", "2"), rows.map { it.value("B_05.02.0050") })
        assertEquals(Roi.p1Lei, rows[1].value("B_05.02.0060"), "provider id replaced by its code")
        assertEquals("LEI", rows[1].value("B_05.02.0070"))
    }

    @Test
    fun `assessment rows exist only for services supporting a critical function`() {
        assertEquals(1, build(Roi.register(critical = true)).table("B_07.01").rows.size)
        assertEquals(0, build(Roi.register(critical = false)).table("B_07.01").rows.size)
    }

    @Test
    fun `functions report criticality as a CIR option`() {
        val row = build().table("B_06.01").rows.single()
        assertEquals("1", row.value("B_06.01.0060"))
        assertEquals(Roi.entityLei, row.value("B_06.01.0040"))
    }

    @Test
    fun `definitions default to the CIR labels`() {
        val rows = build().table("B_99.01").rows
        assertTrue(rows.any { it.value("B_99.01.C0010") == "B_07.01.0050" && it.value("B_99.01.C0040") == "Not substitutable" })
    }

    @Test
    fun `annual cost falls back to the scan when nobody entered it`() {
        val c = Roi.contract(listOf(Roi.service(resources = listOf("app")))).copy(annualCost = Roi.field(null))
        val row = build(Roi.register(listOf(c)), listOf(Roi.resource("app", "microsoft.web/sites", "NL", monthly = 100.0))).table("B_02.01").rows.single()
        assertEquals("1200.00", row.value("B_02.01.0050"))
        assertEquals(ProvenanceLabel.Scanned, row.cells["B_02.01.0050"]!!.label)
    }
}
