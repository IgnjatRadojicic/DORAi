package dorai.app.connect

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dorai.app.components.*
import dorai.app.theme.ProductStyle

/** First screen: the user types the server address and the app checks that a DORAi server answers there. */
@Composable
fun ConnectScreen(
    state: ConnectState,
    onAddressChange: (String) -> Unit,
    onConnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier.fillMaxSize(), color = ProductStyle.colors.background) {
        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), contentAlignment = Alignment.Center) {
            ProductPanel(Modifier.widthIn(max = 460.dp)) {
                PanelHeading("DORAi", "Connect to your server")
                Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        uiText("Enter the address of the DORAi server your firm runs."),
                        color = ProductStyle.colors.muted, style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedTextField(
                        value = state.address,
                        onValueChange = onAddressChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(uiText("Server address")) },
                        placeholder = { Text("http://localhost:8080") },
                        singleLine = true,
                        enabled = !state.busy,
                        isError = state.error != null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { onConnect() }),
                    )
                    state.error?.let { ErrorView(it) }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        ProductButton(
                            if (state.busy) "Connecting..." else "Connect", onConnect,
                            enabled = !state.busy && state.address.isNotBlank(),
                            icon = if (state.busy) ProductIcon.REFRESH else null,
                        )
                    }
                }
            }
        }
    }
}
