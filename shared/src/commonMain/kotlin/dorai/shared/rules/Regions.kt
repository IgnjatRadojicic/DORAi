package dorai.shared.rules

/**
 * Cloud region to country (ISO 3166-1 alpha-2) for Azure and AWS, and the EU/EEA check.
 * Register data locations are country codes, so a resource location is always resolved here before comparing.
 * Unknown regions resolve to null and are never assumed to be in the EU.
 */
object Regions {

    /** EU-27 plus the EEA members Iceland, Liechtenstein and Norway. */
    val euEeaCountries: Set<String> = setOf(
        "AT", "BE", "BG", "HR", "CY", "CZ", "DK", "EE", "FI", "FR", "DE", "GR", "HU", "IE",
        "IT", "LV", "LT", "LU", "MT", "NL", "PL", "PT", "RO", "SK", "SI", "ES", "SE",
        "IS", "LI", "NO",
    )

    // Azure names never collide with AWS names (westeurope vs eu-west-1), so one table serves both.
    private val countryByRegion: Map<String, String> = mapOf(
        // Azure, Europe
        "westeurope" to "NL", "northeurope" to "IE",
        "francecentral" to "FR", "francesouth" to "FR",
        "germanywestcentral" to "DE", "germanynorth" to "DE",
        "swedencentral" to "SE", "swedensouth" to "SE",
        "italynorth" to "IT", "polandcentral" to "PL", "spaincentral" to "ES",
        "norwayeast" to "NO", "norwaywest" to "NO",
        "switzerlandnorth" to "CH", "switzerlandwest" to "CH",
        "uksouth" to "GB", "ukwest" to "GB",
        // Azure, Americas
        "eastus" to "US", "eastus2" to "US", "centralus" to "US", "northcentralus" to "US",
        "southcentralus" to "US", "westcentralus" to "US", "westus" to "US", "westus2" to "US", "westus3" to "US",
        "canadacentral" to "CA", "canadaeast" to "CA", "brazilsouth" to "BR",
        // Azure, Asia-Pacific
        "eastasia" to "HK", "southeastasia" to "SG", "japaneast" to "JP", "japanwest" to "JP",
        "australiaeast" to "AU", "centralindia" to "IN", "koreacentral" to "KR",
        // AWS, Europe
        "eu-central-1" to "DE", "eu-central-2" to "CH",
        "eu-west-1" to "IE", "eu-west-2" to "GB", "eu-west-3" to "FR",
        "eu-north-1" to "SE", "eu-south-1" to "IT", "eu-south-2" to "ES",
        // AWS, Americas
        "us-east-1" to "US", "us-east-2" to "US", "us-west-1" to "US", "us-west-2" to "US",
        "ca-central-1" to "CA", "sa-east-1" to "BR",
        // AWS, Asia-Pacific
        "ap-southeast-1" to "SG", "ap-southeast-2" to "AU", "ap-northeast-1" to "JP",
        "ap-northeast-2" to "KR", "ap-south-1" to "IN", "ap-east-1" to "HK",
    )

    /**
     * Country code for a region, or null when unknown or not regional (for example `global`).
     * Accepts Terraform display names ("West Europe") as well as slugs ("westeurope").
     */
    fun countryOf(region: String?): String? =
        region?.let { countryByRegion[it.replace(" ", "").trim().lowercase()] }

    /** True only for known regions in an EU/EEA country. */
    fun isEuEea(region: String?): Boolean = countryOf(region)?.let { it in euEeaCountries } ?: false
}
