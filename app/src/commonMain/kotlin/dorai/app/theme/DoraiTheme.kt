package dorai.app.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dorai.app.components.ProductMotion
import dorai.app.components.AppLanguage
import dorai.app.components.LocalAppLanguage

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class ProductFonts(val body: FontFamily, val mono: FontFamily)

@Composable
expect fun rememberProductFonts(): ProductFonts

data class ProductPalette(
    val background: Color, val panel: Color, val hover: Color, val ink: Color,
    val muted: Color, val line: Color, val accent: Color, val green: Color,
    val amber: Color, val red: Color, val note: Color, val noteBorder: Color,
    val noteInk: Color, val tableHead: Color,
)

private val dark = ProductPalette(
    Color(0xFF1E1F22), Color(0xFF2B2D30), Color(0xFF34363C), Color(0xFFDFE1E5),
    Color(0xFF9DA0A8), Color(0xFF3C3F45), Color(0xFF5AB9FC), Color(0xFF83C999),
    Color(0xFFE9B66F), Color(0xFFF2A197), Color(0xFF253440), Color(0xFF3B556D),
    Color(0xFFB7CCE1), Color(0xFF25272C),
)
private val light = ProductPalette(
    Color(0xFFF7F8FA), Color.White, Color(0xFFEEF1F5), Color(0xFF27282D),
    Color(0xFF626773), Color(0xFFDFE2E8), Color(0xFF1672B8), Color(0xFF207647),
    Color(0xFF875500), Color(0xFFB23B2C), Color(0xFFEAF3FE), Color(0xFFB8D1ED),
    Color(0xFF365674), Color(0xFFEEF2F8),
)

val LocalProductPalette = staticCompositionLocalOf { dark }
val LocalProductFonts = staticCompositionLocalOf { ProductFonts(FontFamily.SansSerif, FontFamily.Monospace) }

object ProductStyle {
    val colors: ProductPalette @Composable get() = LocalProductPalette.current
    val fonts: ProductFonts @Composable get() = LocalProductFonts.current
}

@Composable
fun DoraiTheme(mode: ThemeMode = ThemeMode.SYSTEM, language: AppLanguage = AppLanguage.ENGLISH, content: @Composable () -> Unit) {
    val darkMode = mode == ThemeMode.DARK || (mode == ThemeMode.SYSTEM && isSystemInDarkTheme())
    val palette = animatePalette(if (darkMode) dark else light)
    val fonts = rememberProductFonts()
    val base = if (darkMode) darkColorScheme() else lightColorScheme()
    val colors = base.copy(
        primary = palette.accent, onPrimary = palette.background,
        primaryContainer = palette.note, onPrimaryContainer = palette.ink,
        secondary = palette.green, onSecondary = palette.background,
        tertiary = palette.amber, error = palette.red,
        background = palette.background, onBackground = palette.ink,
        surface = palette.panel, onSurface = palette.ink,
        surfaceVariant = palette.hover, onSurfaceVariant = palette.muted,
        outline = palette.muted, outlineVariant = palette.line,
    )
    val type = Typography(
        headlineLarge = TextStyle(fontFamily = fonts.body, fontSize = 47.sp, lineHeight = 53.sp, fontWeight = FontWeight.Medium, letterSpacing = (-2).sp),
        headlineMedium = TextStyle(fontFamily = fonts.body, fontSize = 27.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.7).sp),
        titleLarge = TextStyle(fontFamily = fonts.body, fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
        titleMedium = TextStyle(fontFamily = fonts.body, fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium),
        titleSmall = TextStyle(fontFamily = fonts.body, fontSize = 13.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
        bodyLarge = TextStyle(fontFamily = fonts.body, fontSize = 14.sp, lineHeight = 23.sp),
        bodyMedium = TextStyle(fontFamily = fonts.body, fontSize = 13.sp, lineHeight = 21.sp),
        bodySmall = TextStyle(fontFamily = fonts.body, fontSize = 12.sp, lineHeight = 19.sp),
        labelLarge = TextStyle(fontFamily = fonts.body, fontSize = 13.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
        labelMedium = TextStyle(fontFamily = fonts.mono, fontSize = 11.sp, lineHeight = 17.sp),
        labelSmall = TextStyle(fontFamily = fonts.mono, fontSize = 10.sp, lineHeight = 15.sp, letterSpacing = 0.65.sp),
    )
    CompositionLocalProvider(LocalProductPalette provides palette, LocalProductFonts provides fonts, LocalAppLanguage provides language) {
        MaterialTheme(colorScheme = colors, typography = type, content = content)
    }
}

@Composable
private fun animatePalette(target: ProductPalette): ProductPalette {
    @Composable
    fun tone(value: Color, label: String): Color = animateColorAsState(value, tween(ProductMotion.THEME), label = label).value
    return ProductPalette(
        tone(target.background, "background"), tone(target.panel, "panel"), tone(target.hover, "hover"),
        tone(target.ink, "ink"), tone(target.muted, "muted"), tone(target.line, "line"),
        tone(target.accent, "accent"), tone(target.green, "green"), tone(target.amber, "amber"),
        tone(target.red, "red"), tone(target.note, "note"), tone(target.noteBorder, "note-border"),
        tone(target.noteInk, "note-ink"), tone(target.tableHead, "table-head"),
    )
}
