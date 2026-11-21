package dorai.app.shell

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dorai.app.components.*
import dorai.shared.model.Tokens

/** Who is signed in, with the way back to the login page. */
@Composable
fun AccountBar(session: Tokens, onLogout: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(bottom = 16.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Spacer(Modifier.weight(1f))
        MetaText(session.username)
        OutlineChip(session.role.name)
        ProductButton("Log out", onLogout, outlined = true)
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
