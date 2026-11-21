package dorai.app.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dorai.app.theme.ProductStyle

@Composable
fun LanguagePicker(onSelect: (AppLanguage) -> Unit) {
    val current = LocalAppLanguage.current
    var expanded by remember { mutableStateOf(false) }
    val description = uiText(UiCopy.LANGUAGE)
    Box {
        Surface(onClick = { expanded = true }, color = ProductStyle.colors.panel,
            shape = RoundedCornerShape(9.dp), border = BorderStroke(1.dp, ProductStyle.colors.line),
            modifier = Modifier.actionPointer().semantics { contentDescription = description; stateDescription = current.nativeName }) {
            Row(Modifier.height(36.dp).padding(horizontal = 11.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                LineIcon(ProductIcon.GLOBE, size = 16.dp)
                Text(current.code, fontFamily = ProductStyle.fonts.mono, fontSize = 11.sp)
                LineIcon(ProductIcon.CHEVRON, size = 12.dp)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, shape = RoundedCornerShape(12.dp),
            containerColor = ProductStyle.colors.panel, border = BorderStroke(1.dp, ProductStyle.colors.line)) {
            AppLanguage.entries.forEach { language ->
                DropdownMenuItem(
                    modifier = Modifier.actionPointer(),
                    text = {
                        Row(Modifier.width(175.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(language.nativeName, modifier = Modifier.weight(1f), fontWeight = if (language == current) FontWeight.Medium else FontWeight.Normal)
                            Text(language.code, fontFamily = ProductStyle.fonts.mono, fontSize = 10.sp, color = ProductStyle.colors.muted)
                        }
                    },
                    trailingIcon = { if (language == current) LineIcon(ProductIcon.CHECK, color = ProductStyle.colors.accent, size = 16.dp) else Spacer(Modifier.size(16.dp)) },
                    onClick = { expanded = false; onSelect(language) },
                )
            }
        }
    }
}
