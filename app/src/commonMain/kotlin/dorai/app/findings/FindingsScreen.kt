package dorai.app.findings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dorai.app.components.*
import dorai.app.theme.ProductStyle

@Composable
fun FindingsScreen(
    state: FindingsState, onChoosePlan: () -> Unit, onCheck: () -> Unit,
    onFilter: (FindingSeverity?) -> Unit, modifier: Modifier = Modifier,
    canCheck: Boolean = true, onRemovePlan: () -> Unit = {},
    onLoadDemo: (() -> Unit)? = null, isDemo: Boolean = false,
    dropModifier: Modifier = Modifier,
) {
    val removeLabel = uiText(UiCopy.REMOVE_PLAN)
    val selectLabel = uiText(UiCopy.SELECT_PLAN)
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        PageHeading("Change findings") {
            ProductButton(if (state.busy) "Checking..." else "Check plan", onCheck,
                enabled = state.plan != null && !state.busy && canCheck, icon = if (state.busy) ProductIcon.REFRESH else ProductIcon.SHIELD)
        }
        ProductPanel {
            PanelHeading("", "Terraform plan") { OutlineChip("JSON / 10 MB") }
            BoxWithConstraints(Modifier.padding(18.dp)) {
                val actions: @Composable () -> Unit = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ProductButton("Browse...", onChoosePlan, outlined = true, enabled = !state.busy && canCheck)
                        if (onLoadDemo != null) ProductButton("Sample plan", onLoadDemo, outlined = true, enabled = !state.busy && canCheck, icon = ProductIcon.FILE)
                    }
                }
                val fileTile: @Composable (Modifier) -> Unit = { tileModifier ->
                    val borderColor = ProductStyle.colors.line
                    Row(tileModifier.then(dropModifier).drawBehind {
                        if (state.plan == null) drawRoundRect(borderColor,
                            style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))))
                    }.actionPointer(!state.busy && canCheck)
                        .clickable(enabled = !state.busy && canCheck, role = Role.Button, onClickLabel = selectLabel, onClick = onChoosePlan)
                        .padding(12.dp).heightIn(min = 36.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        LineIcon(if (state.plan == null) ProductIcon.UPLOAD else ProductIcon.FILE, color = ProductStyle.colors.accent, size = 22.dp)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(state.plan?.name ?: selectLabel, style = MaterialTheme.typography.titleSmall)
                            state.plan?.let { plan -> MetaText(((plan.bytes.size / 1024.0 * 10).toInt() / 10.0).toString() + " KB") }
                        }
                        if (state.plan != null) IconButton(onClick = onRemovePlan, enabled = !state.busy,
                            modifier = Modifier.actionPointer(!state.busy).semantics { contentDescription = removeLabel }) { LineIcon(ProductIcon.CLOSE) }
                    }
                }
                if (maxWidth >= 700.dp) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    fileTile(Modifier.weight(1f))
                    actions()
                } else Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    fileTile(Modifier.fillMaxWidth())
                    actions()
                }
            }
        }
        state.error?.let { ErrorView(it, if (state.plan != null && canCheck) onCheck else null) }
        ExpandDetails(state.checked && !state.busy) {
            MetricGrid(listOf(
                Metric("All findings", state.findings.size, "", ProductIcon.FILE, selected = state.filter == null, onClick = { onFilter(null) }),
                Metric("High", state.findings.count { it.severity == FindingSeverity.HIGH }, "", ProductIcon.SHIELD, state.findings.any { it.severity == FindingSeverity.HIGH }, selected = state.filter == FindingSeverity.HIGH, onClick = { onFilter(FindingSeverity.HIGH) }),
                Metric("Medium", state.findings.count { it.severity == FindingSeverity.MEDIUM }, "", ProductIcon.ALERT, selected = state.filter == FindingSeverity.MEDIUM, onClick = { onFilter(FindingSeverity.MEDIUM) }),
                Metric("Low", state.findings.count { it.severity == FindingSeverity.LOW }, "", ProductIcon.SHIELD, selected = state.filter == FindingSeverity.LOW, onClick = { onFilter(FindingSeverity.LOW) }),
            ))
        }
        ProductPanel {
            PanelHeading("", "Findings") { SeverityFilter(state.filter, state.checked && !state.busy, onFilter) }
            when {
                state.busy -> LoadingView("Checking...")
                !state.checked -> EmptyView("No results", "")
                state.findings.isEmpty() -> EmptyView("No findings", "", ProductIcon.SHIELD)
                state.visible.isEmpty() -> EmptyView("No matching findings", "")
                else -> FindingsTable(state.visible, isDemo)
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                MetaText(if (state.checked) uiFormat(if (state.findings.size == 1) UiCopy.FINDING_COUNT_ONE else UiCopy.FINDING_COUNT_MANY,
                    "shown" to state.visible.size, "total" to state.findings.size) else "")
                if (isDemo) MetaText("Demo")
            }
        }
        if (!canCheck) Text(uiText("Read-only access"), color = ProductStyle.colors.muted)
    }
}

