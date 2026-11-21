package dorai.shared.roi

import dorai.shared.model.ProvenanceLabel
import dorai.shared.model.Register
import dorai.shared.model.RoiRow
import dorai.shared.model.ValidationIssue
import dorai.shared.model.ValidationSeverity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RoiReadinessTest {

    private fun check(register: Register = Roi.register()): List<ValidationIssue> =
        RoiReadiness.check(RoiTables.build(register, emptyList(), Roi.date), register)

    private fun List<ValidationIssue>.missing(code: String) = any { it.code == "roi-missing" && it.field == code }

    @Test
    fun `critical service needs notice periods, governing law and reliance`() {
        val issues = check()
        for (c in listOf("B_02.02.0100", "B_02.02.0110", "B_02.02.0120", "B_02.02.0180")) assertTrue(issues.missing(c), c)
    }

    @Test
    fun `non-critical service does not`() {
        val issues = check(Roi.register(critical = false))
        for (c in listOf("B_02.02.0100", "B_02.02.0110", "B_02.02.0120", "B_02.02.0180", "B_02.02.0140")) assertFalse(issues.missing(c), c)
    }

    @Test
    fun `sensitivity is needed only when the critical service stores data`() {
        val stores = Roi.register(listOf(Roi.contract(listOf(Roi.service(roi = mapOf("B_02.02.0140" to "true", "B_02.02.0150" to "DE"))))))
        assertTrue(check(stores).missing("B_02.02.0170"))
        val doesNot = Roi.register(listOf(Roi.contract(listOf(Roi.service(roi = mapOf("B_02.02.0140" to "false"))))))
        assertFalse(check(doesNot).missing("B_02.02.0170"))
        assertFalse(check(doesNot).missing("B_02.02.0150"))
    }

    @Test
    fun `termination reason is needed only for an ended contract`() {
        assertFalse(check().missing("B_02.02.0090"))
        val ended = Roi.register(listOf(Roi.contract(end = kotlinx.datetime.LocalDate(2025, 12, 31))))
        assertTrue(check(ended).missing("B_02.02.0090"))
    }

    @Test
    fun `optional columns are never missing`() {
        val issues = check()
        assertFalse(issues.missing("B_06.01.0060"))
        assertFalse(issues.missing("B_07.01.0120"))
        assertFalse(issues.missing("B_05.01.0030"))
    }

    @Test
    fun `wrong formats are errors`() {
        val bad = Roi.register(listOf(Roi.contract(listOf(Roi.service(roi = mapOf("B_02.02.0100" to "ninety", "B_02.02.0120" to "Germany", "B_02.02.0180" to "9"))))))
        val formats = check(bad).filter { it.code == "roi-format" }.map { it.field }.toSet()
        assertEquals(setOf("B_02.02.0100", "B_02.02.0120", "B_02.02.0180"), formats)
    }

    @Test
    fun `function ids must be F and a number`() {
        val r = Roi.register().let { it.copy(functions = it.functions.map { f -> f.copy(id = "Payments") }) }
        assertTrue(check(r).any { it.code == "roi-format" && it.field == "B_06.01.0010" })
    }

    @Test
    fun `references to unknown contracts and providers are errors`() {
        val row = RoiRow("x", mapOf("B_05.02.0010" to Roi.field("C9"), "B_05.02.0020" to Roi.field("S18"), "B_05.02.0030" to Roi.field("P1"), "B_05.02.0050" to Roi.field("2"), "B_05.02.0060" to Roi.field("P7")))
        val issues = check(Roi.register(rows = mapOf("B_05.02" to listOf(row))))
        assertTrue(issues.any { it.code == "roi-unknown-reference" && it.field == "B_05.02.0010" })
        assertTrue(issues.any { it.code == "roi-unknown-reference" && it.field == "B_05.02.0060" })
    }

    @Test
    fun `a subcontractor's recipient must sit one rank up`() {
        val row = RoiRow("x", mapOf("B_05.02.0010" to Roi.field("C1"), "B_05.02.0020" to Roi.field("S18"), "B_05.02.0030" to Roi.field("P1"), "B_05.02.0050" to Roi.field("3"), "B_05.02.0060" to Roi.field("P1")))
        assertTrue(check(Roi.register(rows = mapOf("B_05.02" to listOf(row)))).any { it.code == "roi-broken-chain" })
    }

    @Test
    fun `legal persons need LEI or EUID`() {
        val r = Roi.register(providerRoi = mapOf("B_05.01.0010" to "IE123456", "B_05.01.0020" to "IE_VAT", "B_05.01.0070" to "1"))
        assertTrue(check(r).any { it.code == "roi-legal-person-code" })
    }

    @Test
    fun `unaccepted AI values are warnings, not export values`() {
        val s = Roi.service().copy(roi = mapOf("B_02.02.0180" to Roi.field("4", ProvenanceLabel.AiSuggested)))
        val issues = check(Roi.register(listOf(Roi.contract(listOf(s)))))
        assertTrue(issues.any { it.code == "roi-unconfirmed" && it.severity == ValidationSeverity.Warning })
        assertTrue(issues.missing("B_02.02.0180"))
    }

    @Test
    fun `default definitions are a warning`() {
        assertTrue(check().any { it.code == "default-definitions" && it.severity == ValidationSeverity.Warning })
    }

    @Test
    fun `two services that are one RoI row by the taxonomy key must agree`() {
        val place = mapOf("B_02.02.0130" to "DE", "B_02.02.0140" to "true", "B_02.02.0150" to "DE", "B_02.02.0160" to "DE")
        fun twoServices(secondReliance: String) = Roi.register(listOf(Roi.contract(listOf(
            Roi.service("C1-S1", roi = place + ("B_02.02.0180" to "4")),
            Roi.service("C1-S2", roi = place + ("B_02.02.0180" to secondReliance)),
        ))))
        assertTrue(check(twoServices("2")).any { it.code == "roi-duplicate-key" && it.template == "B_02.02" })
        assertTrue(check(twoServices("4")).none { it.code == "roi-duplicate-key" })
    }
}

