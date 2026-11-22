package dorai.app.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dorai.app.resources.Res
import dorai.app.resources.dorai_icon_small
import dorai.app.resources.dorai_logo
import dorai.shared.Dorai
import org.jetbrains.compose.resources.painterResource

/*
 * The designer's logo in its three forms (composeResources/drawable):
 * the light tile for small spots, the full logo with the wordmark for the sign-in card.
 * The full logo has no background of its own; a dark-theme variant is still to come from the designer.
 */

/** The app icon tile. For every spot under 40 dp, where the detailed mark would turn to mush. */
@Composable
fun BrandTile(size: Dp, modifier: Modifier = Modifier) {
    Image(painterResource(Res.drawable.dorai_icon_small), contentDescription = null, modifier.size(size))
}

/** Tile and product name: sidebar and top bar. */
@Composable
internal fun BrandMark(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        BrandTile(34.dp)
        Text(Dorai.NAME, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp)
    }
}

/** The full logo with the wordmark, centred: sign-in card. */
@Composable
internal fun BrandLogo(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Image(painterResource(Res.drawable.dorai_logo), contentDescription = Dorai.NAME, Modifier.width(112.dp),
            contentScale = ContentScale.FillWidth)
    }
}
