package dorai.app.export

import androidx.compose.runtime.Composable
import dorai.app.findings.PlanDocument

sealed interface SaveOutcome {
    data class Saved(val location: String) : SaveOutcome
    data object Cancelled : SaveOutcome
}

interface WorkspaceFiles {
    /** Null means the user dismissed the picker. */
    suspend fun openPlan(): PlanDocument?
    suspend fun save(name: String, mediaType: String, bytes: ByteArray): SaveOutcome
}

@Composable
expect fun rememberWorkspaceFiles(): WorkspaceFiles
