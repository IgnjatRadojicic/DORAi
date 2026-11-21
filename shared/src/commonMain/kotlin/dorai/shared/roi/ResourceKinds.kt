package dorai.shared.roi

/**
 * What a cloud resource type means for the register: whether it keeps data (B_02.02.0140: storage "even
 * temporarily", so queues and caches count) and whether it runs workloads. Matches Azure Resource Graph,
 * AWS and Terraform spellings by fragment, so new SKUs of a known service need no change here.
 */
object ResourceKinds {
    private val dataStores = listOf(
        // Azure
        "microsoft.storage/", "microsoft.sql/", "microsoft.dbfor", "microsoft.documentdb/", "microsoft.cache/",
        "microsoft.keyvault/", "microsoft.servicebus/", "microsoft.eventhub/", "microsoft.operationalinsights/",
        "microsoft.recoveryservices/", "microsoft.datafactory/", "microsoft.synapse/",
        "azurerm_storage", "azurerm_mssql", "azurerm_sql", "azurerm_postgresql", "azurerm_mysql", "azurerm_cosmosdb",
        "azurerm_redis", "azurerm_key_vault", "azurerm_servicebus", "azurerm_eventhub", "azurerm_log_analytics",
        // AWS
        "s3:", "aws_s3", "rds:", "aws_db", "aws_rds", "dynamodb:", "aws_dynamodb", "sqs:", "aws_sqs", "sns:", "aws_sns",
        "elasticache:", "aws_elasticache", "kms:", "aws_kms", "secretsmanager:", "aws_secretsmanager", "logs:",
        "aws_cloudwatch_log", "backup:", "aws_backup", "efs:", "aws_efs", "kinesis:", "aws_kinesis",
    )

    fun storesData(type: String): Boolean {
        val t = type.lowercase()
        return dataStores.any { t.startsWith(it) || t.contains(it) }
    }
}
