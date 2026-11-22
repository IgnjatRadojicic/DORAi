package dorai.app.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import dorai.app.theme.ProductStyle

/**
 * Asks before something that can't be undone or throws work away. The dialog names the target in [title],
 * says what happens in [body], and keeps the page still underneath, so the confirm can't land on another row.
 * Escape, Back or a click outside cancel.
 */
@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = true,
    dismiss: String = "Cancel",
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        containerColor = ProductStyle.colors.panel,
        title = { Text(uiText(title), style = MaterialTheme.typography.titleLarge) },
        text = { Text(uiText(body), color = ProductStyle.colors.muted, style = MaterialTheme.typography.bodyLarge) },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProductButton(dismiss, onDismiss, outlined = true)
                ProductButton(confirm, onConfirm, destructive = destructive)
            }
        },
    )
}
