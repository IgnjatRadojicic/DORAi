package dorai.app.review

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dorai.app.components.*
import dorai.app.theme.ProductStyle
import kotlin.math.roundToInt

@Composable
fun ReviewScreen(
    state: ReviewState,
    onGenerate: () -> Unit,
    onRefresh: () -> Unit,
    onAccept: (String) -> Unit,
    onReject: (String) -> Unit,
    modifier: Modifier = Modifier,
    canDecide: Boolean = true,
) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 64.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp)) {
        PageHeading("AI review") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ProductButton("Refresh", onRefresh, outlined = true, enabled = !state.busy, icon = ProductIcon.REFRESH)
                if (canDecide) ProductButton(if (state.generating) "Asking the AI..." else "Ask the AI", onGenerate, enabled = !state.busy, icon = ProductIcon.BOOK)
            }
        }
        state.suggestions?.let {
            MetricGrid(listOf(
                Metric("To review", state.pending.size, "", ProductIcon.BOOK, state.pending.isNotEmpty()),
                Metric("Accepted", it.count { s -> s.status == ReviewStatus.ACCEPTED }, "", ProductIcon.CHECK),
                Metric("Rejected", it.count { s -> s.status == ReviewStatus.REJECTED }, "", ProductIcon.CLOSE),
            ))
        }
        state.error?.let { if (state.suggestions != null) ErrorView(it) }
        when {
            state.generating && state.suggestions.isNullOrEmpty() -> ProductPanel { LoadingView("Asking the AI...") }
            state.loading && state.suggestions == null -> ProductPanel { LoadingView("Loading suggestions...") }
            state.error != null && state.suggestions == null -> ProductPanel { Box(Modifier.padding(24.dp)) { ErrorView(state.error, onRefresh) } }
            state.suggestions.isNullOrEmpty() -> ProductPanel {
                EmptyView("No suggestions", "Ask the AI to suggest service types and business functions for resources that have none.", ProductIcon.BOOK)
            }
            else -> {
                SuggestionGroup("To review", state.pending, state, canDecide, onAccept, onReject)
                SuggestionGroup("Decided", state.decided, state, canDecide, onAccept, onReject)
            }
        }
    }
}

@Composable
private fun SuggestionGroup(
    title: String,
    suggestions: List<ReviewSuggestion>,
    state: ReviewState,
    canDecide: Boolean,
    onAccept: (String) -> Unit,
    onReject: (String) -> Unit,
) {
    if (suggestions.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MetaText("${uiText(title)} (${suggestions.size})")
        suggestions.sortedBy { it.resourceName }.forEach { suggestion ->
            SuggestionCard(suggestion, deciding = state.deciding == suggestion.id, enabled = canDecide && !state.busy, onAccept, onReject)
        }
    }
}

@Composable
private fun SuggestionCard(
    suggestion: ReviewSuggestion,
    deciding: Boolean,
    enabled: Boolean,
    onAccept: (String) -> Unit,
    onReject: (String) -> Unit,
) {
    ProductPanel {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(suggestion.resourceName, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                when (suggestion.status) {
                    ReviewStatus.PENDING -> ProvenanceBadge(Provenance.AI_SUGGESTED)
                    ReviewStatus.ACCEPTED -> ProvenanceBadge(Provenance.HUMAN_CONFIRMED)
                    ReviewStatus.REJECTED -> OutlineChip("rejected", ProductStyle.colors.red, dot = true)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlineChip(suggestion.fieldLabel ?: suggestion.field.label)
                Text(listOfNotNull(suggestion.value, suggestion.valueLabel).joinToString(" "), style = MaterialTheme.typography.bodyLarge)
                suggestion.confidence?.let { MetaText("${(it * 100).roundToInt()}% ${uiText("confidence")}") }
            }
            Text(suggestion.reason, style = MaterialTheme.typography.bodyMedium)
            suggestion.citations.forEach { CitationView(it) }
            if (suggestion.status == ReviewStatus.PENDING) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ProductButton(if (deciding) "Saving..." else "Accept", { onAccept(suggestion.id) }, enabled = enabled, icon = ProductIcon.CHECK)
                    ProductButton("Reject", { onReject(suggestion.id) }, outlined = true, enabled = enabled, icon = ProductIcon.CLOSE)
                }
            }
        }
    }
}
