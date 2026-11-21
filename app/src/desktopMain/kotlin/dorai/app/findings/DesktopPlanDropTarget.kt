package dorai.app.findings

import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.awtTransferable
import dorai.app.components.FrontendFailure
import java.awt.datatransfer.DataFlavor
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
actual fun rememberPlanDropTarget(enabled: Boolean, onPlan: (PlanDocument) -> Unit, onError: (Throwable) -> Unit): Modifier {
    val currentEnabled by rememberUpdatedState(enabled)
    val currentPlan by rememberUpdatedState(onPlan)
    val currentError by rememberUpdatedState(onError)
    val scope = rememberCoroutineScope()
    val target = remember {
        object : DragAndDropTarget {
            override fun onDrop(event: DragAndDropEvent): Boolean {
                if (!currentEnabled) return false
                val file = try {
                    (event.awtTransferable.getTransferData(DataFlavor.javaFileListFlavor) as? List<*>)?.firstOrNull() as? File
                } catch (error: Exception) { currentError(error); null } ?: return false
                scope.launch {
                    try {
                        val plan = withContext(Dispatchers.IO) {
                            val bytes = Files.newInputStream(file.toPath()).use { it.readNBytes(PlanDocument.MAX_BYTES + 1) }
                            if (bytes.size > PlanDocument.MAX_BYTES) throw FrontendFailure("The plan must be no larger than 10 MB.")
                            PlanDocument(file.name, bytes)
                        }
                        currentPlan(plan)
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (error: Exception) { currentError(error) }
                }
                return true
            }
        }
    }
    return Modifier.dragAndDropTarget(
        shouldStartDragAndDrop = { currentEnabled && it.awtTransferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor) }, target = target)
}
