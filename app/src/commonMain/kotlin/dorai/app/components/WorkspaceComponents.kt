package dorai.app.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dorai.app.theme.ProductStyle

@Composable
fun MetaText(text: String, modifier: Modifier = Modifier, color: Color = ProductStyle.colors.muted) {
    Text(uiText(text), modifier, color = color, style = MaterialTheme.typography.labelSmall)
}

@Composable
fun ProductPanel(modifier: Modifier = Modifier, highlighted: Boolean = false, onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val colors = ProductStyle.colors
    val border by animateColorAsState(if (highlighted) colors.accent.copy(alpha = 0.55f) else if (hovered && onClick != null) colors.muted.copy(alpha = 0.5f) else colors.line,
        tween(ProductMotion.FEEDBACK), label = "panel-border")
    val fill by animateColorAsState(if (highlighted) lerp(colors.panel, colors.accent, 0.045f) else colors.panel,
        tween(ProductMotion.FEEDBACK), label = "panel-fill")
    if (onClick == null) Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), color = fill,
        border = BorderStroke(1.dp, border)) { Column(content = content) }
    else Surface(onClick = onClick, interactionSource = interaction, modifier = modifier.fillMaxWidth().hoverable(interaction).actionPointer(),
        shape = RoundedCornerShape(10.dp), color = fill, border = BorderStroke(1.dp, border)) { Column(content = content) }
}

@Composable
fun WorkspacePanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    ProductPanel(modifier) { Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content) }
}

@Composable
fun PanelHeading(overline: String, title: String, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 17.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            if (overline.isNotEmpty()) MetaText(overline)
            Text(uiText(title), style = MaterialTheme.typography.titleMedium)
        }
        trailing()
    }
    HorizontalDivider(color = ProductStyle.colors.line)
}

@Composable
fun ProductButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    outlined: Boolean = false, enabled: Boolean = true, icon: ProductIcon? = null) {
    val colors = ProductStyle.colors
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (pressed) 0.975f else 1f, tween(ProductMotion.FEEDBACK), label = "button-press")
    val fill by animateColorAsState(when {
        outlined && hovered && enabled -> colors.hover
        outlined -> Color.Transparent
        !enabled -> colors.accent.copy(alpha = 0.28f)
        hovered -> lerp(colors.accent, Color.White, 0.1f)
        else -> colors.accent
    }, tween(ProductMotion.FEEDBACK), label = "button-fill")
    Surface(onClick = onClick, enabled = enabled, interactionSource = interaction,
        modifier = modifier.hoverable(interaction).actionPointer(enabled).graphicsLayer { scaleX = scale; scaleY = scale }, shape = RoundedCornerShape(7.dp),
        color = fill,
        contentColor = if (outlined) colors.ink else colors.background,
        border = BorderStroke(if (focused) 2.dp else 1.dp, if (focused) colors.accent else if (outlined) colors.line else colors.accent.copy(alpha = if (enabled) 1f else 0.28f))) {
        Row(Modifier.defaultMinSize(minHeight = 36.dp).padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
            val foreground = if (!enabled) colors.muted else if (outlined) colors.ink else colors.background
            if (icon != null) LineIcon(icon, color = foreground, size = 15.dp)
            Text(uiText(text), color = foreground, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun OutlineChip(text: String, color: Color = ProductStyle.colors.muted, dot: Boolean = false) {
    Surface(color = Color.Transparent, shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, if (color == ProductStyle.colors.muted) ProductStyle.colors.line else color.copy(alpha = 0.4f))) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            if (dot) Box(Modifier.size(6.dp).background(color, RoundedCornerShape(50)))
            Text(uiText(text), color = color, fontFamily = ProductStyle.fonts.mono, fontSize = 10.sp, lineHeight = 14.sp)
        }
    }
}

@Composable
fun SeverityBadge(severity: FindingSeverity) {
    val colors = ProductStyle.colors
    OutlineChip(severity.name.lowercase().replaceFirstChar { it.uppercase() }, when (severity) {
        FindingSeverity.HIGH -> colors.red
        FindingSeverity.MEDIUM -> colors.amber
        FindingSeverity.LOW -> colors.muted
    }, dot = true)
}

@Composable
fun ProvenanceBadge(provenance: Provenance) {
    OutlineChip(provenance.label, when (provenance) {
        Provenance.HUMAN_CONFIRMED -> ProductStyle.colors.accent
        Provenance.AI_SUGGESTED -> ProductStyle.colors.amber
        else -> ProductStyle.colors.muted
    }, dot = true)
}

