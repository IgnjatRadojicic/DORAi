package dorai.app.roi

import dorai.app.components.runImmediate
import dorai.shared.model.ProvenanceLabel
import dorai.shared.roi.RoiCell
import dorai.shared.roi.RoiItems
import dorai.shared.roi.RoiRowOut
import dorai.shared.roi.RoiView
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RoiViewModelTest {

    private class FakeSource(var reliance: RoiCell?, val keepsOldValue: Boolean = false) : RoiSource {
        val writes = mutableListOf<Triple<RoiItems.Target, String, String?>>()
        override suspend fun load() = RoiView(
            LocalDate(2026, 12, 31), ready = false, errorCount = 0, warningCount = 0,
            tables = mapOf(
                "B_02.02" to listOf(RoiRowOut("C1-S1#1", buildMap {
                    put("B_02.02.0010", RoiCell("C1", ProvenanceLabel.HumanConfirmed))
                    put("B_02.02.0150", RoiCell("NL", ProvenanceLabel.Scanned))
                    reliance?.let { put("B_02.02.0180", it) }
                })),
                "B_04.01" to listOf(RoiRowOut("C1|LEI", emptyMap())),
            ),
            issues = emptyList(),
        )
        override suspend fun setValue(target: RoiItems.Target, column: String, value: String?) {
            writes += Triple(target, column, value)
            if (!keepsOldValue) reliance = value?.let { RoiCell(it, ProvenanceLabel.HumanConfirmed) }
        }
    }

    @Test
    fun scannedAndDerivedCellsAreNotEditableButEmptyAndPersonValuesAre() {
        val model = RoiViewModel(FakeSource(null), canEdit = { true })
        runImmediate { model.refresh() }
        assertTrue(model.editable("B_02.02", "C1-S1#1", "B_02.02.0180"))
        assertTrue(model.editable("B_02.02", "C1-S1#1", "B_02.02.0010"))
        assertFalse(model.editable("B_02.02", "C1-S1#1", "B_02.02.0150"))
        assertFalse(model.editable("B_04.01", "C1|LEI", "B_04.01.0020"))
        assertFalse(RoiViewModel(FakeSource(null), canEdit = { false }).also { runImmediate { it.refresh() } }.editable("B_02.02", "C1-S1#1", "B_02.02.0180"))
    }

    @Test
    fun savingWritesToTheServiceAndReloads() {
        val source = FakeSource(null)
        val model = RoiViewModel(source, canEdit = { true })
        runImmediate { model.refresh() }
        model.startEdit("C1-S1#1", "B_02.02.0180")
        model.type("4")
        runImmediate { model.save() }
        assertEquals(Triple(RoiItems.Target.Item("C1-S1"), "B_02.02.0180", "4"), source.writes.single())
        assertNull(model.state.edit)
        assertEquals("4", model.state.view!!.tables.getValue("B_02.02").single().cells.getValue("B_02.02.0180").value)
        assertEquals("Saved B_02.02.0180.", model.state.message)
    }

    @Test
    fun aValueTheRegisterItemOverridesIsReported() {
        val model = RoiViewModel(FakeSource(RoiCell("2", ProvenanceLabel.HumanConfirmed), keepsOldValue = true), canEdit = { true })
        runImmediate { model.refresh() }
        model.startEdit("C1-S1#1", "B_02.02.0180")
        model.type("4")
        runImmediate { model.save() }
        assertTrue(model.state.message!!.contains("still shows"))
    }

    @Test
    fun templateStatusCoversAllFifteenTemplates() {
        val model = RoiViewModel(FakeSource(null), canEdit = { true })
        runImmediate { model.refresh() }
        assertEquals(15, model.state.templates.size)
        assertEquals(1, model.state.templates.single { it.code == "B_02.02" }.rows)
    }
}
