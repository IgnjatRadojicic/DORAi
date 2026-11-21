package dorai.app.findings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun rememberPlanDropTarget(enabled: Boolean, onPlan: (PlanDocument) -> Unit, onError: (Throwable) -> Unit): Modifier
