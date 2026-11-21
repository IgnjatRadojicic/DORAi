package dorai.server.fixtures

import dorai.server.core.AiAnswer
import dorai.server.core.CloudCollector
import dorai.server.core.CollectResult
import dorai.server.core.CostEntry
import dorai.server.core.CostProvider
import dorai.server.core.CostResult
import dorai.server.core.FindingExplainer
import dorai.server.core.FunctionSuggester
import dorai.server.core.RegisterRepository
import dorai.server.core.RegulationSearch
import dorai.server.core.ReportDrafter
import dorai.server.core.ResourceClassifier
import dorai.server.core.Services
import dorai.server.core.SuggestionRepository
import dorai.server.core.SuggestionRepository.Decision
import dorai.shared.model.BusinessFunction
import dorai.shared.model.Citation
import dorai.shared.model.CostSource
import dorai.shared.model.Finding
import dorai.shared.model.Register
import dorai.shared.model.Resource
import dorai.shared.model.ScanMode
import dorai.shared.model.ServiceType
import dorai.shared.model.Suggestion
import dorai.shared.model.SuggestionStatus
import dorai.shared.model.ValidationReport
import dorai.shared.rules.RegisterValidator
import dorai.shared.rules.RulesPlanChecker
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.LocalDate
import kotlin.time.Clock

/** Fixture wiring: FinPay data, in-memory storage, the real rules and an AI that never answers. */
fun fixtureServices(data: FixtureData = FixtureData.load()): Services {
    val costs = data.scan.costs.map { CostEntry(it.resourceId, it.provider, it.month, it.amount, it.currency) }
    return Services(
        collectors = data.resources.map { it.provider }.distinct().map { FixtureCollector(it, data.resources) },
        costs = FixtureCostProvider(costs),
        register = InMemoryRegisterRepository(data.register, data.resources, costs),
        suggestions = InMemorySuggestionRepository(data.suggestions),
        validator = RegisterValidator { ValidationReport(isValid = true, errorCount = 0, warningCount = 0, issues = emptyList()) },
        planChecker = RulesPlanChecker(),
        demoPlan = data.planEastus.decodeToString(),
        classifier = NoAi,
        functionSuggester = NoAi,
        explainer = NoAi,
        reportDrafter = NoAi,
        regulationSearch = NoAi,
        exporter = null,
    )
}

class FixtureCollector(override val provider: String, private val all: List<Resource>) : CloudCollector {
    override suspend fun collect() = CollectResult(all.filter { it.provider == provider }, ScanMode.Fixture)
}

/** Returns every fixture month regardless of the range, so the demo data never ages out. */
class FixtureCostProvider(private val entries: List<CostEntry>) : CostProvider {
    override suspend fun costs(from: LocalDate, to: LocalDate) = CostResult(entries, CostSource.Fixture)
}

class InMemoryRegisterRepository(
    private val register: Register,
    private var resources: List<Resource>,
    private var costs: List<CostEntry>,
) : RegisterRepository {
    private val lock = Mutex()
    override suspend fun register() = register
    override suspend fun resources() = lock.withLock { resources }
    override suspend fun costs() = lock.withLock { costs }
    override suspend fun saveScan(resources: List<Resource>, costs: List<CostEntry>) = lock.withLock {
        this.resources = resources
        this.costs = costs
    }
}

class InMemorySuggestionRepository(
    initial: List<Suggestion>,
    private val clock: Clock = Clock.System,
) : SuggestionRepository {
    private val lock = Mutex()
    private val items = LinkedHashMap<String, Suggestion>().apply { initial.forEach { put(it.id, it) } }

    override suspend fun all() = lock.withLock { items.values.toList() }

    override suspend fun pending(resourceId: String?) = lock.withLock {
        items.values.filter { it.status == SuggestionStatus.Pending && (resourceId == null || it.resourceId == resourceId) }
    }

    override suspend fun add(suggestions: List<Suggestion>) = lock.withLock { suggestions.forEach { items[it.id] = it } }

    override suspend fun decide(id: String, status: SuggestionStatus): Decision = lock.withLock {
        val current = items[id] ?: return@withLock Decision.NotFound
        if (current.status != SuggestionStatus.Pending) return@withLock Decision.AlreadyDecided(current)
        current.copy(status = status, decidedAt = clock.now()).also { items[id] = it }.let(Decision::Done)
    }
}

/** Stand-in until the AI lands: every question gets "insufficient evidence", search finds nothing. */
object NoAi : ResourceClassifier, FunctionSuggester, FindingExplainer, ReportDrafter, RegulationSearch {
    private val none = AiAnswer.InsufficientEvidence("AI is not configured on this server.")
    override suspend fun classify(resource: Resource): AiAnswer<ServiceType> = none
    override suspend fun suggest(resource: Resource, functions: List<BusinessFunction>): AiAnswer<String> = none
    override suspend fun explain(finding: Finding): AiAnswer<String> = none
    override suspend fun draft(register: Register, findings: List<Finding>): AiAnswer<String> = none
    override suspend fun search(query: String, limit: Int): List<Citation> = emptyList()
}
