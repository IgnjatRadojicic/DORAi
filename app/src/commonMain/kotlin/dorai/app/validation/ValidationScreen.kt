package dorai.app.validation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dorai.app.components.*
import dorai.app.theme.ProductStyle

@Composable
fun ValidationScreen(state: ValidationState, onRefresh: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 64.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp)) {
        PageHeading("Register validation") {
            ProductButton(if (state.busy) "Validating..." else "Refresh validation", onRefresh, outlined = true, enabled = !state.busy, icon = ProductIcon.REFRESH)
        }
        ValidationMetrics(state)
        ValidationReportPanel(state, onRefresh)
    }
}

@Composable
fun ValidationMetrics(state: ValidationState) {
    state.report?.let { report ->
        MetricGrid(listOf(
            Metric("Errors", report.errorCount, "", ProductIcon.SHIELD, report.errorCount > 0),
            Metric("Warnings", report.warningCount, "", ProductIcon.ALERT, report.warningCount > 0),
            Metric("Templates", 5, "", ProductIcon.ARCHIVE),
        ))
    }
}

@Composable
fun ValidationReportPanel(state: ValidationState, onRefresh: () -> Unit, isDemo: Boolean = false) {
    ProductPanel {
        PanelHeading("", "Register validation") {
            state.report?.let { report ->
                OutlineChip(if (report.isValid) "Valid" else "Review required",
                    if (report.isValid) ProductStyle.colors.accent else ProductStyle.colors.amber, dot = true)
            }
        }
        when {
            state.busy -> LoadingView("Validating...")
            state.error != null -> Box(Modifier.padding(24.dp)) { ErrorView(state.error, onRefresh) }
            state.report == null -> EmptyView("No validation report", "")
            state.report.issues.isEmpty() -> EmptyView(
                if (state.report.isValid) "No issues" else "Review required",
                "",
                ProductIcon.CHECK,
            )
            else -> ProductTable(listOf("Status", "Template / Field", "Issue", "Reference"), listOf(0.8f, 1.5f, 3.2f, 1.6f), minWidth = 800) {
                state.report.issues.sortedBy { it.severity.ordinal }.forEach { issue ->
                    ProductTableRow {
                        Box(Modifier.weight(0.8f)) {
                            OutlineChip(if (issue.severity == IssueSeverity.ERROR) "Error" else "Warning",
                                if (issue.severity == IssueSeverity.ERROR) ProductStyle.colors.red else ProductStyle.colors.amber, dot = true)
                        }
                        Column(Modifier.weight(1.5f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlineChip(issue.template)
                            Text(if (isDemo) uiText(issue.field) else issue.field, style = MaterialTheme.typography.bodyMedium)
                        }
                        Column(Modifier.weight(3.2f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(if (isDemo) uiText(issue.message) else issue.message, style = MaterialTheme.typography.bodyMedium)
                            MetaText(issue.code)
                        }
                        Text(issue.entityId ?: uiText("Register"), modifier = Modifier.weight(1.6f), fontFamily = ProductStyle.fonts.mono,
                            fontSize = 12.sp, color = ProductStyle.colors.muted)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            MetaText(state.report?.issues?.size?.let { uiFormat(if (it == 1) UiCopy.ISSUE_COUNT_ONE else UiCopy.ISSUE_COUNT_MANY, "count" to it) } ?: "")
            if (isDemo) MetaText("Demo")
        }
    }
}
