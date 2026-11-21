package dorai.app.export

import dorai.app.findings.PlanDocument
import dorai.app.components.runImmediate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExportViewModelTest {
    private class FilesFake : WorkspaceFiles {
        var saves = 0
        var outcome: SaveOutcome = SaveOutcome.Saved("selected/report.pdf")
        var failure: Exception? = null
        override suspend fun openPlan(): PlanDocument? = null
        override suspend fun save(name: String, mediaType: String, bytes: ByteArray): SaveOutcome {
            saves++
            failure?.let { throw it }
            return outcome
        }
    }

    @Test
    fun aSuccessfulDownloadIsOnlySuccessfulAfterSaving() {
        val files = FilesFake()
        val model = ExportViewModel({ ExportDocument("%PDF-1.7".encodeToByteArray(), "application/pdf") }, files)
        runImmediate { model.download(ExportFormat.PDF) }
        assertEquals("selected/report.pdf", model.state.savedLocation)
        files.outcome = SaveOutcome.Cancelled
        runImmediate { model.download(ExportFormat.PDF) }
        assertTrue(model.state.cancelled)
        assertNull(model.state.savedLocation)
        assertNull(model.state.error)
    }

    @Test
    fun htmlEmptyAndWrongSignatureResponsesAreNeverSaved() {
        val files = FilesFake()
        listOf(
            ExportDocument("<html>login</html>".encodeToByteArray(), "text/html"),
            ExportDocument(byteArrayOf(), "application/pdf"),
            ExportDocument("not a report".encodeToByteArray(), "application/octet-stream"),
        ).forEach { document ->
            val model = ExportViewModel({ document }, files)
            runImmediate { model.download(ExportFormat.PDF) }
            assertTrue(model.state.error != null)
            assertNull(model.state.savedLocation)
        }
        assertEquals(0, files.saves)
    }

    @Test
    fun csvIsSavedAsAnArchiveAndSupportsMimeParameters() {
        val files = FilesFake()
        val document = ExportDocument(byteArrayOf(0x50, 0x4B, 3, 4, 1), "application/zip; charset=binary")
        val model = ExportViewModel({ document }, files)
        runImmediate { model.download(ExportFormat.CSV) }
        assertEquals(1, files.saves)
        assertNull(model.state.error)
    }

    @Test
    fun deniedRoleDoesNotDownloadOrOpenASaveDialog() {
        val files = FilesFake()
        var downloads = 0
        val model = ExportViewModel({ downloads++; error("Must not be called") }, files, canExport = { false })
        runImmediate { model.download(ExportFormat.PDF) }
        assertEquals(0, downloads)
        assertEquals(0, files.saves)
        assertTrue(model.state.error != null)
    }

    @Test
    fun diskFailuresClearPreviousSuccess() {
        val files = FilesFake()
        val model = ExportViewModel({ ExportDocument("%PDF-1.7".encodeToByteArray(), "application/pdf") }, files)
        runImmediate { model.download(ExportFormat.PDF) }
        files.failure = IllegalStateException("private path")
        runImmediate { model.download(ExportFormat.PDF) }
        assertNull(model.state.savedLocation)
        assertNull(model.state.busy)
        assertTrue(model.state.error != null)
    }
}
