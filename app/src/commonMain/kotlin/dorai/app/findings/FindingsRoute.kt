package dorai.app.findings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dorai.app.export.WorkspaceFiles
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** The shell owns the ViewModel lifecycle and the shared-client adapter. */
@Composable
fun FindingsRoute(model: FindingsViewModel, files: WorkspaceFiles, modifier: Modifier = Modifier, canCheck: Boolean = true,
    onLoadDemo: (() -> Unit)? = null, isDemo: Boolean = false) {
    val scope = rememberCoroutineScope()
    var choosing by remember { mutableStateOf(false) }
    val dropTarget = rememberPlanDropTarget(!choosing && !model.state.busy && canCheck, model::selectPlan, model::showFileError)
    FindingsScreen(
        state = model.state.copy(busy = model.state.busy || choosing),
        onChoosePlan = {
            if (!choosing && canCheck) scope.launch {
                choosing = true
                try {
                    model.selectPlan(files.openPlan())
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    model.showFileError(error)
                } finally { choosing = false }
            }
        },
        onCheck = { if (canCheck && !choosing) scope.launch { model.check() } },
        onFilter = model::filter,
        modifier = modifier,
        canCheck = canCheck,
        onRemovePlan = model::removePlan,
        onLoadDemo = onLoadDemo,
        isDemo = isDemo,
        dropModifier = dropTarget,
    )
}
