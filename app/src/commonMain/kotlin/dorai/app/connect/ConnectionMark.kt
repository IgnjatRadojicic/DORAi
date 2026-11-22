package dorai.app.connect

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dorai.app.components.MetaText
import dorai.app.components.OutlineChip
import dorai.app.components.actionPointer
import dorai.app.components.uiText
import dorai.app.theme.ProductStyle

/** Small mark that shows whether the server answers, with its address underneath. */
@Composable
fun ConnectionMark(status: ServerStatus, address: String, modifier: Modifier = Modifier) {
    val colors = ProductStyle.colors
    Column(modifier.semantics { liveRegion = LiveRegionMode.Polite }, horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        when (status) {
            ServerStatus.CHECKING -> OutlineChip("Checking", colors.muted, dot = true)
            ServerStatus.CONNECTED -> OutlineChip("Connected", colors.green, dot = true)
            ServerStatus.UNREACHABLE -> OutlineChip("Not connected", colors.red, dot = true)
        }
        MetaText(address)
    }
}

/**
 * One line under the sign-in card: whether the server answers and which server it is, so nobody signs in to the
 * wrong one without seeing it. [onChange] opens the invite field, the only way to reach another server.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ServerLine(status: ServerStatus, address: String, onChange: () -> Unit, modifier: Modifier = Modifier) {
    val colors = ProductStyle.colors
    val (label, color) = when (status) {
        ServerStatus.CHECKING -> "Checking" to colors.muted
        ServerStatus.CONNECTED -> "Connected" to colors.green
        ServerStatus.UNREACHABLE -> "Not connected" to colors.red
    }
    FlowRow(modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), itemVerticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        MetaText(uiText(label) + " · " + address.removePrefix("https://").removePrefix("http://"))
        Text(uiText("Change"), Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onChange).actionPointer().padding(horizontal = 4.dp, vertical = 2.dp),
            color = colors.accent, style = MaterialTheme.typography.bodyMedium)
    }
}
