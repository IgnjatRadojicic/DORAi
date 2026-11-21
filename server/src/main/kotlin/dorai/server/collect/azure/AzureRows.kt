package dorai.server.collect.azure

import dorai.server.collect.cloudResource
import dorai.server.collect.round2
import dorai.server.core.CostEntry
import dorai.server.core.Vendors
import dorai.shared.model.BackupInfo
import dorai.shared.model.Redundancy
import dorai.shared.model.Resource
import dorai.shared.model.SecurityAlert
import dorai.shared.rules.ServiceTypeTable
import kotlinx.datetime.LocalDate

/** Resource Graph rows to resources. Pure, so it is tested without Azure. */
object AzureRows {

    const val SUBSCRIPTIONS = "ResourceContainers | where type =~ 'microsoft.resources/subscriptions' | project subscriptionId"

    /** Unhealthy Defender for Cloud assessments, one row per resource and recommendation. */
    const val ALERTS = "securityresources | where type == 'microsoft.security/assessments'" +
        " | where tostring(properties.status.code) == 'Unhealthy'" +
        " | project alertId = id, resourceId = tolower(tostring(properties.resourceDetails.Id))," +
        " title = tostring(properties.displayName), severity = tostring(properties.metadata.severity)"

    /** Alert rows by lowercased resource id. */
    fun alerts(rows: List<Map<String, Any?>>): Map<String, List<SecurityAlert>> =
        rows.mapNotNull { row ->
            val resource = row.str("resourceId").ifBlank { return@mapNotNull null }
            resource.lowercase() to SecurityAlert(
                source = "Defender for Cloud",
                id = row.str("alertId"),
                title = row.str("title").ifBlank { "Unnamed recommendation" },
                severity = row.str("severity").uppercase().ifBlank { "UNKNOWN" },
            )
        }.groupBy({ it.first }, { it.second })

    /** [resourceGroup] is checked by LiveConfig (Azure name characters only) before it reaches the query. */
    fun query(resourceGroup: String?) = buildString {
        appendLine("Resources")
        if (resourceGroup != null) appendLine("| where resourceGroup =~ '$resourceGroup'")
        appendLine("| project id, name, type, location, tags, sku, kind,")
        appendLine("    ha = tostring(properties.highAvailability.mode),")
        appendLine("    geoBackup = tostring(properties.backup.geoRedundantBackup),")
        appendLine("    backupDays = toint(properties.backup.backupRetentionDays),")
        appendLine("    secondaryLocation = tostring(properties.secondaryLocation),")
        append("    replicaLocations = properties.locations")
    }

    /**
     * One row to a resource. Every field is optional. Azure ids and types are case-insensitive; both are
     * lowercased so they join with Cost Management ids and the type table.
     */
    fun toResource(row: Map<String, Any?>): Resource {
        val type = row.str("type").lowercase()
        return cloudResource(
            id = row.str("id").lowercase(),
            name = row.str("name"),
            type = type,
            provider = ServiceTypeTable.AZURE,
            location = row.str("location"),
            tags = (row["tags"] as? Map<*, *>).orEmpty()
                .mapNotNull { (k, v) -> if (k is String && v is String) k to v else null }.toMap(),
            redundancy = redundancy(type, row),
            replicaRegions = replicaRegions(row),
            backup = backup(type, row),
        )
    }

    private fun redundancy(type: String, row: Map<String, Any?>): Redundancy = when (type) {
        "microsoft.storage/storageaccounts" ->
            when ((row["sku"] as? Map<*, *>)?.get("name")?.toString()?.uppercase()?.substringAfterLast('_')) {
                "LRS" -> Redundancy.Local
                "ZRS" -> Redundancy.Zone
                "GRS", "RAGRS", "GZRS", "RAGZRS" -> Redundancy.Geo
                else -> Redundancy.Unknown
            }
        "microsoft.dbforpostgresql/flexibleservers" -> when {
            row.str("geoBackup").equals("Enabled", ignoreCase = true) -> Redundancy.Geo
            row.str("ha").equals("ZoneRedundant", ignoreCase = true) -> Redundancy.Zone
            else -> Redundancy.Local
        }
        else -> Redundancy.Unknown
    }

