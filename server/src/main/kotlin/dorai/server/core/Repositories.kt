package dorai.server.core

import dorai.shared.model.Register
import dorai.shared.model.Resource
import dorai.shared.model.Suggestion
import dorai.shared.model.SuggestionStatus
import kotlin.concurrent.Volatile
import kotlin.time.Instant

/** The register and the last scan. In memory in phase 1, PostgreSQL from phase 2. */
interface RegisterRepository {
    suspend fun register(): Register
    suspend fun resources(): List<Resource>
    suspend fun costs(): List<CostEntry>

    /** Replaces the stored scan with a new one. */
    suspend fun saveScan(resources: List<Resource>, costs: List<CostEntry>)

    /**
     * Changes the register in one step: [change] gets the current register and returns the new one with a
     * result for the caller. Edits run one after another, so none is lost.
     */
    suspend fun <T> updateRegister(change: (Register) -> Pair<Register, T>): T
}

/** A Terraform plan the firm checked: who sent it and when. */
data class CheckedPlan(val json: String, val checkedBy: String, val checkedAt: Instant)

/** The plans sent to `/check`, so the PDF report uses the firm's latest one. */
interface PlanRepository {
    suspend fun latest(): CheckedPlan?
    suspend fun save(plan: CheckedPlan)
}

/** Keeps only the latest plan, until the server restarts (fixture mode and tests). */
class InMemoryPlanRepository(initial: CheckedPlan? = null) : PlanRepository {
    @Volatile private var last: CheckedPlan? = initial
    override suspend fun latest() = last
    override suspend fun save(plan: CheckedPlan) { last = plan }
}

/** AI suggestions and the reviewer's decisions on them. */
interface SuggestionRepository {
    suspend fun all(): List<Suggestion>

    /** Pending suggestions, for one resource or (when null) for all. */
    suspend fun pending(resourceId: String? = null): List<Suggestion>

    suspend fun add(suggestions: List<Suggestion>)

    /** Accepts or rejects a pending suggestion; [by] is the reviewer's username. */
    suspend fun decide(id: String, status: SuggestionStatus, by: String): Decision

    sealed interface Decision {
        data class Done(val suggestion: Suggestion) : Decision
        data class AlreadyDecided(val suggestion: Suggestion) : Decision
        data object NotFound : Decision
    }
}
