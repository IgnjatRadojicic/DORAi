package dorai.app.validation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import dorai.app.components.displayMessage
import kotlinx.coroutines.CancellationException

enum class IssueSeverity { ERROR, WARNING }

data class RegisterIssue(
    val severity: IssueSeverity,
    val code: String,
    val template: String,
    val field: String,
    val message: String,
    val entityId: String? = null,
)

data class RegisterValidation(
    val isValid: Boolean,
    val errorCount: Int,
    val warningCount: Int,
    val issues: List<RegisterIssue>,
) {
    val groups: Map<String, List<RegisterIssue>> get() = issues.sortedWith(
        compareBy<RegisterIssue> { it.severity.ordinal }.thenBy { it.template }.thenBy { it.field }
    ).groupBy { it.template }
}

fun interface ValidationSource { suspend fun validateRegister(): RegisterValidation }

data class ValidationState(
    val busy: Boolean = false,
    val report: RegisterValidation? = null,
    val error: String? = null,
)

class ValidationViewModel(private val source: ValidationSource) : ViewModel() {
    var state by mutableStateOf(ValidationState())
        private set

    suspend fun refresh() {
        if (state.busy) return
        // Discard old validation so a failed refresh cannot imply the register is valid.
        state = ValidationState(busy = true)
        try {
            state = ValidationState(report = source.validateRegister())
        } catch (cancelled: CancellationException) {
            state = ValidationState()
            throw cancelled
        } catch (error: Exception) {
            state = ValidationState(error = error.displayMessage("Validation could not be loaded. Check the connection and retry."))
        }
    }
}
