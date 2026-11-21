package dorai.shared.rules

import dorai.shared.model.ChangeAction
import dorai.shared.model.DoraiJson
import dorai.shared.model.Redundancy
import dorai.shared.model.Resource
import dorai.shared.model.ResourceSource
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

const val FUNCTION_TAG = "dora-function"
const val CONTRACT_TAG = "dora-contract"

/**
 * Turns `terraform show -json` output into planned [Resource]s, the same model a live scan produces.
 * Pure: no I/O, no cloud, no AI. Plan JSON is untrusted input: only read, never executed.
 * Format: https://developer.hashicorp.com/terraform/internals/json-format
 */
object TerraformPlanReader {

    /**
     * One resource per managed resource change. Data sources, `read` actions and non-ICT types are skipped;
     * unchanged resources stay in with [ChangeAction.None] (the rules ignore them).
     * @throws InvalidPlanException when the text is not JSON or has no `resource_changes`.
     */
    fun read(planJson: String): List<Resource> {
        val plan = runCatching { DoraiJson.parseToJsonElement(planJson) }.getOrNull() as? JsonObject
            ?: throw InvalidPlanException("The plan is not valid JSON.")
        val changes = plan["resource_changes"] as? JsonArray
            ?: throw InvalidPlanException("Not a Terraform plan: 'resource_changes' is missing. Use `terraform show -json plan.bin`.")

        val providerKeyByAddress = (plan.obj("configuration").obj("root_module")?.get("resources") as? JsonArray).orEmpty()
            .mapNotNull { r -> r.str("address")?.let { address -> r.str("provider_config_key")?.let { address to it } } }
            .groupBy({ it.first }, { it.second })
            .mapValues { it.value.first() }

        return changes.mapNotNull { rc -> readChange(plan, providerKeyByAddress, rc) }
    }

    /** Terraform action list to [ChangeAction]; `read` and anything unknown give null (skipped). */
    fun toAction(actions: List<String>): ChangeAction? = when (actions) {
        listOf("no-op") -> ChangeAction.None
        listOf("create") -> ChangeAction.Create
        listOf("update") -> ChangeAction.Update
        listOf("delete") -> ChangeAction.Delete
        listOf("delete", "create"), listOf("create", "delete") -> ChangeAction.Replace
        else -> null
    }

    private fun readChange(plan: JsonObject, providerKeys: Map<String, String>, rc: JsonElement): Resource? {
        val address = rc.str("address") ?: return null
        val type = rc.str("type") ?: return null
        if (rc.str("mode") == "data" || ServiceTypeTable.isIgnored(type)) return null
        val change = rc.obj("change") ?: return null
        val actions = (change["actions"] as? JsonArray).orEmpty().mapNotNull { it.str() }
        val action = toAction(actions) ?: return null

        val before = change.obj("before")
        val after = change.obj("after")
        // On delete `after` is null: everything is read from `before`.
        val state = if (action == ChangeAction.Delete) before else after ?: before
        val providerName = rc.str("provider_name")
        val entry = ServiceTypeTable.find(type)
        val tags = tagsOf(after) ?: tagsOf(before) ?: emptyMap()
        val location = after.str("location") ?: before.str("location")
            ?: state.str("region")
            ?: providerRegion(plan, providerKeys, address, providerName)
            ?: ""

        return Resource(
            id = address,
            name = nameOf(state) ?: rc.str("name") ?: address,
            type = type,
            provider = entry?.provider ?: providerOf(providerName),
            location = location,
            country = Regions.countryOf(location),
            source = ResourceSource.Planned,
            changeAction = action,
            tags = tags,
            serviceType = entry?.code,
            redundancy = redundancyOf(type, state),
            previousRedundancy = if (action == ChangeAction.Update || action == ChangeAction.Replace) redundancyOf(type, before) else null,
            functionId = tags[FUNCTION_TAG],
            contractId = tags[CONTRACT_TAG],
        )
    }

    private fun nameOf(state: JsonObject?): String? =
        state.str("name") ?: state.str("identifier") ?: state.str("bucket") ?: state.str("function_name")

    private fun tagsOf(state: JsonObject?): Map<String, String>? =
        state.obj("tags")?.entries?.mapNotNull { (k, v) -> v.str()?.let { k to it } }?.toMap()

    // registry.terraform.io/hashicorp/aws -> aws
    private fun shortName(providerName: String?): String? = providerName?.substringAfterLast('/')

    private fun providerOf(providerName: String?): String = when (val short = shortName(providerName)) {
        "aws" -> ServiceTypeTable.AWS
        "azurerm" -> ServiceTypeTable.AZURE
        null -> "unknown"
        else -> short
    }

    /**
     * AWS resources carry no region: it is set on the provider block the resource uses (`provider_config_key`).
     * Count/for_each instances ("x[0]") share their block's key; resources without one use the default block.
     * The region is a literal or a single `var.x` reference; anything else is unknown, never a guess.
     */
    private fun providerRegion(plan: JsonObject, providerKeys: Map<String, String>, address: String, providerName: String?): String? {
        val configs = plan.obj("configuration").obj("provider_config") ?: return null
        val key = providerKeys[address.substringBefore('[')] ?: shortName(providerName) ?: return null
        val region = configs.obj(key).obj("expressions").obj("region") ?: return null
        region.str("constant_value")?.let { return it }
        val reference = (region["references"] as? JsonArray)?.firstOrNull()?.str() ?: return null
        if (!reference.startsWith("var.")) return null
        return plan.obj("variables").obj(reference.removePrefix("var.")).str("value")
    }

    /** Replication level from plan attributes; types without a known setting are [Redundancy.Unknown]. */
    private fun redundancyOf(type: String, attributes: JsonObject?): Redundancy = when (type) {
        "azurerm_storage_account" -> when (attributes.str("account_replication_type")?.uppercase()) {
            "LRS" -> Redundancy.Local
            "ZRS" -> Redundancy.Zone
            "GRS", "RAGRS", "GZRS", "RAGZRS" -> Redundancy.Geo
            else -> Redundancy.Unknown
        }
        "azurerm_postgresql_flexible_server" -> when {
            attributes == null -> Redundancy.Unknown
            (attributes["geo_redundant_backup_enabled"] as? JsonPrimitive)?.booleanOrNull == true -> Redundancy.Geo
            // high_availability is a list block in plan JSON: [{ "mode": "ZoneRedundant", ... }]
            (attributes["high_availability"] as? JsonArray).orEmpty().any { it.str("mode") == "ZoneRedundant" } -> Redundancy.Zone
            else -> Redundancy.Local
        }
        else -> Redundancy.Unknown
    }
}

// Small readers over untrusted JSON: a missing field, null or wrong type gives null, never an exception.

private fun JsonElement?.obj(name: String): JsonObject? = ((this as? JsonObject)?.get(name)).takeUnless { it is JsonNull } as? JsonObject

private fun JsonElement?.str(name: String): String? = (this as? JsonObject)?.get(name).str()

private fun JsonElement?.str(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content
