package dorai.shared.rules

import dorai.shared.model.ServiceType

/** One row of [ServiceTypeTable]: [code] is null when the type is known but its code is left to the AI and a person. */
data class ServiceTypeEntry(val provider: String, val code: ServiceType?) {
    val needsAi: Boolean get() = code == null
}

/**
 * Fixed Annex III service types for the Azure and AWS resource types in use (S17 cloud IaaS, S18 cloud PaaS).
 * Each type is listed under every spelling it arrives in: Azure Resource Graph, azurerm Terraform,
 * AWS scan and aws Terraform. The rules and the CLI read only this table, so a plan check needs no AI.
 * Types not in the table go to the AI classifier.
 */
object ServiceTypeTable {
    const val AZURE = "Azure"
    const val AWS = "AWS"

    private val entries: Map<String, ServiceTypeEntry> = buildMap {
        fun add(code: ServiceType?, provider: String, vararg types: String) = types.forEach { put(it.lowercase(), ServiceTypeEntry(provider, code)) }

        // Azure, fixed
        add(ServiceType.S17, AZURE, "microsoft.storage/storageaccounts", "azurerm_storage_account")
        add(ServiceType.S18, AZURE, "microsoft.dbforpostgresql/flexibleservers", "azurerm_postgresql_flexible_server")
        // Azure, AI suggests and a person confirms
        add(
            null, AZURE,
            "microsoft.web/sites", "azurerm_linux_web_app", "azurerm_linux_function_app",
            "microsoft.web/serverfarms", "azurerm_service_plan",
            "microsoft.servicebus/namespaces", "azurerm_servicebus_namespace",
            "microsoft.keyvault/vaults", "azurerm_key_vault",
            "microsoft.cognitiveservices/accounts", "azurerm_cognitive_account",
            "microsoft.operationalinsights/workspaces", "azurerm_log_analytics_workspace",
        )
        // AWS, fixed
        add(ServiceType.S17, AWS, "s3:bucket", "aws_s3_bucket")
        add(
            ServiceType.S18, AWS,
            "aws_db_instance", "dynamodb:table", "aws_dynamodb_table",
            "lambda:function", "aws_lambda_function", "sqs:queue", "aws_sqs_queue",
        )
    }

    // Not ICT services: identity setup and Terraform helper providers. Never classified, never a finding.
    private val ignoredTypes = setOf("aws_iam_role")
    private val ignoredPrefixes = listOf("random_", "null_", "time_", "tls_", "local_", "archive_")

    /** The row for a resource type, or null when the type is unknown (the AI classifies it). */
    fun find(type: String): ServiceTypeEntry? = entries[type.trim().lowercase()]

    /** True for types that are not ICT services; scan and rules skip them. */
    fun isIgnored(type: String): Boolean {
        val t = type.lowercase()
        return t in ignoredTypes || ignoredPrefixes.any { t.startsWith(it) }
    }
}

/**
 * Which legal vendor sells each cloud, so a resource can be matched to a register provider (B_05.01).
 * The register names the contracting entity ("Microsoft Ireland Operations Ltd"), a resource only its cloud
 * ("Azure"), so the match is on the vendor name prefix. A cloud not listed here is always a new provider.
 */
object CloudVendors {
    private val vendorByCloud = mapOf(
        ServiceTypeTable.AZURE.lowercase() to "Microsoft",
        ServiceTypeTable.AWS.lowercase() to "Amazon Web Services",
        "google" to "Google",
    )

    fun vendorOf(cloud: String): String? = vendorByCloud[cloud.lowercase()]
}