class ResilienceChecksTest {
    private fun fn(rpo: String?) = Roi.register().let { r ->
        r.copy(functions = r.functions.map { f -> f.copy(roi = rpo?.let { mapOf("B_06.01.0090" to Roi.field(it)) } ?: emptyMap()) })
    }

    private fun db(backup: dorai.shared.model.BackupInfo?, redundancy: dorai.shared.model.Redundancy = dorai.shared.model.Redundancy.Zone) =
        Roi.resource("db", "microsoft.dbforpostgresql/flexibleservers", "NL").copy(backup = backup, redundancy = redundancy)

    @Test
    fun `backups less frequent than the declared RPO are flagged`() {
        val issues = ResilienceChecks.check(fn("1"), listOf(db(dorai.shared.model.BackupInfo(intervalHours = 24))))
        assertTrue(issues.any { it.code == "art12-rpo-not-met" })
    }

    @Test
    fun `no backup at all is flagged when an RPO is declared`() {
        assertTrue(ResilienceChecks.check(fn("4"), listOf(db(null))).any { it.code == "art12-no-backup" })
    }

    @Test
    fun `a matching backup and a zone-redundant store raise nothing`() {
        assertTrue(ResilienceChecks.check(fn("1"), listOf(db(dorai.shared.model.BackupInfo(intervalHours = 1)))).isEmpty())
    }

    @Test
    fun `single-location data of a critical function is flagged`() {
        val issues = ResilienceChecks.check(fn(null), listOf(db(null, dorai.shared.model.Redundancy.Local)))
        assertTrue(issues.any { it.code == "art12-single-location" })
    }
}

class VendorChecksTest {
    @Test
    fun `a paid seller missing from the register is a warning`() {
        val issues = VendorChecks.check(Roi.register(), listOf(VendorSpend("Elastic N.V.", "AWS", 1200.0, "EUR"), VendorSpend("Elastic N.V.", "AWS", 300.0, "EUR")))
        val issue = issues.single()
        assertEquals("unregistered-vendor" to ValidationSeverity.Warning, issue.code to issue.severity)
        assertTrue("1500.00 EUR" in issue.message, issue.message)
    }

