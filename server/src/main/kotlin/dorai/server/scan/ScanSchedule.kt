package dorai.server.scan

import dorai.shared.model.ScanResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

/**
 * Scans in the background every `DORAI_SCAN_EVERY` (for example `24h`, `6h` or `PT12H`), so the register is
 * compared with the real cloud without anyone pressing Scan. Unset means off. The first run is one interval
 * after the start; a failed run is logged and the next one runs on time.
 */
object ScanSchedule {
    val MINIMUM: Duration = 1.hours

    /** The interval, or null when off. Throws on a value that is not a duration or is under [MINIMUM]. */
    fun fromEnv(get: (String) -> String? = System::getenv): Duration? {
        val raw = get("DORAI_SCAN_EVERY")?.trim()?.takeIf { it.isNotEmpty() && !it.equals("off", ignoreCase = true) } ?: return null
        val every = requireNotNull(Duration.parseOrNull(raw)) { "DORAI_SCAN_EVERY=$raw is not a duration like 24h or 6h." }
        require(every >= MINIMUM) { "DORAI_SCAN_EVERY must be at least $MINIMUM; cloud and cost APIs are rate limited." }
        return every
    }

    /** Runs [scan] every [every] until the calling coroutine is cancelled (the server stops). */
    suspend fun loop(every: Duration, scan: suspend () -> ScanResult, report: (Result<ScanResult>) -> Unit) {
        while (coroutineContext.isActive) {
            delay(every)
            val result = try {
                Result.success(scan())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
            report(result)
        }
    }
}
