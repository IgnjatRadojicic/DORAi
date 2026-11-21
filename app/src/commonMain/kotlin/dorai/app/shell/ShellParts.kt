package dorai.app.shell

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dorai.app.components.*

/** Shows which server the workspace talks to, with a way back to the Connect screen. */
@Composable
fun ServerBar(address: String, onChangeServer: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(bottom = 16.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        LineIcon(ProductIcon.CLOUD, size = 14.dp)
        MetaText(address, Modifier.weight(1f))
        ProductButton("Change server", onChangeServer, outlined = true)
    }
}

/** Stands in for a section that a later step builds. */
@Composable
fun SectionPlaceholder(title: String, description: String, icon: ProductIcon, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        PageHeading(title) {}
        ProductPanel { EmptyView(title, description, icon) }
    }
}
