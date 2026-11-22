package dorai.app.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
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
 * Sign-in and temporary password forms in the shared workspace style.
 * [below] holds the optional invite link field under the sign-in card.
 */
@Composable
fun LoginScreen(
    state: LoginState,
    actions: LoginActions,
    modifier: Modifier = Modifier,
    below: @Composable () -> Unit = {},
) {
    Surface(modifier.fillMaxSize(), color = ProductStyle.colors.background) {
        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), contentAlignment = Alignment.Center) {
            Column(Modifier.widthIn(max = 440.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ProductPanel {
                    when (state.step) {
                        LoginStep.SIGN_IN -> SignInCard(state, actions)
                        LoginStep.CHANGE_PASSWORD -> PasswordChangeCard(state, actions)
                    }
                }
                if (state.step == LoginStep.SIGN_IN) below()
            }
        }
    }
}

@Composable
private fun SignInCard(state: LoginState, actions: LoginActions) {
    Column(Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
            BrandLogo()
            Text(uiText("Sign in"), style = MaterialTheme.typography.headlineMedium)
        }
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedTextField(
                value = state.username,
                onValueChange = actions.onUsernameChange,
                modifier = Modifier.fillMaxWidth().focusOnShow(state.username.isEmpty()),
                label = { Text(uiText("Username")) },
                singleLine = true,
                enabled = !state.busy,
                shape = AuthFieldShape,
                colors = authFieldColors(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )
            PasswordField("Password", state.password, actions.onPasswordChange, !state.busy) {
                if (state.canSubmit) actions.onLogin()
            }
            state.error?.let { ErrorView(it) }
        }
        AuthSubmitButton(
            if (state.busy) "Signing in..." else "Sign in", actions.onLogin,
            enabled = state.canSubmit, busy = state.busy,
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
        PasswordField("New password", state.newPassword, actions.onNewPasswordChange, !state.busy, ImeAction.Next,
            modifier = Modifier.focusOnShow())
        PasswordField("Repeat new password", state.repeatPassword, actions.onRepeatPasswordChange, !state.busy) {
            if (state.canChange) actions.onChangePassword()
        }
        state.error?.let { ErrorView(it) }
        AuthSubmitButton(
            if (state.busy) "Saving..." else "Set password and continue", actions.onChangePassword,
            enabled = state.canChange, busy = state.busy,
        )
        ProductButton("Back to sign in", actions.onBack, modifier = Modifier.fillMaxWidth(), outlined = true, enabled = !state.busy)
    }
}

@Composable
private fun PasswordField(
    label: String, value: String, onChange: (String) -> Unit, enabled: Boolean,
    imeAction: ImeAction = ImeAction.Go, modifier: Modifier = Modifier, onGo: () -> Unit = {},
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(uiText(label)) },
        singleLine = true,
        enabled = enabled,
        shape = AuthFieldShape,
        colors = authFieldColors(),
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = imeAction),
        keyboardActions = KeyboardActions(onGo = { onGo() }),
    )
}
