package dorai.app.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dorai.app.components.*
import dorai.app.theme.ProductStyle

/**
 * First screen of the app: username and password.
 * [corner] is drawn in the top right corner; the shell puts the connection mark there.
 */
@Composable
fun LoginScreen(
    state: LoginState,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onLogin: () -> Unit,
    modifier: Modifier = Modifier,
    corner: @Composable () -> Unit = {},
) {
    Surface(modifier.fillMaxSize(), color = ProductStyle.colors.background) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), contentAlignment = Alignment.Center) {
                ProductPanel(Modifier.widthIn(max = 420.dp)) {
                    PanelHeading("DORAi", "Sign in")
                    Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        OutlinedTextField(
                            value = state.username,
                            onValueChange = onUsernameChange,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(uiText("Username")) },
                            singleLine = true,
                            enabled = !state.busy,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        )
                        OutlinedTextField(
                            value = state.password,
                            onValueChange = onPasswordChange,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(uiText("Password")) },
                            singleLine = true,
                            enabled = !state.busy,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(onGo = { if (state.canSubmit) onLogin() }),
                        )
                        state.error?.let { ErrorView(it) }
                        ProductButton(
                            if (state.busy) "Signing in..." else "Sign in", onLogin,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = state.canSubmit,
                            icon = if (state.busy) ProductIcon.REFRESH else null,
                        )
                    }
                }
            }
            Box(Modifier.align(Alignment.TopEnd).padding(20.dp)) { corner() }
        }
    }
}
