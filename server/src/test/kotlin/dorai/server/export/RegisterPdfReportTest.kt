package dorai.server.export

import dorai.server.core.AiAnswer
import dorai.server.core.DraftText
import dorai.server.core.FindingNote
import dorai.server.core.ReportDrafter
import dorai.server.fixtures.FixtureData
import dorai.shared.model.Citation
import dorai.shared.model.Finding
import dorai.shared.model.ProvenanceLabel
import dorai.shared.model.Register
import dorai.shared.rules.RulesPlanChecker
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import kotlin.test.*

class RegisterPdfReportTest {
    private val data = FixtureData.load()
    private val register = data.register
    private val reference = Citation("reg-1", "DORA", "Article 28", "1", "Financial entities shall manage ICT third-party risk.", 0.9)

    @Test
    fun reportContainsRegisterValidationAndEveryOriginalRuleFinding() = runTest {
        val findings = RulesPlanChecker().check(data.planEastus.decodeToString(), register)
        val bytes = RegisterPdfReport().generate(register, findings)
        assertTrue(bytes.decodeToString(0, 5) == "%PDF-")
        Loader.loadPDF(bytes).use { pdf ->
            assertTrue(pdf.numberOfPages >= 3)
            assertEquals("DORAi | ICT register review", pdf.documentInformation.title)
            val text = PDFTextStripper().getText(pdf)
            assertContains(text, register.entity.name)
            assertContains(text, "Basic validation: passed")
            assertContains(text, "AI summary unavailable")
            findings.forEach { finding ->
                assertContains(text, finding.ruleId)
                assertContains(text, finding.resourceAddress)
                assertContains(text.replace(Regex("\\s+"), " "), finding.detail.replace(Regex("\\s+"), " "))
                assertContains(text, finding.severity.name.uppercase())
            }
            for (number in 1..pdf.numberOfPages) assertContains(text, "Page $number")
            pdf.pages.forEach { page ->
                val fonts = page.resources.fontNames.map { page.resources.getFont(it) }
                assertTrue(fonts.isNotEmpty())
                assertTrue(fonts.all { it.isEmbedded }, "Reports must not depend on installed fonts")
            }
        }
    }

    @Test
    fun validationErrorsRemainVisibleInADiagnosticReport() = runTest {
        val invalid = register.copy(entity = register.entity.copy(name = ""))
        val text = text(RegisterPdfReport().generate(invalid, emptyList()))
        assertContains(text, "Basic validation: action required")
        assertContains(text, "Annual register expense: Not available")
        assertContains(text, "ERROR")
    }

    @Test
    fun unconfirmedValuesAndTheirReasonsAreOmittedFromAmountsAndAppendix() = runTest {
        val first = register.contracts.first()
        val pending = first.annualCost.copy(value = "987654321.123", provenance = first.annualCost.provenance.copy(
            label = ProvenanceLabel.AiSuggested, reason = "SECRET PROPOSED COST 987654321.123"))
        val changed = register.copy(contracts = listOf(first.copy(annualCost = pending)) + register.contracts.drop(1))
        val text = text(RegisterPdfReport().generate(changed, emptyList()))
        assertFalse(text.contains("987654321.123"))
        assertFalse(text.contains("SECRET PROPOSED COST"))
        assertContains(text, "AI-suggested / omitted")
        assertContains(text, "Annual register expense: Not available")
        assertContains(text, "Fields awaiting human confirmation")
    }

    @Test
    fun validationMessagesDoNotExposeAnUnconfirmedFunctionValue() = runTest {
        val contract = register.contracts.first()
        val service = contract.services.first()
        val field = service.functionId.copy(value = "PRIVATE_FUNCTION_PROPOSAL", provenance = service.functionId.provenance.copy(label = ProvenanceLabel.AiSuggested))
        val changed = register.copy(contracts = listOf(contract.copy(services = listOf(service.copy(functionId = field)) + contract.services.drop(1))) + register.contracts.drop(1))
        val text = text(RegisterPdfReport().generate(changed, emptyList()))
        assertFalse(text.contains("PRIVATE_FUNCTION_PROPOSAL"))
        assertContains(text, "Proposed value omitted.")
    }

    @Test
    fun missingMixedNegativeAndZeroCostsNeverProduceMisleadingShares() = runTest {
        val first = register.contracts.first()
        val cases = listOf(
            register.copy(contracts = listOf(first.copy(annualCost = first.annualCost.copy(value = null))) + register.contracts.drop(1)),
            register.copy(contracts = listOf(first.copy(currency = "USD")) + register.contracts.drop(1)),
            register.copy(contracts = listOf(first.copy(annualCost = first.annualCost.copy(value = "-1"))) + register.contracts.drop(1)),
        )
        cases.forEach { changed ->
            val text = text(RegisterPdfReport().generate(changed, emptyList()))
            assertContains(text, "Annual register expense: Not available")
            assertFalse(text.contains("100.00%"))
        }
        val zero = register.copy(contracts = register.contracts.map { it.copy(annualCost = it.annualCost.copy(value = "0")) })
        val text = text(RegisterPdfReport().generate(zero, emptyList()))
        assertContains(text, "Annual register expense: 0 EUR")
        assertFalse(text.contains("NaN"))
        assertFalse(text.contains("Infinity"))
    }