    /** Regions holding copies: a storage account's secondary region, Cosmos DB's other write or read regions. */
    internal fun replicaRegions(row: Map<String, Any?>): List<String> = buildList {
        row.str("secondaryLocation").takeIf { it.isNotBlank() }?.let(::add)
        (row["replicaLocations"] as? List<*>).orEmpty().forEach { l ->
            ((l as? Map<*, *>)?.get("locationName") as? String)?.takeIf { it.isNotBlank() }?.let(::add)
        }
    }.distinct()

    /**
     * Backups the platform keeps by itself. PostgreSQL flexible server restores to any point in its
     * retention (the transaction log is backed up at least every 5 minutes), so the interval is under an hour.
     */
    internal fun backup(type: String, row: Map<String, Any?>): BackupInfo? = when (type) {
        "microsoft.dbforpostgresql/flexibleservers" -> BackupInfo(
            intervalHours = 1,
            retentionDays = (row["backupDays"] as? Number)?.toInt(),
            geoRedundant = row.str("geoBackup").equals("Enabled", ignoreCase = true),
        )
        else -> null
    }

    private fun Map<String, Any?>.str(key: String) = (this[key] as? String).orEmpty()
}

/** Cost Management rows to cost entries. Pure, so it is tested without Azure. */
object AzureCostRows {

    /**
     * Columns are found by name, never by position: the API has returned Cost, BillingMonth (or UsageDate),
     * ResourceId and Currency in different orders across versions. Unreadable rows are skipped.
     */
    fun parse(columns: List<String>, rows: List<List<Any?>>): List<CostEntry> =
        parse(columns, rows, "ResourceId") { it.lowercase() }

    /** Marketplace spend grouped by PublisherName: one row per seller and month, id `vendor:<seller>`. */
    fun parseVendors(columns: List<String>, rows: List<List<Any?>>): List<CostEntry> =
        parse(columns, rows, "PublisherName") { Vendors.id(it) }
            .groupBy { Triple(it.resourceId, it.month, it.currency) }
            .map { (k, r) -> r.first().copy(amount = round2(r.sumOf { it.amount })) }

    private fun parse(columns: List<String>, rows: List<List<Any?>>, idColumn: String, idOf: (String) -> String): List<CostEntry> {
        fun col(vararg names: String) = columns.indexOfFirst { c -> names.any { it.equals(c, ignoreCase = true) } }
        val cost = col("Cost", "totalCost", "PreTaxCost")
        val month = col("BillingMonth", "UsageDate")
        val id = col(idColumn)
        val currency = col("Currency")
        if (cost < 0 || month < 0 || id < 0) return emptyList()

        return rows.mapNotNull { row ->
            val amount = (row.getOrNull(cost) as? Number)?.toDouble() ?: row.getOrNull(cost)?.toString()?.toDoubleOrNull()
            val m = row.getOrNull(month)?.let(::monthOf)
            val resourceId = row.getOrNull(id)?.toString()?.trim().orEmpty()
            if (amount == null || m == null || resourceId.isEmpty()) return@mapNotNull null
            CostEntry(
                resourceId = idOf(resourceId),
                provider = ServiceTypeTable.AZURE,
                month = m,
                amount = round2(amount),
                currency = (if (currency >= 0) row.getOrNull(currency)?.toString() else null)?.ifBlank { null } ?: "EUR",
            )
        }
    }

    /** BillingMonth is an ISO date ("2026-11-01T00:00:00"); UsageDate is a number like 20261101. */
    internal fun monthOf(value: Any): LocalDate? {
        val text = if (value is Number) value.toLong().toString() else value.toString().trim()
        val digits = if (text.length >= 7 && text[4] == '-') text.substring(0, 4) + text.substring(5, 7) else text.take(6)
        val year = digits.take(4).toIntOrNull() ?: return null
        val m = digits.drop(4).take(2).toIntOrNull()?.takeIf { it in 1..12 } ?: return null
        return LocalDate(year, m, 1)
    }
}
