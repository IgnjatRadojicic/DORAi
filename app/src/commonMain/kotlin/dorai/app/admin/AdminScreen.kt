package dorai.app.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dorai.app.components.NoticeEffect
import dorai.app.components.SelectChip
import dorai.app.components.EmptyView
import dorai.app.components.ErrorView
import dorai.app.components.LoadingView
import dorai.app.components.MetaText
import dorai.app.components.OutlineChip
import dorai.app.components.PageHeading
import dorai.app.components.PanelHeading
import dorai.app.components.ConfirmDialog
import dorai.app.components.ProductButton
import dorai.app.components.ProductIcon
import dorai.app.components.ProductPanel
import dorai.app.components.ProductTable
import dorai.app.components.ProductTableRow
import dorai.app.theme.ProductStyle
import dorai.shared.model.Role
import kotlinx.coroutines.launch
import dorai.app.components.UiCopy
import dorai.app.components.uiFormat
import dorai.app.components.uiText
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType

@Composable
fun AdminRoute(model: AdminViewModel, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    LaunchedEffect(model) { model.refresh() }
    AdminScreen(
        model.state,
        AdminActions(
            refresh = { scope.launch { model.refresh() } },
            username = model::onUsername,
            role = model::onRole,
            password = model::onPassword,
            create = { scope.launch { model.create() } },
            invite = { scope.launch { model.invite(it) } },
            act = { action, user -> scope.launch { model.act(action, user) } },
            enable = { scope.launch { model.enable(it) } },
            cancelConfirm = model::cancelConfirm,
            closeHandover = model::closeHandover,
            canActOn = { name -> model.state.users.firstOrNull { it.username == name }?.let(model::canActOn) == true },
        ),
        modifier,
    )
}

/** Callbacks of the admin screen; the screen never talks to the server itself. */
class AdminActions(
    val refresh: () -> Unit,
    val username: (String) -> Unit,
    val role: (Role) -> Unit,
    val password: (String) -> Unit,
    val create: () -> Unit,
    val invite: (String) -> Unit,
    val act: (AdminAction, String) -> Unit,
    val enable: (String) -> Unit,
    val cancelConfirm: () -> Unit,
    val closeHandover: () -> Unit,
    val canActOn: (String) -> Boolean,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdminScreen(state: AdminState, actions: AdminActions, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 64.dp), verticalArrangement = Arrangement.spacedBy(28.dp)) {
        PageHeading("Administration") {
            ProductButton(if (state.busy) "Loading..." else "Refresh", actions.refresh, outlined = true, enabled = !state.busy, icon = ProductIcon.REFRESH)
        }
        state.handover?.let { HandoverPanel(it, actions.closeHandover) }
        ProductPanel {
            PanelHeading("New user", "Create a user and an invite link")
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Two equal fields on one line; a label too long for its half is cut, never wrapped, so both keep one height.
                Row(Modifier.widthIn(max = 640.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(state.newUsername, actions.username, Modifier.weight(1f),
                        label = { Text(uiText("Username"), maxLines = 1, overflow = TextOverflow.Ellipsis) }, singleLine = true, enabled = !state.busy,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next))
                    OutlinedTextField(state.newPassword, actions.password, Modifier.weight(1f),
                        label = { Text(uiText("Temporary password (blank: generated)"), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        singleLine = true, enabled = !state.busy, visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { if (state.canCreate) actions.create() }))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    MetaText("Role")
                    Role.entries.forEach { role -> SelectChip(role.name, role == state.newRole, { actions.role(role) }, enabled = !state.busy) }
                }
                MetaText("Admin: users, invites and settings. Reviewer: scan, accept or reject, export. Viewer: read only.")
                ProductButton("Create user", actions.create, enabled = state.canCreate)
            }
        }
        NoticeEffect(state.message)
        when {
            state.busy && state.users.isEmpty() -> LoadingView("Loading users...")
            state.error != null -> ErrorView(state.error, actions.refresh)
            state.users.isEmpty() -> EmptyView("No users", "")
            else -> UsersPanel(state, actions)
        }
        if (state.openInvites.isNotEmpty()) {
            ProductPanel {
                PanelHeading("Invites", "Not used yet")
                ProductTable(listOf("User", "Expires"), listOf(1f, 1f), minWidth = 500) {
                    state.openInvites.forEach { invite ->
                        ProductTableRow {
                            Text(invite.username, Modifier.weight(1f))
                            MetaText(invite.expiresAt.toString(), Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UsersPanel(state: AdminState, actions: AdminActions) {
    state.confirm?.let { pending ->
        val name = pending.username
        when (pending.action) {
            AdminAction.DISABLE -> ConfirmDialog(uiFormat(UiCopy.DISABLE_USER_TITLE, "name" to name),
                uiFormat(UiCopy.DISABLE_USER_BODY, "name" to name),
                uiFormat(UiCopy.DISABLE_USER_CONFIRM, "name" to name), { actions.act(AdminAction.DISABLE, name) }, actions.cancelConfirm)
            AdminAction.REVOKE -> ConfirmDialog(uiFormat(UiCopy.REVOKE_USER_TITLE, "name" to name),
                uiFormat(UiCopy.REVOKE_USER_BODY, "name" to name),
                "Sign out everywhere", { actions.act(AdminAction.REVOKE, name) }, actions.cancelConfirm)
        }
    }
    ProductPanel {
        PanelHeading("Users", uiFormat(UiCopy.USERS_ON_SERVER, "count" to state.users.size))
        ProductTable(listOf("User", "Role", "Status", "Actions"), listOf(1f, 0.7f, 1f, 2.2f), minWidth = 900) {
            state.users.forEach { user ->
                ProductTableRow {
                    Text(user.username, Modifier.weight(1f))
                    Text(user.role.name, Modifier.weight(0.7f))
                    Box(Modifier.weight(1f)) {
                        when {
                            user.disabled -> OutlineChip("Disabled", ProductStyle.colors.red, dot = true)
                            user.passwordChangeRequired -> OutlineChip("Must change password", ProductStyle.colors.amber, dot = true)
                            else -> OutlineChip("Active", ProductStyle.colors.accent, dot = true)
                        }
                    }
                    FlowRow(Modifier.weight(2.2f), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!user.disabled) ProductButton("New invite", { actions.invite(user.username) }, outlined = true, enabled = !state.busy)
                        else ProductButton("Enable", { actions.enable(user.username) }, outlined = true, enabled = !state.busy)
                        if (actions.canActOn(user.username)) {
                            // The labels never change, so the row stays still; the dialog below asks.
                            ProductButton("Sign out everywhere", { actions.act(AdminAction.REVOKE, user.username) }, outlined = true, enabled = !state.busy)
                            ProductButton("Disable", { actions.act(AdminAction.DISABLE, user.username) }, outlined = true, enabled = !state.busy)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HandoverPanel(handover: Handover, close: () -> Unit) {
    ProductPanel(highlighted = true) {
        PanelHeading(uiFormat(UiCopy.HANDOVER_TITLE, "name" to handover.username), uiText(UiCopy.HANDOVER_SHOWN_ONCE)) {
            ProductButton("Done", close, outlined = true)
        }
        SelectionContainer {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                handover.link?.let {
                    MetaText(uiFormat(UiCopy.HANDOVER_LINK, "until" to handover.expiresAt))
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
                handover.temporaryPassword?.let {
                    MetaText("Temporary password. Send it on a different channel than the link; it must be changed at first sign-in:")
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
