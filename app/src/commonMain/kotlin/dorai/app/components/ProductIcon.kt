package dorai.app.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dorai.app.theme.ProductStyle

enum class ProductIcon { UPLOAD, FILE, SHIELD, ALERT, CHECK, ARROW, DOWNLOAD, CLOSE, CHEVRON, BOOK, LOCATION, FILTER, REFRESH, CLOUD, SUN, MOON, ARCHIVE, GLOBE }

@Composable
fun LineIcon(icon: ProductIcon, modifier: Modifier = Modifier, color: Color = ProductStyle.colors.muted, size: Dp = 18.dp) {
    Canvas(modifier.size(size)) {
        val u = this.size.width / 24f
        val stroke = Stroke(1.7f * u, cap = StrokeCap.Round)
        fun line(x: Float, y: Float, x2: Float, y2: Float) = drawLine(color, Offset(x * u, y * u), Offset(x2 * u, y2 * u), strokeWidth = stroke.width, cap = StrokeCap.Round)
        fun path(vararg points: Pair<Float, Float>, closed: Boolean = false) {
            val shape = Path().apply {
                moveTo(points[0].first * u, points[0].second * u)
                points.drop(1).forEach { lineTo(it.first * u, it.second * u) }
                if (closed) close()
            }
            drawPath(shape, color, style = stroke)
        }
        when (icon) {
            ProductIcon.GLOBE -> {
                drawCircle(color, 9f * u, center, style = stroke)
                drawOval(color, Offset(8f * u, 3f * u), Size(8f * u, 18f * u), style = stroke)
                line(3f, 12f, 21f, 12f)
            }
            ProductIcon.UPLOAD, ProductIcon.DOWNLOAD -> {
                line(4f, 16f, 4f, 21f); line(4f, 21f, 20f, 21f); line(20f, 21f, 20f, 16f)
                if (icon == ProductIcon.UPLOAD) { line(12f, 16f, 12f, 3f); path(7f to 8f, 12f to 3f, 17f to 8f) }
                else { line(12f, 3f, 12f, 16f); path(7f to 11f, 12f to 16f, 17f to 11f) }
            }
            ProductIcon.FILE, ProductIcon.ARCHIVE -> {
                path(5f to 2f, 14f to 2f, 20f to 8f, 20f to 22f, 5f to 22f, closed = true)
                path(14f to 2f, 14f to 8f, 20f to 8f)
                if (icon == ProductIcon.ARCHIVE) { line(10f, 10f, 10f, 19f); line(12f, 12f, 12f, 17f) }
                else { line(8f, 13f, 16f, 13f); line(8f, 17f, 14f, 17f) }
            }
            ProductIcon.SHIELD -> { path(12f to 2f, 21f to 6f, 20f to 14f, 17f to 19f, 12f to 22f, 7f to 19f, 4f to 14f, 3f to 6f, closed = true); path(8f to 12f, 11f to 15f, 16f to 9f) }
            ProductIcon.ALERT, ProductIcon.CHECK -> {
                drawCircle(color, 9f * u, Offset(12f * u, 12f * u), style = stroke)
                if (icon == ProductIcon.ALERT) { line(12f, 7f, 12f, 12f); line(12f, 16f, 12f, 16.1f) }
                else path(8f to 12f, 11f to 15f, 16f to 9f)
            }
            ProductIcon.ARROW -> { line(4f, 12f, 20f, 12f); path(14f to 6f, 20f to 12f, 14f to 18f) }
            ProductIcon.CLOSE -> { line(6f, 6f, 18f, 18f); line(6f, 18f, 18f, 6f) }
            ProductIcon.CHEVRON -> path(7f to 9f, 12f to 14f, 17f to 9f)
            ProductIcon.BOOK -> { path(3f to 3f, 9f to 3f, 12f to 5f, 15f to 3f, 21f to 3f, 21f to 20f, 15f to 20f, 12f to 22f, 9f to 20f, 3f to 20f, closed = true); line(12f, 5f, 12f, 22f) }
            ProductIcon.LOCATION -> { drawCircle(color, 5f * u, Offset(12f * u, 8f * u), style = stroke); path(5f to 11f, 12f to 22f, 19f to 11f) }
            ProductIcon.FILTER -> { line(3f, 5f, 21f, 5f); line(6f, 12f, 18f, 12f); line(10f, 19f, 14f, 19f) }
            ProductIcon.REFRESH -> { drawArc(color, 40f, 285f, false, Offset(4f * u, 4f * u), Size(16f * u, 16f * u), style = stroke); path(20f to 3f, 20f to 9f, 14f to 9f) }
            ProductIcon.CLOUD -> path(5f to 18f, 2f to 15f, 3f to 10f, 7f to 9f, 9f to 4f, 15f to 4f, 18f to 10f, 21f to 11f, 22f to 15f, 19f to 18f, closed = true)
            ProductIcon.SUN -> {
                drawCircle(color, 4f * u, center, style = stroke)
                line(12f, 1f, 12f, 4f); line(12f, 20f, 12f, 23f)
                line(1f, 12f, 4f, 12f); line(20f, 12f, 23f, 12f)
                line(4f, 4f, 6f, 6f); line(18f, 18f, 20f, 20f)
                line(4f, 20f, 6f, 18f); line(18f, 6f, 20f, 4f)
            }
            ProductIcon.MOON -> {
                val crescent = Path().apply {
                    moveTo(20.5f * u, 13.2f * u)
                    cubicTo(19.8f * u, 17.8f * u, 16.2f * u, 21f * u, 11.8f * u, 21f * u)
                    cubicTo(6.8f * u, 21f * u, 3f * u, 17.2f * u, 3f * u, 12.2f * u)
                    cubicTo(3f * u, 7.8f * u, 6.2f * u, 4.2f * u, 10.8f * u, 3.5f * u)
                    cubicTo(8.2f * u, 7.2f * u, 8.6f * u, 11.5f * u, 11.2f * u, 14f * u)
                    cubicTo(13.8f * u, 16.6f * u, 18.2f * u, 16.2f * u, 20.5f * u, 13.2f * u)
                    close()
                }
                drawPath(crescent, color, style = stroke)
            }
        }
    }
}
