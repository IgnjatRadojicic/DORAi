package dorai.server.core

import dorai.server.auth.AuthService

import dorai.shared.rules.PlanChecker
import dorai.shared.rules.RegisterValidator

/**
 * Everything the routes call, wired by hand. Each lane swaps its fixture or fake
 * for the real implementation here, without touching routes or other lanes.
 */
data class Services(
    val collectors: List<CloudCollector>,
    val costs: CostProvider,
    val register: RegisterRepository,
    val suggestions: SuggestionRepository,
    val validator: RegisterValidator,
    val planChecker: PlanChecker,
    /** Terraform plan checked when `/check` gets no plan. */
    val demoPlan: String,
    val classifier: ResourceClassifier,
    val functionSuggester: FunctionSuggester,
    val explainer: FindingExplainer,
    val reportDrafter: ReportDrafter,
    val regulationSearch: RegulationSearch,
    /** Null only on a server built without export; the export routes then answer 501. */
    val exporter: RegisterExporter?,
    /** Null when auth is off (no database): every route is open, as in development. */
    val auth: AuthService? = null,
)
