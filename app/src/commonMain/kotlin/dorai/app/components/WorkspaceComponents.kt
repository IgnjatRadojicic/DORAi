package dorai.app.components

import androidx.compose.foundation.clickable
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.Measured
import androidx.compose.ui.layout.HorizontalAlignmentLine
import androidx.compose.ui.layout.Layout
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
import dorai.app.theme.ProductShapes
import dorai.app.theme.ProductSpacing

@Composable
fun MetaText(text: String, modifier: Modifier = Modifier, color: Color = ProductStyle.colors.muted) {
    Text(uiText(text), modifier, color = color, style = MaterialTheme.typography.labelSmall)
}

/** Codes and identifiers (LEIs, rule ids, Terraform types): the one place for monospace. */
@Composable
fun CodeText(text: String, modifier: Modifier = Modifier, color: Color = ProductStyle.colors.muted) {
    Text(text, modifier, color = color, style = MaterialTheme.typography.labelMedium)
}

/**
 * One option of a choice (a kind, a template, a role). Selected shows as a tinted chip with a check,
 * never as the blue primary button, so it can't be mistaken for an action.
 */
@Composable
fun SelectChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val colors = ProductStyle.colors
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val fill by animateColorAsState(when {
        selected -> colors.accent.copy(alpha = 0.14f)
        hovered && enabled -> colors.hover
        else -> Color.Transparent
    }, tween(ProductMotion.FEEDBACK), label = "chip-fill")
    Surface(onClick = onClick, enabled = enabled, interactionSource = interaction,
        modifier = modifier.hoverable(interaction).actionPointer(enabled).semantics { this.selected = selected; role = Role.Tab },
        shape = ProductShapes.pill, color = fill,
        border = BorderStroke(1.dp, if (selected) colors.accent.copy(alpha = 0.45f) else colors.line)) {
        Row(Modifier.defaultMinSize(minHeight = 34.dp).padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (selected) LineIcon(ProductIcon.CHECK, color = colors.accent, size = 14.dp)
            Text(uiText(text), color = if (selected) colors.ink else colors.muted, style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal)
        }
    }
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
    if (onClick == null) Surface(modifier.fillMaxWidth(), shape = ProductShapes.card, color = fill,
        border = BorderStroke(1.dp, border)) { Column(content = content) }
    else Surface(onClick = onClick, interactionSource = interaction, modifier = modifier.fillMaxWidth().hoverable(interaction).actionPointer(),
        shape = ProductShapes.card, color = fill, border = BorderStroke(1.dp, border)) { Column(content = content) }
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
    outlined: Boolean = false, enabled: Boolean = true, icon: ProductIcon? = null, destructive: Boolean = false) {
    val colors = ProductStyle.colors
    // Red only for the confirm button of a dialog that deletes, disables or signs someone out.
    val main = if (destructive) colors.red else colors.accent
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (pressed) 0.975f else 1f, tween(ProductMotion.FEEDBACK), label = "button-press")
    val fill by animateColorAsState(when {
        outlined && hovered && enabled -> colors.hover
        outlined -> Color.Transparent
        !enabled -> main.copy(alpha = 0.28f)
        hovered -> lerp(main, Color.White, 0.1f)
        else -> main
    }, tween(ProductMotion.FEEDBACK), label = "button-fill")
    Surface(onClick = onClick, enabled = enabled, interactionSource = interaction,
        modifier = modifier.hoverable(interaction).actionPointer(enabled).graphicsLayer { scaleX = scale; scaleY = scale }, shape = ProductShapes.control,
        color = fill,
        contentColor = if (outlined) colors.ink else colors.background,
        border = BorderStroke(if (focused) 2.dp else 1.dp, if (focused) colors.accent else if (outlined) colors.line else main.copy(alpha = if (enabled) 1f else 0.28f))) {
        Row(Modifier.defaultMinSize(minHeight = ProductSpacing.control).padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
            val foreground = if (!enabled) colors.muted else if (outlined) colors.ink else colors.background
            if (icon != null) LineIcon(icon, color = foreground, size = 15.dp)
            Text(uiText(text), color = foreground, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun OutlineChip(text: String, color: Color = ProductStyle.colors.muted, dot: Boolean = false) {
    Surface(color = Color.Transparent, shape = ProductShapes.pill,
        border = BorderStroke(1.dp, if (color == ProductStyle.colors.muted) ProductStyle.colors.line else color.copy(alpha = 0.4f))) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            if (dot) Box(Modifier.size(6.dp).background(color, RoundedCornerShape(50)))
            Text(uiText(text), color = color, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
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
    Surface(color = ProductStyle.colors.background, shape = ProductShapes.control, border = BorderStroke(1.dp, ProductStyle.colors.line)) {
        SelectionContainer {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LineIcon(ProductIcon.BOOK, size = 14.dp, color = ProductStyle.colors.ink)
                    Text(listOfNotNull(citation.regulation, citation.article, citation.paragraph).joinToString(" / "), style = MaterialTheme.typography.bodySmall)
                }
                // Long excerpts start at three lines, so the Accept and Reject buttons stay close to the suggestion.
                var expanded by remember(citation) { mutableStateOf(false) }
                var long by remember(citation) { mutableStateOf(false) }
                Text(citation.excerpt, style = MaterialTheme.typography.bodySmall, color = ProductStyle.colors.muted,
                    maxLines = if (expanded) Int.MAX_VALUE else 3, overflow = TextOverflow.Ellipsis,
                    onTextLayout = { if (!expanded) long = it.hasVisualOverflow })
                if (long || expanded) {
                    Text(uiText(if (expanded) "Show less" else "Show more"),
                        Modifier.clickable { expanded = !expanded }.actionPointer(), color = ProductStyle.colors.accent,
                        style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                }
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
    Surface(color = ProductStyle.colors.note, border = BorderStroke(1.dp, ProductStyle.colors.noteBorder), shape = ProductShapes.card) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LineIcon(ProductIcon.ALERT, color = ProductStyle.colors.noteInk, size = 17.dp)
            Text(uiText(message), color = ProductStyle.colors.noteInk, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/**
 * A count at the top of a page. [attention] colours it amber (something to look at);
 * [error] colours it red with the alert icon (something that blocks), so an error never reads as "OK".
 */
data class Metric(val label: String, val value: Int, val description: String, val icon: ProductIcon, val attention: Boolean = false,
    val selected: Boolean = false, val onClick: (() -> Unit)? = null, val error: Boolean = false)

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
                                    val tone = when {
                                        metric.error && metric.value > 0 -> ProductStyle.colors.red
                                        metric.attention -> ProductStyle.colors.amber
                                        else -> null
                                    }
                                    LineIcon(if (metric.error && metric.value > 0) ProductIcon.ALERT else metric.icon, color = tone ?: ProductStyle.colors.muted, size = 17.dp)
                                }
                                AnimatedContent(targetState = metric.value,
                                    transitionSpec = { fadeIn(tween(ProductMotion.CONTENT)).togetherWith(fadeOut(tween(ProductMotion.FEEDBACK))) },
                                    label = "metric-value") { count ->
                                    Text(count.toString(), style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "tnum"),
                                        color = when {
                                            metric.error && count > 0 -> ProductStyle.colors.red
                                            metric.attention -> ProductStyle.colors.amber
                                            else -> ProductStyle.colors.ink
                                        })
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

/** The column names while a table shows as cards (narrow screens); null when it is a real table. */
private val LocalCardTable = compositionLocalOf<List<String>?> { null }

/**
 * A table from 600 dp; below that every row becomes a card with the column names as labels,
 * so phones never scroll sideways through a table.
 */
@Composable
fun ProductTable(headers: List<String>, weights: List<Float>, minWidth: Int = 850, content: @Composable ColumnScope.() -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < 600.dp) {
            CompositionLocalProvider(LocalCardTable provides headers) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
            }
            return@BoxWithConstraints
        }
        val tableWidth = maxOf(maxWidth, minWidth.dp)
        val scroll = rememberScrollState()
        Column {
            TableScrollbar(scroll, Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp))
            Box(Modifier.horizontalScroll(scroll)) {
                Column(Modifier.width(tableWidth)) {
                    Row(Modifier.fillMaxWidth().background(ProductStyle.colors.tableHead).padding(horizontal = 24.dp, vertical = 15.dp),
                        horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        headers.forEachIndexed { index, label -> MetaText(uiText(label), Modifier.weight(weights[index])) }
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
    LocalCardTable.current?.let { headers -> return CardRow(headers, content) }
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val fill by animateColorAsState(if (hovered) ProductStyle.colors.hover else Color.Transparent,
        tween(ProductMotion.FEEDBACK), label = "row-hover")
    Row(Modifier.fillMaxWidth().hoverable(interaction).background(fill)
        .padding(horizontal = 24.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(20.dp), content = content)
    HorizontalDivider(color = ProductStyle.colors.line.copy(alpha = 0.7f))
}

/** Weights and row alignment mean nothing in a card: the cells simply stack. */
private object CardRowScope : RowScope {
    override fun Modifier.weight(weight: Float, fill: Boolean): Modifier = this
    override fun Modifier.align(alignment: Alignment.Vertical): Modifier = this
    override fun Modifier.alignBy(alignmentLine: HorizontalAlignmentLine): Modifier = this
    override fun Modifier.alignByBaseline(): Modifier = this
    override fun Modifier.alignBy(alignmentLineBlock: (Measured) -> Int): Modifier = this
}

/** One row as a card: the first cell as its title, every other cell under its column name. */
@Composable
private fun CardRow(headers: List<String>, content: @Composable RowScope.() -> Unit) {
    val colors = ProductStyle.colors
    Surface(Modifier.fillMaxWidth(), shape = ProductShapes.control, color = colors.panel, border = BorderStroke(1.dp, colors.line)) {
        Layout(
            content = {
                headers.forEach { MetaText(it) }
                CardRowScope.content()
            },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        ) { measurables, constraints ->
            val labels = measurables.take(headers.size)
            val cells = measurables.drop(headers.size)
            val loose = constraints.copy(minWidth = 0, minHeight = 0)
            val gap = 10.dp.roundToPx()
            val placed = cells.mapIndexed { index, cell ->
                val cellPlaceable = cell.measure(loose)
                val showLabel = index > 0 && headers.getOrNull(index)?.isNotBlank() == true && cellPlaceable.height > 0
                (if (showLabel) labels[index].measure(loose) else null) to cellPlaceable
            }
            val height = placed.filter { it.second.height > 0 }.sumOf { (label, cell) -> (label?.height ?: 0) + cell.height + gap } - gap
            layout(constraints.maxWidth, maxOf(height, 0)) {
                var y = 0
                placed.forEach { (label, cell) ->
                    if (cell.height == 0) return@forEach
                    label?.let { it.placeRelative(0, y); y += it.height }
                    cell.placeRelative(0, y)
                    y += cell.height + gap
                }
            }
        }
    }
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