@Composable
fun CitationView(citation: EvidenceCitation) {
    Surface(color = ProductStyle.colors.background, shape = RoundedCornerShape(4.dp), border = BorderStroke(1.dp, ProductStyle.colors.line)) {
        SelectionContainer {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LineIcon(ProductIcon.BOOK, size = 14.dp, color = ProductStyle.colors.ink)
                    Text(listOfNotNull(citation.regulation, citation.article, citation.paragraph).joinToString(" / "), style = MaterialTheme.typography.bodySmall)
                }
                Text(citation.excerpt, style = MaterialTheme.typography.bodySmall, color = ProductStyle.colors.muted)
            }
        }
    }
}

@Composable
fun EmptyView(title: String, description: String, icon: ProductIcon = ProductIcon.FILE) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        LineIcon(icon, color = ProductStyle.colors.accent, size = 25.dp)
        Text(uiText(title), style = MaterialTheme.typography.titleSmall)
        if (description.isNotBlank()) Text(uiText(description), color = ProductStyle.colors.muted, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun ErrorView(message: String, retry: (() -> Unit)? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LineIcon(ProductIcon.ALERT, color = ProductStyle.colors.red, size = 17.dp)
            Text(uiText(message), color = ProductStyle.colors.red, style = MaterialTheme.typography.bodyMedium)
        }
        if (retry != null) ProductButton("Try again", retry, outlined = true)
    }
}

@Composable
fun LoadingView(message: String) {
    Row(Modifier.fillMaxWidth().padding(40.dp).semantics { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(12.dp))
        Text(uiText(message), color = ProductStyle.colors.muted)
    }
}

@Composable
fun DemoNotice(message: String) {
    Surface(color = ProductStyle.colors.note, border = BorderStroke(1.dp, ProductStyle.colors.noteBorder), shape = RoundedCornerShape(5.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LineIcon(ProductIcon.ALERT, color = ProductStyle.colors.noteInk, size = 17.dp)
            Text(uiText(message), color = ProductStyle.colors.noteInk, style = MaterialTheme.typography.bodySmall)
        }
    }
}

data class Metric(val label: String, val value: Int, val description: String, val icon: ProductIcon, val attention: Boolean = false,
    val selected: Boolean = false, val onClick: (() -> Unit)? = null)

@Composable
fun MetricGrid(metrics: List<Metric>) {
    BoxWithConstraints {
        val columns = if (maxWidth < 600.dp) 2 else metrics.size
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            metrics.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { metric ->
                        ProductPanel(Modifier.weight(1f), highlighted = metric.selected, onClick = metric.onClick) {
                            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(verticalAlignment = Alignment.Top) {
                                    Text(uiText(metric.label), Modifier.weight(1f), color = ProductStyle.colors.muted, style = MaterialTheme.typography.bodySmall)
                                    LineIcon(metric.icon, color = if (metric.attention) ProductStyle.colors.amber else ProductStyle.colors.muted, size = 17.dp)
                                }
                                AnimatedContent(targetState = metric.value,
                                    transitionSpec = { fadeIn(tween(ProductMotion.CONTENT)).togetherWith(fadeOut(tween(ProductMotion.FEEDBACK))) },
                                    label = "metric-value") { count ->
                                    Text(count.toString(), fontFamily = ProductStyle.fonts.mono, fontSize = 30.sp, lineHeight = 38.sp,
                                        color = if (metric.attention) ProductStyle.colors.amber else ProductStyle.colors.ink)
                                }
                                if (metric.description.isNotBlank()) Text(uiText(metric.description), color = ProductStyle.colors.muted, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProductTable(headers: List<String>, weights: List<Float>, minWidth: Int = 850, content: @Composable ColumnScope.() -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val tableWidth = maxOf(maxWidth, minWidth.dp)
        val scroll = rememberScrollState()
        Column {
            TableScrollbar(scroll, Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp))
            Box(Modifier.horizontalScroll(scroll)) {
                Column(Modifier.width(tableWidth)) {
                    Row(Modifier.fillMaxWidth().background(ProductStyle.colors.tableHead).padding(horizontal = 24.dp, vertical = 15.dp),
                        horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        headers.forEachIndexed { index, label -> MetaText(uiText(label).uppercase(), Modifier.weight(weights[index])) }
                    }
                    HorizontalDivider(color = ProductStyle.colors.line)
                    content()
                }
            }
            TableScrollbar(scroll, Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp))
        }
    }
}

@Composable
fun ProductTableRow(content: @Composable RowScope.() -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val fill by animateColorAsState(if (hovered) ProductStyle.colors.hover else Color.Transparent,
        tween(ProductMotion.FEEDBACK), label = "row-hover")
    Row(Modifier.fillMaxWidth().hoverable(interaction).background(fill)
        .padding(horizontal = 24.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(20.dp), content = content)
    HorizontalDivider(color = ProductStyle.colors.line.copy(alpha = 0.7f))
}

@Composable
fun PageHeading(title: String, action: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < 500.dp) Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(uiText(title), style = MaterialTheme.typography.headlineMedium)
            action()
        } else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Text(uiText(title), modifier = Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium)
            action()
        }
    }
}
