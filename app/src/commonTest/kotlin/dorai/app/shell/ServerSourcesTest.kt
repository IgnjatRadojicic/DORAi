package dorai.app.shell

import dorai.app.components.EvidenceCitation
import dorai.app.components.FindingSeverity
import dorai.app.components.FrontendFailure
import dorai.app.components.Provenance
import dorai.app.validation.IssueSeverity
import dorai.shared.api.ApiResult
import dorai.shared.model.Citation
import dorai.shared.model.Finding
import dorai.shared.model.Problem
import dorai.shared.model.Severity
import dorai.shared.model.ValidationIssue
import dorai.shared.model.ValidationReport
import dorai.shared.model.ValidationSeverity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class ServerSourcesTest {
    @Test
    fun aFindingKeepsRuleSeverityResourceAndCitation() {
        val finding = Finding(
            ruleId = "DATA_LOCATION", severity = Severity.High, title = "Data moves outside the EU",
            resourceAddress = "azurerm_storage_account.main", templates = listOf("B_02.02"),
            detail = "eastus is outside the EU/EEA", location = "eastus", functionId = "F1",
            functionName = "Payments", criticalFunction = true,
            citation = Citation("c1", "DORA", "Art. 28", "2", "Financial entities shall...", 0.82),
            explanation = "The register lists westeurope.",
        )
        val mapped = finding.toChangeFinding()
        assertEquals("DATA_LOCATION", mapped.ruleId)
        assertEquals(FindingSeverity.HIGH, mapped.severity)
        assertEquals("azurerm_storage_account.main", mapped.resourceAddress)
        assertEquals(listOf("B_02.02"), mapped.templates)
        assertEquals(true, mapped.criticalFunction)
        assertEquals(EvidenceCitation("c1", "DORA", "Art. 28", "2", "Financial entities shall..."), mapped.citation)
        assertEquals("The register lists westeurope.", mapped.explanation)
        assertEquals(Provenance.PLANNED, mapped.provenance)
    }

    @Test
    fun everySeverityMapsToItsOwnLevel() {
        val base = Finding("R", Severity.Low, "t", "r", detail = "d")
        assertEquals(
            listOf(FindingSeverity.LOW, FindingSeverity.MEDIUM, FindingSeverity.HIGH),
            Severity.entries.map { base.copy(severity = it).toChangeFinding().severity },
        )
    }

    @Test
    fun validationKeepsCountsAndIssues() {
        val report = ValidationReport(
            isValid = false, errorCount = 1, warningCount = 1,
            issues = listOf(
                ValidationIssue(ValidationSeverity.Error, "LEI", "B_01.01", "lei", "Invalid LEI", "E1"),
                ValidationIssue(ValidationSeverity.Warning, "NAME", "B_05.01", "name", "Check name"),
            ),
        )
        val mapped = report.toRegisterValidation()
        assertFalse(mapped.isValid)
        assertEquals(1, mapped.errorCount)
        assertEquals(1, mapped.warningCount)
        assertEquals(listOf(IssueSeverity.ERROR, IssueSeverity.WARNING), mapped.issues.map { it.severity })
        assertEquals("E1", mapped.issues[0].entityId)
        assertEquals("B_05.01", mapped.issues[1].template)
    }

    @Test
    fun failuresBecomeSafeMessagesWithoutServerDetail() {
        fun failure(status: Int) = ApiResult.Failure(Problem(title = "x", status = status, detail = "secret detail"))

        val offline = assertFailsWith<FrontendFailure> { failure(0).orFail("Could not load.") }
        assertEquals("The server did not answer. Check the connection and retry.", offline.message)

        val forbidden = assertFailsWith<FrontendFailure> { failure(403).orFail("Could not load.") }
        assertEquals("Your role does not allow this action.", forbidden.message)

        val server = assertFailsWith<FrontendFailure> { failure(501).orFail("Could not load.") }
        assertEquals("Could not load. The server answered with HTTP 501.", server.message)
        assertFalse(server.message!!.contains("secret"))
    }

    @Test
    fun successReturnsTheValue() {
        assertEquals(42, ApiResult.Success(42).orFail("unused"))
    }
}
