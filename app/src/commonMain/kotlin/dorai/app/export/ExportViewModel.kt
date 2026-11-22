package dorai.app.export

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import dorai.app.components.FrontendFailure
import dorai.app.components.displayMessage
import kotlinx.coroutines.CancellationException

enum class ExportFormat(val fileName: String, val mediaType: String) {
    CSV("register.zip", "application/zip"), PDF("compliance-report.pdf", "application/pdf")
}

/** [fileName]: the name the server gave the file; null keeps the format's default name. */
data class ExportDocument(val bytes: ByteArray, val mediaType: String, val fileName: String? = null)

fun interface ExportSource { suspend fun download(format: ExportFormat): ExportDocument }

data class ExportState(
    val busy: ExportFormat? = null,
    val savedLocation: String? = null,
    val cancelled: Boolean = false,
    val error: String? = null,
)

class ExportViewModel(
    private val source: ExportSource,
    private val files: WorkspaceFiles,
    private val canExport: () -> Boolean = { true },
) : ViewModel() {
    var state by mutableStateOf(ExportState())
        private set

    /**
     * True once the person agreed to export a register that has validation errors. Asked once per sign-in,
     * so saving drafts doesn't train people to click through; the banner on the page stays.
     */
    var errorsAcknowledged by mutableStateOf(false)
        private set

    fun acknowledgeErrors() { errorsAcknowledged = true }

    suspend fun download(format: ExportFormat) {
        if (state.busy != null) return
        if (!canExport()) {
            state = ExportState(error = "Your role does not allow exports.")
            return
        }
        state = ExportState(busy = format)
        try {
            val document = source.download(format)
            validateDocument(format, document)
            state = when (val result = files.save(document.fileName ?: format.fileName, format.mediaType, document.bytes)) {
                is SaveOutcome.Saved -> ExportState(savedLocation = result.location)
                SaveOutcome.Cancelled -> ExportState(cancelled = true)
            }
        } catch (cancelled: CancellationException) {
            state = ExportState()
            throw cancelled
        } catch (error: Exception) {
            state = ExportState(error = error.displayMessage("The export could not be saved. Check the connection and destination, then retry."))
        }
    }
}

internal fun validateDocument(format: ExportFormat, document: ExportDocument) {
    val type = document.mediaType.substringBefore(';').trim().lowercase()
    if (type != format.mediaType && type != "application/octet-stream") {
        throw FrontendFailure("The server returned an unexpected file type.")
    }
    val bytes = document.bytes
    if (bytes.isEmpty()) throw FrontendFailure("The server returned an empty file.")
    val signatureValid = when (format) {
        ExportFormat.PDF -> bytes.size >= 5 && bytes.copyOfRange(0, 5).decodeToString() == "%PDF-"
        ExportFormat.CSV -> bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte() &&
            ((bytes[2] == 3.toByte() && bytes[3] == 4.toByte()) || (bytes[2] == 5.toByte() && bytes[3] == 6.toByte()))
    }
    if (!signatureValid) throw FrontendFailure("The downloaded file is not a valid ${format.name} document.")
}
