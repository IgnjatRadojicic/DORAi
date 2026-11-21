package dorai.server.collect

import dorai.server.core.CostEntry
import dorai.server.core.CostProvider
import dorai.server.core.CostResult
import dorai.shared.model.CostSource
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.LocalDate
import org.slf4j.LoggerFactory

/**
 * Cost of every cloud in one currency. Each cloud has its own provider (live API or, for the LocalStack
 * demo, the fixture). A cloud whose cost call fails or that has no billing data contributes nothing, with a
 * warning; cost is best effort and never blocks a scan.
 */
class CombinedCostProvider(
    private val parts: List<Part>,
    private val converter: CurrencyConverter,
) : CostProvider {

    data class Part(val cloud: String, val provider: CostProvider)

    override suspend fun costs(from: LocalDate, to: LocalDate): CostResult {
        val results = parts.mapNotNull { part ->
            try {
                part.provider.costs(from, to)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.warn("{} cost unavailable, scanning without it: {}", part.cloud, e.message)
                null
            }
        }
        val sources = results.filter { it.entries.isNotEmpty() }.map { it.source }.toSet()
        val source = when {
            sources.isEmpty() || sources == setOf(CostSource.Live) -> CostSource.Live
            sources == setOf(CostSource.Fixture) -> CostSource.Fixture
            else -> CostSource.Mixed
        }
        return CostResult(converter.convert(results.flatMap { it.entries }), source)
    }

    private companion object {
        val log = LoggerFactory.getLogger(CombinedCostProvider::class.java)
    }
}

/**
 * Converts cost rows into one currency with fixed rates (DORAI_FX_<CODE>). Concentration adds amounts,
 * so a row that cannot be converted is left out with a warning rather than added in the wrong currency.
 */
class CurrencyConverter(private val target: String, private val rates: Map<String, Double>) {

    fun convert(entries: List<CostEntry>): List<CostEntry> {
        val missing = mutableSetOf<String>()
        val converted = entries.mapNotNull { e ->
            val currency = e.currency.uppercase()
            when {
                currency == target -> e.copy(currency = target)
                rates[currency] != null -> e.copy(amount = round2(e.amount * rates.getValue(currency)), currency = target)
                else -> null.also { missing += currency }
            }
        }
        if (missing.isNotEmpty()) {
            log.warn("Left out cost rows in {}: set {} to convert them to {}.", missing, missing.joinToString { "DORAI_FX_$it" }, target)
        }
        return converted
    }

    private companion object {
        val log = LoggerFactory.getLogger(CurrencyConverter::class.java)
    }
}
