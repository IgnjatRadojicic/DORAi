package dorai.app.validation

import dorai.app.components.runImmediate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ValidationViewModelTest {
    @Test
    fun refreshFailureRemovesThePreviouslyValidReport() {
        var fail = false
        val model = ValidationViewModel {
            if (fail) error("Internal server detail")
            RegisterValidation(true, 0, 0, emptyList())
        }
        runImmediate { model.refresh() }
        assertTrue(model.state.report!!.isValid)
        fail = true
        runImmediate { model.refresh() }
        assertNull(model.state.report)
        assertFalse(model.state.busy)
        assertTrue(model.state.error != null)
        assertFalse(model.state.error!!.contains("Internal server detail"))
    }

    @Test
    fun issuesAreGroupedByTemplateWithErrorsFirst() {
        val warning = RegisterIssue(IssueSeverity.WARNING, "WARN", "B_01.01", "name", "Check name")
        val error = RegisterIssue(IssueSeverity.ERROR, "LEI", "B_01.01", "lei", "Invalid identifier")
        val reference = RegisterIssue(IssueSeverity.ERROR, "LINK", "B_02.02", "contract", "Missing reference")
        val report = RegisterValidation(false, 2, 1, listOf(warning, reference, error))
        assertEquals(listOf(error, warning), report.groups["B_01.01"])
        assertEquals(listOf(reference), report.groups["B_02.02"])
    }
}
