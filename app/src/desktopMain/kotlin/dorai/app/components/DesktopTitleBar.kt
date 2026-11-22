package dorai.app.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowScope
import androidx.compose.ui.window.WindowState
import dorai.app.theme.ProductStyle

/** Compose window chrome shares the live product palette on every desktop platform. */
@Composable
fun WindowScope.DesktopTitleBar(state: WindowState, onClose: () -> Unit) {
    val maximize = { state.placement = if (state.placement == WindowPlacement.Maximized) WindowPlacement.Floating else WindowPlacement.Maximized }
    Surface(color = ProductStyle.colors.background) {
        Row(Modifier.fillMaxWidth().height(34.dp), verticalAlignment = Alignment.CenterVertically) {
            WindowDraggableArea(Modifier.weight(1f).fillMaxHeight().pointerInput(Unit) { detectTapGestures(onDoubleTap = { maximize() }) }) {
                Row(Modifier.fillMaxSize().padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BrandTile(16.dp)
                    Text("DORAi", fontFamily = ProductStyle.fonts.body, color = ProductStyle.colors.muted, fontSize = 11.sp)
                }
            }
            CaptionButton("Minimize", CaptionAction.MINIMIZE, { state.isMinimized = true })
            CaptionButton(if (state.placement == WindowPlacement.Maximized) "Restore window" else "Maximize", CaptionAction.MAXIMIZE, maximize,
                restored = state.placement == WindowPlacement.Maximized)
            CaptionButton("Close", CaptionAction.CLOSE, onClose)
        }
    }
}

private enum class CaptionAction { MINIMIZE, MAXIMIZE, CLOSE }

@Composable
private fun CaptionButton(description: String, action: CaptionAction, onClick: () -> Unit, restored: Boolean = false) {
    val label = uiText(description)
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val fill by animateColorAsState(
        if (hovered && action == CaptionAction.CLOSE) Color(0xFFC84046) else if (hovered) ProductStyle.colors.hover else ProductStyle.colors.background,
        tween(ProductMotion.FEEDBACK), label = "caption-hover")
    val ink = if (hovered && action == CaptionAction.CLOSE) Color.White else ProductStyle.colors.ink
    Surface(onClick = onClick, interactionSource = interaction, color = fill,
        modifier = Modifier.width(44.dp).fillMaxHeight().hoverable(interaction).actionPointer().semantics { contentDescription = label }) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(12.dp)) {
                val u = size.width / 12f
                val stroke = 1.1f * u
                when (action) {
                    CaptionAction.MINIMIZE -> drawLine(ink, Offset(u, 6f * u), Offset(11f * u, 6f * u), stroke)
                    CaptionAction.MAXIMIZE -> {
                        if (restored) {
                            drawRect(ink, Offset(3f * u, u), Size(8f * u, 8f * u), style = Stroke(stroke))
                            drawRect(fill, Offset(u, 3f * u), Size(8f * u, 8f * u))
                            drawRect(ink, Offset(u, 3f * u), Size(8f * u, 8f * u), style = Stroke(stroke))
                        } else drawRect(ink, Offset(u, u), Size(10f * u, 10f * u), style = Stroke(stroke))
                    }
                    CaptionAction.CLOSE -> {
                        drawLine(ink, Offset(2f * u, 2f * u), Offset(10f * u, 10f * u), stroke)
                        drawLine(ink, Offset(2f * u, 10f * u), Offset(10f * u, 2f * u), stroke)
                    }
                }
            }
        }
    }
}
