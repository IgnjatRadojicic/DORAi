package dorai.app.export

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import dorai.app.components.FrontendFailure
import dorai.app.components.AppLanguage
import dorai.app.components.LocalAppLanguage
import dorai.app.components.UiCopy
import dorai.app.findings.PlanDocument
import java.awt.FileDialog
import java.awt.Frame
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.swing.JOptionPane
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext

@Composable
actual fun rememberWorkspaceFiles(): WorkspaceFiles {
    val language = rememberUpdatedState(LocalAppLanguage.current)
    return remember { DesktopWorkspaceFiles { language.value } }
}

class DesktopWorkspaceFiles(private val language: () -> AppLanguage = { AppLanguage.ENGLISH }) : WorkspaceFiles {
    override suspend fun openPlan(): PlanDocument? {
        val path = withContext(Dispatchers.Swing) {
            val dialog = FileDialog(null as Frame?, language().text(UiCopy.OPEN_PLAN), FileDialog.LOAD)
            try {
                dialog.file = "*.json"
                dialog.isVisible = true
                dialog.files.firstOrNull()?.toPath()
            } finally { dialog.dispose() }
        } ?: return null
        return withContext(Dispatchers.IO) {
            val bytes = Files.newInputStream(path).use { it.readNBytes(PlanDocument.MAX_BYTES + 1) }
            if (bytes.size > PlanDocument.MAX_BYTES) throw FrontendFailure("The plan must be no larger than 10 MB.")
            PlanDocument(path.fileName.toString(), bytes)
        }
    }

    override suspend fun save(name: String, mediaType: String, bytes: ByteArray): SaveOutcome {
        val path = withContext(Dispatchers.Swing) {
            val dialog = FileDialog(null as Frame?, language().text(UiCopy.SAVE_DOCUMENT), FileDialog.SAVE)
            try {
                dialog.file = name
                dialog.isVisible = true
                val chosen = dialog.files.firstOrNull()?.toPath()?.toAbsolutePath()
                val options = arrayOf(language().text(UiCopy.REPLACE), language().text(UiCopy.CANCEL))
                if (chosen != null && Files.exists(chosen) && JOptionPane.showOptionDialog(
                        null, language().format(UiCopy.REPLACE_FILE, "file" to chosen.fileName), language().text(UiCopy.CONFIRM_REPLACEMENT),
                        JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[1]
                    ) != 0) null else chosen
            } finally { dialog.dispose() }
        } ?: return SaveOutcome.Cancelled
        return withContext(Dispatchers.IO) {
            val temporary = Files.createTempFile(path.parent, ".register-", ".tmp")
            try {
                Files.write(temporary, bytes)
                try {
                    Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                } catch (_: AtomicMoveNotSupportedException) {
                    Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING)
                }
                SaveOutcome.Saved(path.toString())
            } finally { Files.deleteIfExists(temporary) }
        }
    }
}
