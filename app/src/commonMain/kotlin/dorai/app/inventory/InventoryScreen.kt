package dorai.app.inventory

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dorai.app.components.*
import dorai.app.theme.ProductStyle
import kotlin.math.roundToLong

@Composable
fun InventoryScreen(state: InventoryState, onScan: () -> Unit, onRefresh: () -> Unit, modifier: Modifier = Modifier, canScan: Boolean = true) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 64.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp)) {
        PageHeading("Cloud inventory") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ProductButton("Refresh", onRefresh, outlined = true, enabled = !state.busy, icon = ProductIcon.REFRESH)
                if (canScan) ProductButton(if (state.scanning) "Scanning..." else "Scan clouds", onScan, enabled = !state.busy, icon = ProductIcon.CLOUD)
            }
        }
        state.resources?.let { InventoryMetrics(it) }
        InventoryPanel(state, onRefresh)
    }
}

@Composable
private fun InventoryMetrics(resources: List<InventoryResource>) {
    MetricGrid(listOf(
        Metric("Resources", resources.size, "", ProductIcon.CLOUD),
        Metric("No service type", resources.count { it.serviceType == null }, "", ProductIcon.ALERT, resources.any { it.serviceType == null }),
        Metric("No business function", resources.count { it.function == null }, "", ProductIcon.ALERT, resources.any { it.function == null }),
        Metric("Awaiting review", resources.count { it.pendingReview }, "", ProductIcon.BOOK, resources.any { it.pendingReview }),
    ))
}

private val InventoryResource.pendingReview: Boolean
    get() = serviceType?.provenance == Provenance.AI_SUGGESTED || function?.provenance == Provenance.AI_SUGGESTED

@Composable
private fun InventoryPanel(state: InventoryState, onRefresh: () -> Unit) {
    ProductPanel {
        PanelHeading("", "Resources") {
            state.lastScan?.let { OutlineChip(if (it.live) "Live scan" else "Demo data", if (it.live) ProductStyle.colors.accent else ProductStyle.colors.amber, dot = true) }
        }
        val resources = state.resources
        when {
            state.scanning -> LoadingView("Scanning the clouds...")
            state.loading && resources == null -> LoadingView("Loading the inventory...")
            state.error != null && resources == null -> Box(Modifier.padding(24.dp)) { ErrorView(state.error, onRefresh) }
            resources.isNullOrEmpty() -> EmptyView("No resources yet", "Run a scan to read the resources from your clouds.", ProductIcon.CLOUD)
            else -> {
                state.error?.let { Box(Modifier.padding(horizontal = 24.dp)) { ErrorView(it) } }
                ProductTable(listOf("Resource", "Location", "Service type", "Business function", "Monthly cost"),
                    listOf(2.4f, 1.3f, 1.4f, 1.8f, 1f), minWidth = 950) {
                    resources.sortedBy { it.name }.forEach { resource -> ResourceRow(resource) }
                }
            }
        }
        NoticeEffect(state.lastScan?.let { uiFormat(UiCopy.SCAN_FINISHED, "resources" to it.resourceCount, "count" to it.newSuggestionCount) }, key = state.lastScan?.scannedAt)
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            MetaText(resources?.let { uiFormat(UiCopy.RESOURCE_COUNT, "count" to it.size) } ?: "")
            state.lastScan?.let { MetaText(uiFormat(UiCopy.LAST_SCAN, "time" to it.scannedAt.take(16).replace('T', ' '), "count" to it.newSuggestionCount)) }
        }
    }
}

@Composable
private fun ColumnScope.ResourceRow(resource: InventoryResource) {
    var expanded by rememberSaveable(resource.id) { mutableStateOf(false) }
    // The whole row opens the details; the expanded part below is not clickable, so its text can be selected.
    Column(Modifier.fillMaxWidth().toggleDetails(expanded) { expanded = !expanded }) {
        ResourceCells(resource, expanded)
    }
    ExpandDetails(expanded) {
        Column {
            ResourceDetails(resource)
            HorizontalDivider(color = ProductStyle.colors.line.copy(alpha = 0.7f))
        }
    }
}

@Composable
private fun ResourceCells(resource: InventoryResource, expanded: Boolean) {
    ProductTableRow {
        Column(Modifier.weight(2.4f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExpandIndicator(expanded)
                Text(resource.name, style = MaterialTheme.typography.bodyMedium)
            }
            Text(resource.type, fontFamily = ProductStyle.fonts.mono, fontSize = 12.sp, color = ProductStyle.colors.muted)
            MissingTagsMark(resource)
        }
        Column(Modifier.weight(1.3f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(resource.location, style = MaterialTheme.typography.bodyMedium)
            MetaText(listOfNotNull(resource.provider, resource.country).joinToString(" / "))
        }
        SourcedCell(resource.serviceType, Modifier.weight(1.4f))
        SourcedCell(resource.function, Modifier.weight(1.8f))
        Text(resource.monthlyCost?.let { "${formatCost(it)} ${resource.currency.orEmpty()}" } ?: "-",
            modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SourcedCell(value: SourcedValue?, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (value == null) {
            MetaText("Unknown")
        } else {
            Text(listOfNotNull(value.value, value.label).joinToString(" "), style = MaterialTheme.typography.bodyMedium)
            ProvenanceBadge(value.provenance)
        }
    }
}

/** Two decimals without platform formatting, which common code does not have. */
internal fun formatCost(amount: Double): String {
    val cents = (amount * 100).roundToLong()
    val whole = cents / 100
    val rest = (cents % 100).toString().padStart(2, '0')
    return "$whole.$rest"
}