    @Test
    fun decimalAmountsDoNotDependOnMachineLocale() = runTest {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            val changed = register.copy(contracts = register.contracts.mapIndexed { index, contract ->
                contract.copy(annualCost = contract.annualCost.copy(value = if (index == 0) "1234.56" else "10.25"), currency = "EUR")
            })
            assertContains(text(RegisterPdfReport().generate(changed, emptyList())), "Annual register expense: 1244.81 EUR")
        } finally { Locale.setDefault(previous) }
    }

    @Test
    fun multilingualTextSurvivesEmbeddingAndExtraction() = runTest {
        val name = "Čačak / España / Société / München"
        val changed = register.copy(entity = register.entity.copy(name = name))
        assertContains(text(RegisterPdfReport().generate(changed, emptyList())), name)
    }

    @Test
    fun citedDraftIsLabeledAndCannotReplaceTheRuleFinding() = runTest {
        val finding = RulesPlanChecker().check(data.planEastus.decodeToString(), register).first()
        val answer = AiAnswer.Answer(DraftText("Cited summary for review.", listOf(
            FindingNote(finding.ruleId, finding.resourceAddress, "Review the recorded data location."))), "Cited", listOf(reference))
        val report = RegisterPdfReport(drafter { _, _ -> answer })
        val text = text(report.generate(register, listOf(finding)))
        assertContains(text, "AI-suggested summary / awaiting human confirmation")
        assertContains(text, answer.value.summary)
        assertContains(text, finding.detail)
        assertContains(text, finding.severity.name.uppercase())
        assertContains(text, "AI-suggested note: Review the recorded data location.")
        assertContains(text, reference.excerpt)
    }

    @Test
    fun failedInsufficientTimedOutOrMalformedDraftDoesNotPreventExport() = runTest {
        val answers = listOf(
            AiAnswer.Failed("PRIVATE MODEL ERROR"), AiAnswer.InsufficientEvidence("PRIVATE SEARCH ERROR"),
            AiAnswer.Answer(DraftText("UNTRUSTED SUMMARY", listOf(FindingNote("invented", "resource", "invented"))), "", listOf(reference)),
            AiAnswer.Answer(DraftText("UNTRUSTED SUMMARY", emptyList()), "", listOf(reference.copy(excerpt = ""))),
        )
        answers.forEach { answer ->
            val text = text(RegisterPdfReport(drafter { _, _ -> answer }).generate(register, emptyList()))
            assertContains(text, "AI summary unavailable")
            assertFalse(text.contains("PRIVATE"))
            assertFalse(text.contains("UNTRUSTED SUMMARY"))
        }
        val throws = RegisterPdfReport(drafter { _, _ -> error("PRIVATE EXCEPTION") })
        assertContains(text(throws.generate(register, emptyList())), "AI summary unavailable")
        val timesOut = RegisterPdfReport(drafter { _, _ -> awaitCancellation() }, draftTimeoutMillis = 1)
        assertContains(text(timesOut.generate(register, emptyList())), "AI summary unavailable")
    }

    @Test
    fun parentCancellationIsPropagated() = runTest {
        val report = RegisterPdfReport(drafter { _, _ -> throw CancellationException("cancelled") })
        assertFailsWith<CancellationException> { report.generate(register, emptyList()) }
    }

    @Test
    fun emptyRegisterAndLongTablesPaginateWithoutLosingRows() = runTest {
        val empty = register.copy(providers = emptyList(), contracts = emptyList(), functions = emptyList())
        assertContains(text(RegisterPdfReport().generate(empty, emptyList())), "No entries recorded")
        val many = register.copy(functions = (1..120).map { index ->
            register.functions.first().copy(id = "function-$index", name = "Treasury processing " + "review ".repeat(12) + index)
        })
        val bytes = RegisterPdfReport().generate(many, emptyList())
        Loader.loadPDF(bytes).use { pdf ->
            assertTrue(pdf.numberOfPages > 4)
            val text = PDFTextStripper().getText(pdf)
            assertContains(text, "function-120")
            assertContains(text, "Sources and methodology")
        }
    }

    private fun text(bytes: ByteArray): String = Loader.loadPDF(bytes).use { PDFTextStripper().getText(it) }
    private fun drafter(block: suspend (Register, List<Finding>) -> AiAnswer<DraftText>) = object : ReportDrafter {
        override suspend fun draft(register: Register, findings: List<Finding>) = block(register, findings)
    }
}
