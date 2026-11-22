package dorai.app.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** Corner radius by size of the thing: controls, then cards and panels, then dialogs and sheets. Chips are pills. */
object ProductShapes {
    val control = RoundedCornerShape(8.dp)
    val card = RoundedCornerShape(12.dp)
    val dialog = RoundedCornerShape(16.dp)
    val pill = RoundedCornerShape(50)
}

/** The only gaps the screens use. */
object ProductSpacing {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 32.dp

    /** Visible height of a button or field; the touch area stays 48 dp. */
    val control = 40.dp
}
