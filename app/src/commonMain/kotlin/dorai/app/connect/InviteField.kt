package dorai.app.connect

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
    Column(modifier.widthIn(max = 440.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TextButton(onClick = onToggle, enabled = !state.busy,
            modifier = Modifier.heightIn(min = 44.dp).actionPointer(!state.busy), shape = AuthFieldShape,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            colors = ButtonDefaults.textButtonColors(contentColor = ProductStyle.colors.accent)) {
            Text(uiText(if (state.open) "Hide invite link" else "Have an invite link?"),
                style = MaterialTheme.typography.labelLarge)
        }
        ExpandDetails(state.open) {
            ProductPanel {
                Column(Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedTextField(
                        value = state.text,
                        onValueChange = onTextChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(uiText("Invite link")) },
                        placeholder = { Text("dorai://connect?...") },
                        singleLine = true,
                        enabled = !state.busy,
                        isError = state.error != null,
                        shape = AuthFieldShape,
                        colors = authFieldColors(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { if (state.canUse) onUse() }),
                    )
                    state.error?.let { ErrorView(it) }
                    AuthSubmitButton(
                        if (state.busy) "Connecting..." else "Use invite link", onUse,
                        enabled = state.canUse,
                        busy = state.busy,
                    )
                }
            }
        }
    }
}
