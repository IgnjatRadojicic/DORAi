package dorai.server.ai.services

import dorai.shared.model.ServiceType

/** ICT service types of the register templates (CIR (EU) 2024/2956, Annex III). The classifier may answer only these or Unknown. */
object ServiceTypes {
    val names: Map<ServiceType, String> = mapOf(
        ServiceType.S01 to "ICT project management",
        ServiceType.S02 to "ICT development",
        ServiceType.S03 to "ICT help desk and first level support",
        ServiceType.S04 to "ICT security management services",
        ServiceType.S05 to "Provision of data",
        ServiceType.S06 to "Data analysis",
        ServiceType.S07 to "ICT, facilities and hosting services (excluding cloud services)",
        ServiceType.S08 to "Computation (excluding cloud)",
        ServiceType.S09 to "Non-cloud data storage",
        ServiceType.S10 to "Telecom carrier",
        ServiceType.S11 to "Network infrastructure",
        ServiceType.S12 to "Hardware and physical devices",
        ServiceType.S13 to "Software licencing (excluding SaaS)",
        ServiceType.S14 to "ICT operation management (including maintenance)",
        ServiceType.S15 to "ICT consulting",
        ServiceType.S16 to "ICT risk management",
        ServiceType.S17 to "Cloud services: IaaS",
        ServiceType.S18 to "Cloud services: PaaS",
        ServiceType.S19 to "Cloud services: SaaS",
    )

    /** "S18", "s18" or "Unknown" to the enum; anything else is null. */
    fun parse(text: String): ServiceType? {
        val value = text.trim()
        if (value.equals("unknown", ignoreCase = true)) return ServiceType.Unknown
        return names.keys.firstOrNull { it.name.equals(value, ignoreCase = true) }
    }

    /** The closed list as the prompt shows it. */
    fun promptList(): String = names.entries.joinToString("; ") { "${it.key.name} = ${it.value}" } + "; Unknown"
}
