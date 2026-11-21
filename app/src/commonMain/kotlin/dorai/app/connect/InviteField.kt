package dorai.app.connect

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dorai.app.components.*
import dorai.app.theme.ProductStyle

/** "Have an invite link?" under the login card: paste the link from the admin's email to switch to that server. */
@Composable
fun InviteField(
    state: InviteState,
    onToggle: () -> Unit,
    onTextChange: (String) -> Unit,
    onUse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.widthIn(max = 420.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TextButton(onClick = onToggle, enabled = !state.busy) {
            Text(uiText(if (state.open) "Hide invite link" else "Have an invite link?"),
                color = ProductStyle.colors.accent, style = MaterialTheme.typography.bodyMedium)
        }
        ExpandDetails(state.open) {
            ProductPanel {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = state.text,
                        onValueChange = onTextChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(uiText("Invite link")) },
                        placeholder = { Text("dorai://connect?...") },
                        singleLine = true,
                        enabled = !state.busy,
                        isError = state.error != null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { if (state.canUse) onUse() }),
                    )
                    state.error?.let { ErrorView(it) }
                    ProductButton(
                        if (state.busy) "Connecting..." else "Use invite link", onUse,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = state.canUse,
                        icon = if (state.busy) ProductIcon.REFRESH else ProductIcon.ARROW,
                    )
                }
            }
        }
    }
}
