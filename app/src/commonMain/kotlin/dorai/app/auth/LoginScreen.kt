package dorai.app.auth

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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dorai.app.components.*
import dorai.app.theme.ProductStyle

/** What the login page can ask the shell to do. */
class LoginActions(
    val onUsernameChange: (String) -> Unit,
    val onPasswordChange: (String) -> Unit,
    val onLogin: () -> Unit,
    val onNewPasswordChange: (String) -> Unit,
    val onRepeatPasswordChange: (String) -> Unit,
    val onChangePassword: () -> Unit,
    val onBack: () -> Unit,
)

/**
 * First screen of the app: the sign-in card, or the password change card after a temporary password.
 * [corner] is drawn in the top right corner; the shell puts the connection mark there.
 */
@Composable
fun LoginScreen(
    state: LoginState,
    actions: LoginActions,
    modifier: Modifier = Modifier,
    corner: @Composable () -> Unit = {},
) {
    Surface(modifier.fillMaxSize(), color = ProductStyle.colors.background) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), contentAlignment = Alignment.Center) {
                ProductPanel(Modifier.widthIn(max = 420.dp)) {
                    when (state.step) {
                        LoginStep.SIGN_IN -> SignInCard(state, actions)
                        LoginStep.CHANGE_PASSWORD -> PasswordChangeCard(state, actions)
                    }
                }
            }
            Box(Modifier.align(Alignment.TopEnd).padding(20.dp)) { corner() }
        }
    }
}

@Composable
private fun SignInCard(state: LoginState, actions: LoginActions) {
    PanelHeading("DORAi", "Sign in")
    Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        OutlinedTextField(
            value = state.username,
            onValueChange = actions.onUsernameChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(uiText("Username")) },
            singleLine = true,
            enabled = !state.busy,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        )
        PasswordField("Password", state.password, actions.onPasswordChange, !state.busy) { if (state.canSubmit) actions.onLogin() }
        state.error?.let { ErrorView(it) }
        ProductButton(
            if (state.busy) "Signing in..." else "Sign in", actions.onLogin,
            modifier = Modifier.fillMaxWidth(),
            enabled = state.canSubmit,
            icon = if (state.busy) ProductIcon.REFRESH else null,
        )
    }
}

@Composable
private fun PasswordChangeCard(state: LoginState, actions: LoginActions) {
    PanelHeading(state.username.trim(), "Set a new password")
    Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            uiText("Your password is temporary. Choose a new one with at least 12 characters to continue."),
            color = ProductStyle.colors.muted, style = MaterialTheme.typography.bodyMedium,
        )
        PasswordField("New password", state.newPassword, actions.onNewPasswordChange, !state.busy, ImeAction.Next)
        PasswordField("Repeat new password", state.repeatPassword, actions.onRepeatPasswordChange, !state.busy) {
            if (state.canChange) actions.onChangePassword()
        }
        state.error?.let { ErrorView(it) }
        ProductButton(
            if (state.busy) "Saving..." else "Set password and continue", actions.onChangePassword,
            modifier = Modifier.fillMaxWidth(),
            enabled = state.canChange,
            icon = if (state.busy) ProductIcon.REFRESH else null,
        )
        ProductButton("Back to sign in", actions.onBack, modifier = Modifier.fillMaxWidth(), outlined = true, enabled = !state.busy)
    }
}

@Composable
private fun PasswordField(
    label: String, value: String, onChange: (String) -> Unit, enabled: Boolean,
    imeAction: ImeAction = ImeAction.Go, onGo: () -> Unit = {},
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(uiText(label)) },
        singleLine = true,
        enabled = enabled,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = imeAction),
        keyboardActions = KeyboardActions(onGo = { onGo() }),
    )
}
