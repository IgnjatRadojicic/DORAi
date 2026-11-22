package dorai.app.findings

import dorai.app.components.FindingSeverity
import dorai.app.components.FrontendFailure
import dorai.app.components.runImmediate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException

class FindingsViewModelTest {
    private val plan = PlanDocument("change.json", "{}".encodeToByteArray())

    @Test
    fun fileValidationPreventsCallingTheSource() {
        var calls = 0
        val model = FindingsViewModel { calls++; emptyList() }
        listOf(
            PlanDocument("plan.txt", byteArrayOf(1)),
            PlanDocument("empty.json", byteArrayOf()),
            PlanDocument("large.json", ByteArray(PlanDocument.MAX_BYTES + 1)),
        ).forEach {
            model.selectPlan(it)
            runImmediate { model.check() }
            assertNull(model.state.plan)
            assertTrue(model.state.error != null)
        }
        assertEquals(0, calls)
    }

    @Test
    fun resultsSortAndFilterWithoutChangingRuleOutput() {
        val low = ChangeFinding("LOW-RULE", FindingSeverity.LOW, "Low", "z", "Detail")
        val high = ChangeFinding("HIGH-RULE", FindingSeverity.HIGH, "High", "a", "Detail")
        val model = FindingsViewModel { listOf(low, high) }
        model.selectPlan(plan)
        runImmediate { model.check() }
        assertEquals(listOf(high, low), model.state.visible)
        model.filter(FindingSeverity.HIGH)
        assertEquals(listOf(high), model.state.visible)
        assertEquals(listOf(low, high), model.state.findings)
    }

    @Test
    fun failureClearsPreviousSuccessAndSelectionClearsOldResults() {
        var fail = false
        val model = FindingsViewModel { if (fail) throw FrontendFailure("Try again") else emptyList() }
        model.selectPlan(plan)
        runImmediate { model.check() }
        assertTrue(model.state.checked)
        fail = true
        runImmediate { model.check() }
        assertFalse(model.state.checked)
        assertEquals("Try again", model.state.error)
        model.selectPlan(plan.copy(name = "second.json"))
        assertNull(model.state.error)
        assertFalse(model.state.checked)
    }

    @Test
    fun pickerCancellationKeepsTheSelectedPlan() {
        val model = FindingsViewModel { emptyList() }
        model.selectPlan(plan)
        model.selectPlan(null)
        assertEquals(plan, model.state.plan)
    }

    @Test
    fun removingThePlanClearsTheSelectionFilterAndResults() {
        val model = FindingsViewModel { emptyList() }
        model.selectPlan(plan)
        runImmediate { model.check() }
        model.filter(FindingSeverity.HIGH)
        model.removePlan()
        assertEquals(FindingsState(), model.state)
    }

    @Test
    fun coroutineCancellationDoesNotBecomeAnError() {
        val model = FindingsViewModel { throw CancellationException("closed") }
        model.selectPlan(plan)
        kotlin.test.assertFailsWith<CancellationException> { runImmediate { model.check() } }
        assertFalse(model.state.busy)
        assertNull(model.state.error)
    }

    @Test
    fun aFileThatIsNoPlanKeepsTheLoadedPlan() {
        val model = FindingsViewModel { emptyList() }
        model.selectPlan(plan)
        runImmediate { model.check() }
        model.selectPlan(PlanDocument("notes.txt", byteArrayOf(1)))
        assertEquals(plan, model.state.plan)
        assertTrue(model.state.checked)
        assertFalse(model.state.canRetry)
    }

    @Test
    fun aPlanTheServerRefusesBringsBackTheEarlierResults() {
        val finding = ChangeFinding("R1", FindingSeverity.HIGH, "Region moved", "azurerm_x.a", "detail")
        val model = FindingsViewModel { if (it.name == "bad.json") throw FrontendFailure("Not a plan.", retryable = false) else listOf(finding) }
        model.selectPlan(plan)
        runImmediate { model.check() }
        model.selectPlan(PlanDocument("bad.json", "{}".encodeToByteArray()))
        runImmediate { model.check() }
        assertEquals(plan, model.state.plan)
        assertEquals(listOf(finding), model.state.findings)
        assertFalse(model.state.canRetry)
        assertTrue(model.state.error!!.startsWith("bad.json"))
    }
}
