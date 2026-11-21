package dorai.app.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font

@Composable
actual fun rememberProductFonts(): ProductFonts = remember {
    ProductFonts(
        FontFamily(
            Font("fonts/host-400.ttf", FontWeight.Normal),
            Font("fonts/host-500.ttf", FontWeight.Medium),
            Font("fonts/host-600.ttf", FontWeight.SemiBold),
            Font("fonts/host-700.ttf", FontWeight.Bold),
        ),
        FontFamily(Font("fonts/mono-400.ttf", FontWeight.Normal)),
    )
}
