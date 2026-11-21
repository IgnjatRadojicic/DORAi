package dorai.server.collect

/**
 * Live scan settings from the environment. Cloud credentials are not here: they come from the
 * standard SDK variables (AZURE_*, AWS_*) or a local `az login` / AWS profile.
 */
data class LiveConfig(
    val clouds: Set<Cloud>,
    /** Empty means every subscription the credential can read. */
    val azureSubscriptions: List<String> = emptyList(),
    val azureResourceGroup: String? = null,
    val awsRegions: List<String> = listOf(DEFAULT_AWS_REGION),
    /** Set for LocalStack (demo): the AWS collector then lists S3, DynamoDB, Lambda and SQS one by one. */
    val awsEndpoint: String? = null,
    val costCurrency: String = "EUR",
    /** Units of [costCurrency] per one unit of the key currency, e.g. USD to 0.92. */
    val exchangeRates: Map<String, Double> = emptyMap(),
) {
    enum class Cloud { Azure, Aws }

    companion object {
        const val DEFAULT_AWS_REGION = "eu-central-1"
        private val SAFE_NAME = Regex("^[A-Za-z0-9._()-]{1,90}$")

        /** Null when DORAI_DATA_SOURCE is not "live": the server then scans fixtures. */
        fun fromEnv(env: Map<String, String> = System.getenv()): LiveConfig? {
            fun get(name: String) = env[name]?.trim()?.takeIf { it.isNotEmpty() }
            fun list(name: String) = get(name)?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()

            when (val source = get("DORAI_DATA_SOURCE")?.lowercase() ?: "fixtures") {
                "fixtures" -> return null
                "live" -> Unit
                else -> error("DORAI_DATA_SOURCE must be fixtures or live, not '$source'.")
            }

            val clouds = (list("DORAI_CLOUDS").ifEmpty { listOf("azure", "aws") }).map {
                when (it.lowercase()) {
                    "azure" -> Cloud.Azure
                    "aws" -> Cloud.Aws
                    else -> error("DORAI_CLOUDS lists '$it'; known clouds are azure and aws.")
                }
            }.toSet()

            // The resource group goes into a Resource Graph query, so only Azure's own name characters pass.
            val group = get("DORAI_AZURE_RESOURCE_GROUP")
            require(group == null || SAFE_NAME.matches(group)) { "DORAI_AZURE_RESOURCE_GROUP is not a valid resource group name." }

            val currency = (get("DORAI_COST_CURRENCY") ?: "EUR").uppercase()
            val rates = env.filterKeys { it.startsWith("DORAI_FX_") }.mapNotNull { (key, value) ->
                val rate = value.trim().toDoubleOrNull()
                require(rate != null && rate > 0) { "$key must be a positive number." }
                key.removePrefix("DORAI_FX_").uppercase() to rate
            }.toMap()

            return LiveConfig(
                clouds = clouds,
                azureSubscriptions = list("DORAI_AZURE_SUBSCRIPTIONS"),
                azureResourceGroup = group,
                awsRegions = list("DORAI_AWS_REGIONS").ifEmpty { listOfNotNull(get("AWS_REGION") ?: DEFAULT_AWS_REGION) },
                awsEndpoint = get("DORAI_AWS_ENDPOINT"),
                costCurrency = currency,
                exchangeRates = rates,
            )
        }
    }
}
