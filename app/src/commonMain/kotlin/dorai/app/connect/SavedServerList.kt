package dorai.app.connect

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dorai.app.components.LineIcon
import dorai.app.components.MetaText
import dorai.app.components.ProductIcon
import dorai.app.components.actionPointer
import dorai.app.components.uiText
import dorai.app.theme.ProductStyle

/**
 * The servers this app has reached through an invite, under the sign-in card. Choosing one switches to it
 * without a new invite; its own session opens the workspace when it is still valid. Hidden while the
 * server in use is the only one.
 */
@Composable
fun SavedServerList(
    servers: List<SavedServer>,
    inUse: String,
    onUse: (ServerLink) -> Unit,
    onForget: (ServerLink) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (servers.none { it.link.address != inUse }) return
    val colors = ProductStyle.colors
    Column(modifier.widthIn(max = 440.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        MetaText(uiText("Saved servers"), Modifier.padding(start = 4.dp))
        servers.forEach { saved ->
            val current = saved.link.address == inUse
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (current) colors.accent.copy(alpha = 0.10f) else colors.panel,
                border = BorderStroke(1.dp, if (current) colors.accent.copy(alpha = 0.45f) else colors.line),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.clickable { onUse(saved.link) }.actionPointer().heightIn(min = 48.dp).padding(start = 14.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                        Text(saved.link.address.removePrefix("https://").removePrefix("http://"),
                            style = MaterialTheme.typography.bodyMedium, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        saved.username?.let { MetaText(it) }
                    }
                    if (current) {
                        MetaText(uiText("In use"), Modifier.padding(end = 8.dp), color = colors.accent)
                    } else {
                        val forget = uiText("Forget this server")
                        Box(
                            Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).clickable { onForget(saved.link) }.actionPointer()
                                .semantics { contentDescription = forget },
                            contentAlignment = Alignment.Center,
                        ) { LineIcon(ProductIcon.CLOSE, size = 16.dp) }
                    }
                }
            }
        }
    }
}
