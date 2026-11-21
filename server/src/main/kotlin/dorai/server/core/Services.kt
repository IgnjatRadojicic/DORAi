package dorai.server.core

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
    /** Null until export lands; the export routes then answer 501. */
    val exporter: RegisterExporter?,
)
