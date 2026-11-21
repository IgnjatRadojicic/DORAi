package dorai.app.connect

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dorai.app.components.MetaText
import dorai.app.components.OutlineChip
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
