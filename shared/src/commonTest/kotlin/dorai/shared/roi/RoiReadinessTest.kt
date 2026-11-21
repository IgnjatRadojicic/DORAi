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
        assertFalse(issues.missing("B_06.01.0070"))
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
}

class ResilienceChecksTest {
    private fun fn(rpo: String?) = Roi.register().let { r ->
        r.copy(functions = r.functions.map { f -> f.copy(roi = rpo?.let { mapOf("B_06.01.0100" to Roi.field(it)) } ?: emptyMap()) })
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