@Composable
private fun SeverityFilter(selected: FindingSeverity?, enabled: Boolean, onFilter: (FindingSeverity?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LineIcon(ProductIcon.FILTER, size = 15.dp)
        Box {
            ProductButton(selected?.name?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "All severities", { open = true }, outlined = true, enabled = enabled, icon = ProductIcon.CHEVRON)
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                DropdownMenuItem(text = { Text(uiText("All severities")) }, onClick = { onFilter(null); open = false }, modifier = Modifier.actionPointer())
                FindingSeverity.entries.forEach { severity ->
                    DropdownMenuItem(text = { Text(uiText(severity.name.lowercase().replaceFirstChar { it.uppercase() })) }, onClick = { onFilter(severity); open = false }, modifier = Modifier.actionPointer())
                }
            }
        }
    }
}

@Composable
private fun FindingsTable(findings: List<ChangeFinding>, isDemo: Boolean) {
    ProductTable(listOf("Severity", "Finding / Evidence", "Resource", "Register"), listOf(0.9f, 3.3f, 1.9f, 1f)) {
        findings.forEach { finding ->
            key(finding.ruleId, finding.resourceAddress, finding.detail) {
                ProductTableRow {
                    Box(Modifier.weight(0.9f)) { SeverityBadge(finding.severity) }
                    Column(Modifier.weight(3.3f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(if (isDemo) uiText(finding.title) else finding.title, style = MaterialTheme.typography.titleSmall)
                        MetaText(finding.ruleId)
                        Text(if (isDemo) uiText(finding.detail) else finding.detail, style = MaterialTheme.typography.bodySmall, color = ProductStyle.colors.muted)
                        FindingEvidence(finding, isDemo)
                    }
                    Column(Modifier.weight(1.9f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SelectionContainer { Text(finding.resourceAddress, fontFamily = ProductStyle.fonts.mono, fontSize = 12.sp, color = ProductStyle.colors.muted) }
                        finding.functionId?.let { OutlineChip(it + if (finding.criticalFunction) " / " + uiText(UiCopy.CRITICAL) else "") }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        finding.templates.forEach { OutlineChip(it) }
                    }
                }
            }
        }
    }
}

@Composable
private fun FindingEvidence(finding: ChangeFinding, isDemo: Boolean) {
    var expanded by rememberSaveable(finding.ruleId, finding.resourceAddress) { mutableStateOf(false) }
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, tween(ProductMotion.CONTENT), label = "evidence-disclosure")
    val expansionLabel = uiText(if (expanded) UiCopy.EXPANDED else UiCopy.COLLAPSED)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = { expanded = !expanded }, contentPadding = PaddingValues(0.dp),
            modifier = Modifier.actionPointer().semantics { stateDescription = expansionLabel }) {
            LineIcon(ProductIcon.CHEVRON, Modifier.graphicsLayer { rotationZ = rotation }, color = ProductStyle.colors.accent, size = 13.dp)
            Spacer(Modifier.width(6.dp))
            Text(uiText("Details"), color = ProductStyle.colors.accent, style = MaterialTheme.typography.bodySmall)
        }
        ExpandDetails(expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!finding.explanation.isNullOrBlank() && finding.citation != null) Text(finding.explanation, style = MaterialTheme.typography.bodySmall)
                finding.location?.let { Text(uiText("Location") + ": " + it, style = MaterialTheme.typography.bodySmall, color = ProductStyle.colors.muted) }
                finding.functionId?.let { Text(it + (finding.functionName?.let { name -> " / " + if (isDemo) uiText(name) else name } ?: ""), style = MaterialTheme.typography.bodySmall) }
                if (finding.criticalFunction) Text(uiText("Critical function"), color = ProductStyle.colors.amber, style = MaterialTheme.typography.bodySmall)
                if (finding.citation != null) CitationView(finding.citation)
                else Text(uiText("No regulatory citation is available for this finding."), style = MaterialTheme.typography.bodySmall, color = ProductStyle.colors.muted)
            }
        }
    }
}
