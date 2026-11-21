package dorai.app.components

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dorai.app.theme.ProductStyle
import dorai.app.theme.ThemeMode

data class WorkspaceSection(val id: String, val label: String, val number: String, val enabled: Boolean = true)

val matijaSections = listOf(
    WorkspaceSection("findings", "Change findings", "03"),
    WorkspaceSection("export", "Validation & export", "04"),
)

/** Shared visual frame. Navigation remains a callback supplied by the host. */
@Composable
fun ControlCenterFrame(
    selected: String, onSection: (String) -> Unit,
    theme: ThemeMode, onTheme: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
    sections: List<WorkspaceSection> = matijaSections,
    isDemo: Boolean = false,
    onLanguage: ((AppLanguage) -> Unit)? = null,
    account: (@Composable () -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
    content: @Composable (String) -> Unit,
) {
    val colors = ProductStyle.colors
    val sectionState = rememberSaveableStateHolder()
    Surface(modifier.fillMaxSize(), color = colors.background) {
        BoxWithConstraints {
            val desktop = maxWidth >= 1000.dp
            val wideHeader = maxWidth >= 600.dp
            Row(Modifier.fillMaxSize()) {
                if (desktop) {
                    Column(Modifier.width(244.dp).fillMaxHeight().padding(horizontal = 14.dp, vertical = 24.dp)) {
                        BrandMark(Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                        Spacer(Modifier.height(38.dp))
                        Text(uiText("Workspace"), Modifier.padding(start = 12.dp, bottom = 12.dp), color = colors.muted,
                            style = MaterialTheme.typography.bodySmall)
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            sections.forEach { NavigationItem(it, selected == it.id, onSection) }
                        }
                        Spacer(Modifier.weight(1f))
                        HorizontalDivider(color = colors.line.copy(alpha = 0.65f))
                        Row(Modifier.padding(horizontal = 10.dp, vertical = 20.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Surface(Modifier.size(34.dp), shape = RoundedCornerShape(9.dp), color = colors.hover) {
                                Box(contentAlignment = Alignment.Center) { Text("FP", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("FinPay Payments", style = MaterialTheme.typography.titleSmall)
                                Text("FinPay Payments B.V.", color = colors.muted, fontSize = 10.sp)
                            }
                        }
                        footer?.invoke()
                    }
                    VerticalDivider(color = colors.line.copy(alpha = 0.7f))
                }
                Column(Modifier.weight(1f).fillMaxHeight().background(lerp(colors.background, colors.panel, 0.18f))) {
                    Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = if (desktop) 32.dp else 20.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        if (!desktop) BrandMark() else {
                            LineIcon(sectionIcon(selected), color = colors.muted, size = 16.dp)
                            Text(uiText(sections.find { it.id == selected }?.label ?: "Workspace"), color = colors.muted, style = MaterialTheme.typography.bodyMedium)
                        }
                        Spacer(Modifier.weight(1f))
                        if (isDemo) OutlineChip("Demo", colors.muted, dot = true)
                        if (wideHeader && account != null) account()
                        if (onLanguage != null) LanguagePicker(onLanguage)
                        ThemeToggle(theme, onTheme)
                    }
                    if (!wideHeader && account != null) {
                        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
                            horizontalArrangement = Arrangement.End) { account() }
                    }
                    HorizontalDivider(color = colors.line.copy(alpha = 0.7f))
                    if (!desktop) {
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            sections.forEach { section ->
                                Box(Modifier.widthIn(min = 150.dp)) { NavigationItem(section, selected == section.id, onSection) }
                            }
                        }
                    }
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                        SectionTransition(selected, Modifier.widthIn(max = 1390.dp).fillMaxSize().padding(horizontal = if (desktop) 32.dp else 20.dp, vertical = 28.dp)) {
                            activeSection -> sectionState.SaveableStateProvider(activeSection) { content(activeSection) }
                        }
                    }
                    if (!desktop && footer != null) {
                        HorizontalDivider(color = colors.line.copy(alpha = 0.7f))
                        Box(Modifier.fillMaxWidth().background(colors.background).padding(20.dp)) {
                            Box(Modifier.widthIn(max = 244.dp)) { footer() }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun BrandMark(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
        Surface(Modifier.size(34.dp), color = ProductStyle.colors.accent.copy(alpha = 0.1f), shape = RoundedCornerShape(10.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Surface(Modifier.size(19.dp), shape = CircleShape, color = Color.Transparent, border = BorderStroke(4.dp, ProductStyle.colors.accent)) {}
            }
        }
        Text("DORAi", fontSize = 19.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp)
    }
}

private fun sectionIcon(id: String): ProductIcon = when (id) {
    "inventory" -> ProductIcon.CLOUD
    "review" -> ProductIcon.BOOK
    "export" -> ProductIcon.ARCHIVE
    else -> ProductIcon.SHIELD
}

@Composable
private fun NavigationItem(section: WorkspaceSection, active: Boolean, onSection: (String) -> Unit) {
    val colors = ProductStyle.colors
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val fill by animateColorAsState(
        if (active) colors.accent.copy(alpha = 0.1f) else if (hovered && section.enabled) colors.hover else Color.Transparent,
        tween(ProductMotion.FEEDBACK), label = "navigation-background")
    val foreground by animateColorAsState(
        if (!section.enabled) colors.muted.copy(alpha = 0.45f) else if (active) colors.ink else colors.muted,
        tween(ProductMotion.FEEDBACK), label = "navigation-label")
    Surface(onClick = { onSection(section.id) }, enabled = section.enabled, interactionSource = interaction,
        modifier = Modifier.fillMaxWidth().hoverable(interaction).actionPointer(section.enabled).semantics { selected = active },
        shape = RoundedCornerShape(8.dp), color = fill,
        border = if (active) BorderStroke(1.dp, colors.accent.copy(alpha = 0.14f)) else null) {
        Row(Modifier.height(42.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            LineIcon(sectionIcon(section.id), color = if (active) colors.accent else foreground, size = 17.dp)
            Text(uiText(section.label), color = foreground, style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (active) FontWeight.Medium else FontWeight.Normal)
        }
    }
}

@Composable
private fun ThemeToggle(mode: ThemeMode, onTheme: (ThemeMode) -> Unit) {
    val dark = mode == ThemeMode.DARK || (mode == ThemeMode.SYSTEM && isSystemInDarkTheme())
    val angle by animateFloatAsState(if (dark) 0f else 90f, tween(ProductMotion.THEME), label = "theme-icon-rotation")
    val actionLabel = uiText(if (dark) UiCopy.LIGHT_ACTION else UiCopy.DARK_ACTION)
    val currentLabel = uiText(if (dark) UiCopy.DARK_THEME else UiCopy.LIGHT_THEME)
    OutlinedIconButton(
        onClick = { onTheme(if (dark) ThemeMode.LIGHT else ThemeMode.DARK) },
        modifier = Modifier.size(36.dp).actionPointer().semantics {
            contentDescription = actionLabel
            stateDescription = currentLabel
        },
        shape = RoundedCornerShape(9.dp),
        border = BorderStroke(1.dp, ProductStyle.colors.line),
    ) {
        Crossfade(targetState = dark, animationSpec = tween(ProductMotion.CONTENT), label = "theme-icon") { isDark ->
            LineIcon(if (isDark) ProductIcon.MOON else ProductIcon.SUN,
                modifier = Modifier.graphicsLayer { rotationZ = angle }, color = ProductStyle.colors.ink, size = 17.dp)
        }
    }
}

@Composable
fun WorkspaceFooter() {
    HorizontalDivider(color = ProductStyle.colors.line)
    Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        MetaText("DORAi")
        MetaText("v0.1")
    }
}