    @Test
    fun `a registered provider matches despite legal form and punctuation`() {
        assertTrue(VendorChecks.check(Roi.register(), listOf(VendorSpend("Microsoft Ireland Operations, Limited", "Azure", 10.0, "EUR"))).isEmpty())
    }
}

class SaasAppChecksTest {
    private fun app(name: String, publisher: String) = Roi.resource(name, "saas/app", "IE").copy(
        serviceType = dorai.shared.model.ServiceType.S19, tags = mapOf(VendorChecks.PUBLISHER_TAG to publisher),
    )

    @Test
    fun `apps from an unregistered publisher are one warning per publisher`() {
        val issues = VendorChecks.checkApps(Roi.register(), listOf(app("Slack", "Slack Technologies"), app("Slack Huddles", "Slack Technologies"), app("Teams", "Microsoft Ireland Operations")))
        val issue = issues.single()
        assertEquals("unregistered-saas", issue.code)
        assertTrue("Slack, Slack Huddles" in issue.message, issue.message)
    }
}

class ConcentrationChecksTest {
    private val parent = dorai.shared.rules.leiFrom("TEST00PARENT000001")

    private fun twoFromOneGroup(): Register {
        val base = Roi.register(providerRoi = mapOf("B_05.01.0110" to parent, "B_05.01.0120" to "LEI"))
        val p2 = base.providers.single().copy(id = "P2", name = "Microsoft Germany GmbH", lei = Roi.field(dorai.shared.rules.leiFrom("TEST00PROVIDER0002")))
        val c2 = Roi.contract(listOf(Roi.service(id = "C2-S1"))).copy(id = "C2", providerId = "P2")
        return base.copy(providers = base.providers + p2, contracts = base.contracts + c2)
    }

    private fun check(r: Register) = ConcentrationChecks.check(RoiTables.build(r, emptyList(), Roi.date), r)

    @Test
    fun `providers of one group behind a critical function are flagged`() {
        val issue = check(twoFromOneGroup()).single { it.code == "art29-same-group" }
        assertTrue("Microsoft Germany GmbH" in issue.message && parent in issue.message, issue.message)
    }

    @Test
    fun `a single provider is not a group concentration`() {
        assertTrue(check(Roi.register()).none { it.code.startsWith("art29") })
    }

    @Test
    fun `a hard to substitute contract without exit plan or alternative is flagged`() {
        val c = Roi.contract(listOf(Roi.service(roi = mapOf("B_07.01.0050" to "1", "B_07.01.0080" to "false", "B_07.01.0110" to "2"))))
        val codes = check(Roi.register(listOf(c))).map { it.code }.toSet()
        assertTrue("art28-no-exit-plan" in codes && "art28-no-alternative" in codes, codes.toString())
    }
}

class SecurityChecksTest {
    private fun db(vararg severities: String) = Roi.resource("db", "microsoft.dbforpostgresql/flexibleservers", "NL")
        .copy(alerts = severities.mapIndexed { i, s -> dorai.shared.model.SecurityAlert("Defender for Cloud", "a$i", "Finding $i", s) })

    @Test
    fun `open high findings on a critical function's resource are a warning`() {
        val issue = SecurityChecks.check(Roi.register(), listOf(db("HIGH", "LOW", "CRITICAL"))).single()
        assertEquals("ict-open-security-findings" to ValidationSeverity.Warning, issue.code to issue.severity)
        assertTrue("2 open high or critical findings in Defender for Cloud" in issue.message, issue.message)
    }

    @Test
    fun `low findings and non-critical functions raise nothing`() {
        assertTrue(SecurityChecks.check(Roi.register(), listOf(db("LOW", "MEDIUM"))).isEmpty())
        assertTrue(SecurityChecks.check(Roi.register(critical = false), listOf(db("HIGH"))).isEmpty())
    }
}
