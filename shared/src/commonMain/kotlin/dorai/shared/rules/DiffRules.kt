package dorai.shared.rules

import dorai.shared.model.BusinessFunction
import dorai.shared.model.ChangeAction
import dorai.shared.model.Contract
import dorai.shared.model.Finding
import dorai.shared.model.Redundancy
import dorai.shared.model.Register
import dorai.shared.model.Resource
import dorai.shared.model.ServiceType
import dorai.shared.model.Severity

/**
 * One deterministic register rule. Severity and templates are constants of the rule, never computed and
 * never decided by AI. The engine passes only resources that change (action is not [ChangeAction.None]).
 */
interface DiffRule {
    val ruleId: String
    fun evaluate(ctx: DiffContext, r: Resource): List<Finding>
}

/** The register indexed once for the rules. Rules read only this and the planned resource. */
class DiffContext(val register: Register) {
    private val functions = register.functions.associateBy { it.id.lowercase() }
    private val contracts = register.contracts.associateBy { it.id.lowercase() }

    fun function(id: String?): BusinessFunction? = id?.let { functions[it.lowercase()] }
    fun contract(id: String?): Contract? = id?.let { contracts[it.lowercase()] }

    /** True when the resource's cloud vendor is a registered ICT provider (B_05.01). */
    fun isRegisteredProvider(cloud: String): Boolean {
        val vendor = CloudVendors.vendorOf(cloud) ?: return false
        return register.providers.any { it.name.startsWith(vendor, ignoreCase = true) }
    }

    /** Country codes the register declares for a contract's services (B_02.02 data location), upper case. */
    fun dataLocations(contract: Contract): Set<String> =
        contract.services.mapNotNull { it.dataLocation.value?.uppercase() }.toSet()

    companion object {
        /** True when the resource exists after apply (create, update, replace). */
        fun exists(r: Resource): Boolean =
            r.changeAction == ChangeAction.Create || r.changeAction == ChangeAction.Update || r.changeAction == ChangeAction.Replace

        /** "us-east-1 (US)" for finding text; "unknown" when there is no location. */
        fun describe(r: Resource): String =
            if (r.location.isEmpty()) "unknown" else "${r.location} (${Regions.countryOf(r.location) ?: "?"})"

        /** True when the change takes a multi-region resource down to a single region. */
        fun losesMultiRegion(r: Resource): Boolean =
            r.previousRedundancy == Redundancy.Geo && (r.redundancy == Redundancy.Local || r.redundancy == Redundancy.Zone)
    }
}

/** HIGH: the resource comes from a provider not in B_05.01, so there is no Art. 30 contract and no Art. 28(4) assessment. */
object NewProviderRule : DiffRule {
    override val ruleId = "new-provider"

    override fun evaluate(ctx: DiffContext, r: Resource): List<Finding> {
        if (!DiffContext.exists(r) || ctx.isRegisteredProvider(r.provider)) return emptyList()
        return listOf(
            Finding(
                ruleId = ruleId,
                severity = Severity.High,
                title = "New ICT provider",
                resourceAddress = r.id,
                templates = listOf("B_05.01", "B_02.01"),
                detail = "${r.provider} is not a registered ICT provider: add it to B_05.01 with a contract in B_02.01 before this ships.",
                location = DiffContext.describe(r),
                functionId = r.functionId,
            ),
        )
    }
}

/**
 * B_02.02 data location (Art. 30). HIGH when the country is outside the EU/EEA, or differs from every data
 * location the register declares for the contract. MEDIUM when the location is unknown: it cannot be shown to be in the EU.
 */
object DataLocationRule : DiffRule {
    override val ruleId = "data-location"

    override fun evaluate(ctx: DiffContext, r: Resource): List<Finding> {
        if (!DiffContext.exists(r)) return emptyList()
        val country = Regions.countryOf(r.location)
            ?: return listOf(
                finding(r, Severity.Medium, "Data location unknown",
                    "The location of ${r.name} cannot be resolved to a country, so it cannot be shown to be in the EU/EEA."),
            )
        if (!Regions.isEuEea(r.location)) {
            return listOf(finding(r, Severity.High, "Data location outside EU/EEA", "${r.name} would store data in $country, outside the EU/EEA."))
        }
        val contract = ctx.contract(r.contractId) ?: return emptyList()
        val declared = ctx.dataLocations(contract)
        if (declared.isEmpty() || country in declared) return emptyList()
        return listOf(
            finding(r, Severity.High, "Data location differs from register",
                "${r.name} would store data in $country; the register declares ${declared.sorted().joinToString(", ")} for ${contract.id}."),
        )
    }

    private fun finding(r: Resource, severity: Severity, title: String, detail: String) = Finding(
        ruleId = ruleId,
        severity = severity,
        title = title,
        resourceAddress = r.id,
        templates = listOf("B_02.02"),
        detail = detail,
        location = DiffContext.describe(r),
        functionId = r.functionId,
    )
}

