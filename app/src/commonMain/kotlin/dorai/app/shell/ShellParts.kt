package dorai.app.shell

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dorai.app.components.*
import dorai.app.theme.ProductStyle
import dorai.shared.model.Tokens

/** Compact signed-in identity for the workspace header. */
@Composable
fun AccountBar(session: Tokens, modifier: Modifier = Modifier) {
    val colors = ProductStyle.colors
    val role = uiText(session.role.name.lowercase().replaceFirstChar { it.uppercase() })
    Row(modifier.widthIn(max = 160.dp).padding(vertical = 4.dp)
        .semantics(mergeDescendants = true) { stateDescription = role },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(Modifier.size(28.dp), shape = RoundedCornerShape(8.dp), color = colors.hover,
            border = BorderStroke(1.dp, colors.line)) {
            Box(contentAlignment = Alignment.Center) {
                Text(session.username.take(2).uppercase(), color = colors.muted,
                    fontSize = 10.sp, fontWeight = FontWeight.Medium)
            }
        }
        Text(session.username, Modifier.weight(1f, fill = false), color = colors.muted,
            style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun LogoutButton(onLogout: () -> Unit) {
    val colors = ProductStyle.colors
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val focused by interaction.collectIsFocusedAsState()
    val fill by animateColorAsState(if (hovered || focused) colors.hover else Color.Transparent,
        tween(ProductMotion.FEEDBACK), label = "logout-fill")
    val foreground by animateColorAsState(if (hovered || focused) colors.ink else colors.muted,
        tween(ProductMotion.FEEDBACK), label = "logout-label")
    Surface(onClick = onLogout, interactionSource = interaction,
        modifier = Modifier.fillMaxWidth().hoverable(interaction),
        shape = RoundedCornerShape(8.dp), color = fill,
        border = BorderStroke(1.dp, if (focused) colors.accent else colors.line)) {
        Row(Modifier.heightIn(min = 44.dp).padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            LineIcon(ProductIcon.LOGOUT, color = foreground, size = 17.dp)
            Text(uiText("Log out"), color = foreground, style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium)
        }
    }
}
