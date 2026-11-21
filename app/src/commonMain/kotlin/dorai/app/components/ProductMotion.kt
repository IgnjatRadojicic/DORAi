package dorai.app.components

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

object ProductMotion {
    const val FEEDBACK = 120
    const val CONTENT = 200
    const val THEME = 260
}

/** Animate only section changes; ordinary data updates stay in the active view. */
@Composable
fun SectionTransition(section: String, modifier: Modifier = Modifier, content: @Composable (String) -> Unit) {
    AnimatedContent(
        targetState = section,
        modifier = modifier,
        transitionSpec = {
            (fadeIn(tween(ProductMotion.CONTENT, 50)) + slideInVertically(tween(ProductMotion.CONTENT, easing = FastOutSlowInEasing)) { it.coerceAtMost(36) / 3 })
                .togetherWith(fadeOut(tween(ProductMotion.FEEDBACK)))
                .using(SizeTransform(clip = false))
        },
        label = "workspace-section",
    ) { activeSection -> content(activeSection) }
}

@Composable
fun ColumnScope.ExpandDetails(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(tween(ProductMotion.CONTENT)) + fadeIn(tween(ProductMotion.CONTENT)),
        exit = shrinkVertically(tween(ProductMotion.CONTENT)) + fadeOut(tween(ProductMotion.FEEDBACK)),
        label = "finding-details",
    ) { content() }
}