/**
 * HIGH, B_06.01: a resource tagged with a critical or important function (Art. 3(22)) is deleted,
 * loses its multi-region redundancy, or is placed outside the EU/EEA.
 */
object CriticalFunctionRule : DiffRule {
    override val ruleId = "critical-function"

    override fun evaluate(ctx: DiffContext, r: Resource): List<Finding> {
        val function = ctx.function(r.functionId)?.takeIf { it.isCritical } ?: return emptyList()
        val country = Regions.countryOf(r.location)
        val reason = when {
            r.changeAction == ChangeAction.Delete -> "is deleted"
            DiffContext.losesMultiRegion(r) -> "loses its multi-region redundancy"
            DiffContext.exists(r) && country != null && !Regions.isEuEea(r.location) -> "is placed outside the EU/EEA ($country)"
            else -> return emptyList()
        }
        return listOf(
            Finding(
                ruleId = ruleId,
                severity = Severity.High,
                title = "Critical function at risk",
                resourceAddress = r.id,
                templates = listOf("B_06.01"),
                detail = "${r.name} supports ${function.id} ${function.name} (critical) and $reason.",
                location = DiffContext.describe(r),
                functionId = function.id,
            ),
        )
    }
}

/** MEDIUM, B_02.02: a new or changed resource without both DORA tags cannot be linked to the register. Deletes are exempt. */
object UntaggedResourceRule : DiffRule {
    override val ruleId = "untagged"

    override fun evaluate(ctx: DiffContext, r: Resource): List<Finding> {
        if (!DiffContext.exists(r) || (r.functionId != null && r.contractId != null)) return emptyList()
        val missing = listOfNotNull(
            FUNCTION_TAG.takeIf { r.functionId == null },
            CONTRACT_TAG.takeIf { r.contractId == null },
        ).joinToString(" and ")
        return listOf(
            Finding(
                ruleId = ruleId,
                severity = Severity.Medium,
                title = "Untagged resource",
                resourceAddress = r.id,
                templates = listOf("B_02.02"),
                detail = "${r.name} has no $missing tag, so it cannot be linked to a contract and function in the register.",
                location = DiffContext.describe(r),
            ),
        )
    }
}

/**
 * MEDIUM, B_02.02: a new resource needs a register row that does not exist yet. A row is one service of a contract
 * for one function, so the rule looks for the same contract, Annex III code and function.
 * The code alone is too coarse: almost every cloud service is S17 or S18.
 */
object NewServiceTypeRule : DiffRule {
    override val ruleId = "new-service-type"

    override fun evaluate(ctx: DiffContext, r: Resource): List<Finding> {
        val code = r.serviceType?.takeIf { it != ServiceType.Unknown }
        val contract = ctx.contract(r.contractId)
        val functionId = r.functionId
        if (r.changeAction != ChangeAction.Create || code == null || contract == null || functionId == null) return emptyList()
        val hasRow = contract.services.any {
            it.serviceType.value.equals(code.name, ignoreCase = true) && it.functionId.value.equals(functionId, ignoreCase = true)
        }
        if (hasRow) return emptyList()
        return listOf(
            Finding(
                ruleId = ruleId,
                severity = Severity.Medium,
                title = "New service in contract",
                resourceAddress = r.id,
                templates = listOf("B_02.02"),
                detail = "${contract.id} has no ${code.name} service for $functionId: add a B_02.02 row for ${r.name}.",
                location = DiffContext.describe(r),
                functionId = functionId,
            ),
        )
    }
}

/**
 * The diff engine: a pure function from (register, planned changes) to findings. Same input, same output,
 * so `dorai check` in CI and `POST /api/check` always agree.
 */
class RegisterDiff(private val rules: List<DiffRule> = DEFAULT_RULES) {

    fun diff(register: Register, planned: List<Resource>): List<Finding> {
        val ctx = DiffContext(register)
        return planned
            .filter { it.changeAction != ChangeAction.None }
            .flatMap { r -> rules.flatMap { it.evaluate(ctx, r) } }
            // Stable order for the PR comment: HIGH first, then by resource, then by rule.
            .sortedWith(compareByDescending<Finding> { it.severity }.thenBy { it.resourceAddress }.thenBy { it.ruleId })
            .map { f ->
                val function = ctx.function(f.functionId)
                f.copy(functionName = function?.name, criticalFunction = function?.isCritical ?: false)
            }
    }

    companion object {
        val DEFAULT_RULES: List<DiffRule> =
            listOf(NewProviderRule, DataLocationRule, CriticalFunctionRule, UntaggedResourceRule, NewServiceTypeRule)
    }
}

/** The real [PlanChecker]: read the plan, run the rules. No AI, so CI gets the same answer every time. */
class RulesPlanChecker(private val engine: RegisterDiff = RegisterDiff()) : PlanChecker {
    override fun check(planJson: String, register: Register): List<Finding> =
        engine.diff(register, TerraformPlanReader.read(planJson))
}
