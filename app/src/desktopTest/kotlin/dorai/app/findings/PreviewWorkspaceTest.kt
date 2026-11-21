package dorai.app.findings

import dorai.app.components.FindingSeverity
import dorai.app.components.FrontendFailure
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PreviewWorkspaceTest {
    @Test
    fun bundledPlanReturnsFourExamplesAndOtherPlansAreRejected() {
        val plan = PreviewWorkspace.plan()
        val findings = PreviewWorkspace.check(plan)
        assertEquals(4, findings.size)
        assertEquals(2, findings.count { it.severity == FindingSeverity.HIGH })
        assertEquals(2, findings.count { it.severity == FindingSeverity.MEDIUM })
        assertFailsWith<FrontendFailure> { PreviewWorkspace.check(PlanDocument("other.json", "{}".encodeToByteArray())) }
    }

    @Test
    fun anUploadedCopyCanContainAByteOrderMarkAndDifferentWhitespace() {
        val plan = PreviewWorkspace.plan()
        val uploaded = PlanDocument("copy.json", ("\uFEFF  \n" + plan.bytes.decodeToString() + "\n").encodeToByteArray())
        assertEquals(PreviewWorkspace.check(plan), PreviewWorkspace.check(uploaded))
    }
}
