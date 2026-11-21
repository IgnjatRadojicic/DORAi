package dorai.app.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dorai.app.theme.ProductStyle

internal val AuthFieldShape = RoundedCornerShape(8.dp)

@Composable
internal fun authFieldColors() = OutlinedTextFieldDefaults.colors(
    unfocusedBorderColor = ProductStyle.colors.line,
    disabledBorderColor = ProductStyle.colors.line.copy(alpha = 0.6f),
    focusedBorderColor = ProductStyle.colors.accent,
    unfocusedLabelColor = ProductStyle.colors.muted,
)

/** Shared primary action for sign-in, password changes, and invite links. */
@Composable
internal fun AuthSubmitButton(text: String, onClick: () -> Unit, enabled: Boolean, busy: Boolean) {
    val colors = ProductStyle.colors
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val focused by interaction.collectIsFocusedAsState()
    val fill by animateColorAsState(
        if (hovered || focused) lerp(colors.accent, colors.ink, 0.08f) else colors.accent,
        tween(ProductMotion.FEEDBACK), label = "auth-button-fill",
    )
    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaction,
        modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp).hoverable(interaction).actionPointer(enabled)
            .semantics { liveRegion = LiveRegionMode.Polite },
        shape = AuthFieldShape,
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = fill,
            contentColor = colors.background,
            disabledContainerColor = lerp(colors.panel, colors.accent, 0.09f),
            disabledContentColor = colors.muted,
        ),
        border = BorderStroke(if (focused) 2.dp else 1.dp, if (enabled) colors.accent else colors.line),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (busy) CircularProgressIndicator(Modifier.size(16.dp), color = colors.muted, strokeWidth = 2.dp)
            Text(uiText(text), style = MaterialTheme.typography.labelLarge)
            if (!busy) LineIcon(ProductIcon.ARROW, color = if (enabled) colors.background else colors.muted, size = 16.dp)
        }
    }
}
