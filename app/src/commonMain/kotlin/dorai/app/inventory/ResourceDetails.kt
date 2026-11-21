package dorai.app.inventory

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dorai.app.components.*
import dorai.app.theme.ProductStyle

/** The "Details" toggle of a resource row, the same control as the evidence rows in Change findings. */
@Composable
fun DetailsToggle(expanded: Boolean, onToggle: () -> Unit) {
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, tween(ProductMotion.CONTENT), label = "resource-disclosure")
    val expansionLabel = uiText(if (expanded) UiCopy.EXPANDED else UiCopy.COLLAPSED)
    TextButton(onClick = onToggle, contentPadding = PaddingValues(0.dp),
        modifier = Modifier.semantics { stateDescription = expansionLabel }) {
        LineIcon(ProductIcon.CHEVRON, Modifier.graphicsLayer { rotationZ = rotation }, color = ProductStyle.colors.accent, size = 13.dp)
        Spacer(Modifier.width(6.dp))
        Text(uiText("Details"), color = ProductStyle.colors.accent, style = MaterialTheme.typography.bodySmall)
    }
}

/** Small amber mark in the table when a resource lacks a DORA tag the rules expect. */
@Composable
fun MissingTagsMark(resource: InventoryResource) {
    if (resource.missingDoraTags.isNotEmpty()) OutlineChip("Missing DORA tags", ProductStyle.colors.amber, dot = true)
}

/** Everything the server knows about one resource, shown under its row. */
@Composable
fun ResourceDetails(resource: InventoryResource, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().background(ProductStyle.colors.background.copy(alpha = 0.5f))
        .padding(horizontal = 24.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DetailLine("Identifier") {
            SelectionContainer {
                Text(resource.id, fontFamily = ProductStyle.fonts.mono, fontSize = 12.sp, style = MaterialTheme.typography.bodySmall)
            }
        }
        DetailLine("Resource type") { DetailText(resource.type, mono = true) }
        DetailLine("Provider") { DetailText(listOfNotNull(resource.provider, resource.location, resource.country).joinToString(" / ")) }
        DetailLine("Source") {
            DetailText(listOfNotNull(uiText(resource.source), resource.changeAction?.let { uiText(it) }).joinToString(" / "))
        }
        DetailLine("Redundancy") { DetailText(uiText(resource.redundancy)) }
        DetailLine("Contract") { DetailText(resource.contractId ?: uiText("None")) }
        DetailLine("Resource tags") { TagList(resource) }
    }
}

@Composable
private fun DetailLine(label: String, value: @Composable () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
        MetaText(label, Modifier.width(120.dp).padding(top = 2.dp))
        Box(Modifier.weight(1f)) { value() }
    }
}

@Composable
private fun DetailText(text: String, mono: Boolean = false) {
    Text(text, style = MaterialTheme.typography.bodySmall,
        fontFamily = if (mono) ProductStyle.fonts.mono else null)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagList(resource: InventoryResource) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (resource.tags.isEmpty()) DetailText(uiText("No tags"))
        else FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            resource.tags.toSortedMap().forEach { (key, value) -> OutlineChip("$key = $value") }
        }
        if (resource.missingDoraTags.isNotEmpty()) {
            Text(uiText("Missing") + ": " + resource.missingDoraTags.joinToString(", "),
                color = ProductStyle.colors.amber, style = MaterialTheme.typography.bodySmall)
        }
    }
}
