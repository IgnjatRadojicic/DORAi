package dorai.app.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

@Composable
actual fun rememberProductFonts(): ProductFonts {
    val assets = LocalContext.current.assets
    return remember(assets) {
        ProductFonts(
            FontFamily(
                Font("fonts/host-400.ttf", assets, FontWeight.Normal),
                Font("fonts/host-500.ttf", assets, FontWeight.Medium),
                Font("fonts/host-600.ttf", assets, FontWeight.SemiBold),
                Font("fonts/host-700.ttf", assets, FontWeight.Bold),
            ),
            FontFamily(Font("fonts/mono-400.ttf", assets, FontWeight.Normal)),
        )
    }
}
