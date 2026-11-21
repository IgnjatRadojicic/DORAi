package dorai.shared.roi

/**
 * The Register of Information templates and columns of CIR (EU) 2024/2956, Annex I, as data.
 * The exporter, the readiness check and the app forms all read this one table. Closed lists keep the CIR
 * option keys ("1", "2", "S17", ...); [TaxonomyCodes] maps them to the EBA taxonomy codes for the export.
 */
object RoiSpec {
    val templates: List<RoiTemplate> = listOf(
        template("B_01.01", "Entity maintaining the register of information") {
            col("0010", "LEI of the entity maintaining the register", RoiType.Lei)
            col("0020", "Name of the entity", RoiType.Text)
            col("0030", "Country of the entity", RoiType.Country)
            col("0040", "Type of entity", RoiType.Choice, options = ENTITY_TYPES)
            col("0050", "Competent authority", RoiType.Text, RoiNeed.IfReporting)
            col("0060", "Date of the reporting", RoiType.Date, RoiNeed.IfReporting)
        },
        template("B_01.02", "List of entities within the scope of the register of information") {
            col("0010", "LEI of the entity", RoiType.Lei)
            col("0020", "Name of the entity", RoiType.Text)
            col("0030", "Country of the entity", RoiType.Country)
            col("0040", "Type of entity", RoiType.Choice, options = ENTITY_TYPES)
            col("0050", "Hierarchy of the entity within the group", RoiType.Choice, options = HIERARCHY)
            col("0060", "LEI of the direct parent undertaking", RoiType.Lei)
            col("0070", "Date of last update", RoiType.Date)
            col("0080", "Date of integration in the register of information", RoiType.Date)
            col("0090", "Date of deletion in the register of information", RoiType.Date)
            col("0100", "Currency", RoiType.Currency, RoiNeed.IfTotalAssets)
            col("0110", "Value of total assets", RoiType.Monetary, RoiNeed.IfFinancialEntity)
        },
        template("B_01.03", "List of branches") {
            col("0010", "Identification code of the branch", RoiType.Text)
            col("0020", "LEI of the head office of the branch", RoiType.Lei)
            col("0030", "Name of the branch", RoiType.Text)
            col("0040", "Country of the branch", RoiType.Country)
        },
        template("B_02.01", "Contractual arrangements: general information") {
            col("0010", "Contractual arrangement reference number", RoiType.Text)
            col("0020", "Type of contractual arrangement", RoiType.Choice, options = ARRANGEMENT_TYPES)
            col("0030", "Overarching contractual arrangement reference number", RoiType.Text, RoiNeed.IfSubsequent)
            col("0040", "Currency of the amount reported in B_02.01.0050", RoiType.Currency)
            col("0050", "Annual expense or estimated cost of the contractual arrangement for the past year", RoiType.Monetary)
        },
        template("B_02.02", "Contractual arrangements: specific information") {
            col("0010", "Contractual arrangement reference number", RoiType.Text)
            col("0020", "LEI of the entity making use of the ICT service(s)", RoiType.Lei)
            col("0030", "Identification code of the ICT third-party service provider", RoiType.Text)
            col("0040", "Type of code to identify the ICT third-party service provider", RoiType.CodeType)
            col("0050", "Function identifier", RoiType.FunctionId)
            col("0060", "Type of ICT services", RoiType.Choice, options = SERVICE_TYPES)
            col("0070", "Start date of the contractual arrangement", RoiType.Date)
            col("0080", "End date of the contractual arrangement", RoiType.Date)
            col("0090", "Reason of the termination or ending of the contractual arrangement", RoiType.Choice, RoiNeed.IfTerminated, TERMINATION_REASONS)
            col("0100", "Notice period for the entity making use of the ICT service(s)", RoiType.NaturalNumber, RoiNeed.IfCritical)
            col("0110", "Notice period for the ICT third-party service provider", RoiType.NaturalNumber, RoiNeed.IfCritical)
            col("0120", "Country of the governing law of the contractual arrangement", RoiType.Country, RoiNeed.IfCritical)
            col("0130", "Country of provision of the ICT services", RoiType.Country, RoiNeed.IfCritical)
            col("0140", "Storage of data", RoiType.YesNo, RoiNeed.IfCritical)
            col("0150", "Location of the data at rest (storage)", RoiType.Country, RoiNeed.IfStoresData)
            col("0160", "Location of management of the data (processing)", RoiType.Country, RoiNeed.IfProcessesData)
            col("0170", "Sensitiveness of the data stored by the ICT third-party service provider", RoiType.Choice, RoiNeed.IfStoresAndCritical, LOW_MEDIUM_HIGH)
            col("0180", "Level of reliance on the ICT service supporting the critical or important function", RoiType.Choice, RoiNeed.IfCritical, RELIANCE)
        },
        template("B_02.03", "List of intra-group contractual arrangements") {
            col("0010", "Contractual arrangement reference number", RoiType.Text)
            col("0020", "Contractual arrangement reference number linked to B_02.03.0010", RoiType.Text)
            col("0030", "Link", RoiType.YesNo)
        },
        template("B_03.01", "Entities signing the contractual arrangements for receiving ICT service(s)") {
            col("0010", "Contractual arrangement reference number", RoiType.Text)
            col("0020", "LEI of the entity signing the contractual arrangement", RoiType.Lei)
            col("0030", "Link", RoiType.YesNo)
        },
        template("B_03.02", "ICT third-party service providers signing the contractual arrangements") {
            col("0010", "Contractual arrangement reference number", RoiType.Text)
            col("0020", "Identification code of the ICT third-party service provider", RoiType.Text)
            col("0030", "Type of code to identify the ICT third-party service provider", RoiType.CodeType)
        },
        template("B_03.03", "Entities signing the contractual arrangements for providing ICT service(s)") {
            col("0010", "Contractual arrangement reference number", RoiType.Text)
            col("0020", "LEI of the entity providing ICT services", RoiType.Lei)
            col("0031", "Link", RoiType.YesNo)
        },
        template("B_04.01", "Entities making use of the ICT services") {
            col("0010", "Contractual arrangement reference number", RoiType.Text)
            col("0020", "LEI of the entity making use of the ICT service(s)", RoiType.Lei)
            col("0030", "Nature of the entity making use of the ICT service(s)", RoiType.Choice, options = BRANCH_NATURE)
            col("0040", "Identification code of the branch", RoiType.Text, RoiNeed.IfBranch)
        },
        template("B_05.01", "ICT third-party service providers") {
            col("0010", "Identification code of the ICT third-party service provider", RoiType.Text)
            col("0020", "Type of code to identify the ICT third-party service provider", RoiType.CodeType)
            col("0030", "Additional identification code of the ICT third-party service provider", RoiType.Text, RoiNeed.Optional)
            col("0040", "Type of the additional identification code", RoiType.CodeType, RoiNeed.IfAdditionalCode)
            col("0050", "Legal name of the ICT third-party service provider", RoiType.Text)
            col("0060", "Name of the ICT third-party service provider in Latin alphabet", RoiType.Text)
            col("0070", "Type of person of the ICT third-party service provider", RoiType.Choice, options = PERSON_TYPES)
            col("0080", "Country of the ICT third-party service provider's headquarters", RoiType.Country)
            col("0090", "Currency of the amount reported in B_05.01.0100", RoiType.Currency, RoiNeed.IfAmountReported)
            col("0100", "Total annual expense or estimated cost of the ICT third-party service provider", RoiType.Monetary, RoiNeed.IfDirectProvider)
            col("0110", "Identification code of the provider's ultimate parent undertaking", RoiType.Text, RoiNeed.IfNotUltimateParent)
            col("0120", "Type of code to identify the provider's ultimate parent undertaking", RoiType.CodeType, RoiNeed.IfNotUltimateParent)
        },
        template("B_05.02", "ICT service supply chains") {
            col("0010", "Contractual arrangement reference number", RoiType.Text)
            col("0020", "Type of ICT services", RoiType.Choice, options = SERVICE_TYPES)
            col("0030", "Identification code of the ICT third-party service provider", RoiType.Text)
            col("0040", "Type of code to identify the ICT third-party service provider", RoiType.CodeType)
            col("0050", "Rank", RoiType.NaturalNumber)
            col("0060", "Identification code of the recipient of sub-contracted ICT services", RoiType.Text, RoiNeed.IfRankAboveOne)
            col("0070", "Type of code to identify the recipient of sub-contracted ICT services", RoiType.CodeType, RoiNeed.IfRankAboveOne)
        },
        template("B_06.01", "Functions identification") {
            col("0010", "Function identifier", RoiType.FunctionId)
            col("0020", "Licensed activity", RoiType.Choice, options = TaxonomyCodeLists.licensedActivities)
            col("0030", "Function name", RoiType.Text)
            col("0040", "LEI of the entity", RoiType.Lei)
            col("0050", "Criticality or importance assessment", RoiType.Choice, options = CRITICALITY)
            col("0060", "Reasons for criticality or importance", RoiType.Text, RoiNeed.Optional)
            col("0070", "Date of the last assessment of criticality or importance", RoiType.Date)
            col("0080", "Recovery time objective of the function (hours)", RoiType.NaturalNumber)
            col("0090", "Recovery point objective of the function (hours)", RoiType.NaturalNumber)
            col("0100", "Impact of discontinuing the function", RoiType.Choice, options = IMPACT)
        },
        template("B_07.01", "Assessment of the ICT services") {
            col("0010", "Contractual arrangement reference number", RoiType.Text)
            col("0020", "Identification code of the ICT third-party service provider", RoiType.Text)
            col("0030", "Type of code to identify the ICT third-party service provider", RoiType.CodeType)
            col("0040", "Type of ICT services", RoiType.Choice, options = SERVICE_TYPES)
            col("0050", "Substitutability of the ICT third-party service provider", RoiType.Choice, options = SUBSTITUTABILITY)
            col("0060", "Reason where the provider is not or hardly substitutable", RoiType.Choice, RoiNeed.IfHardToSubstitute, NOT_SUBSTITUTABLE_REASONS)
            col("0070", "Date of the last audit on the ICT third-party service provider", RoiType.Date)
            col("0080", "Existence of an exit plan", RoiType.YesNo)
            col("0090", "Possibility of reintegration of the contracted ICT service", RoiType.Choice, options = REINTEGRATION)
            col("0100", "Impact of discontinuing the ICT services", RoiType.Choice, options = IMPACT)
            col("0110", "Are there alternative ICT third-party service providers identified?", RoiType.Choice, options = ALTERNATIVES)
            col("0120", "Identification of alternative ICT third-party service providers", RoiType.Text, RoiNeed.Optional)
        },
        template("B_99.01", "Definitions from entities making use of the ICT services") {
            col("0010", "Column code", RoiType.Text, prefix = "C")
            col("0020", "Column name", RoiType.Text, prefix = "C")
            col("0030", "Option", RoiType.Text, prefix = "C")
            col("0040", "Description or internal definition of the option", RoiType.Text, prefix = "C")
        },
    )

    private val byCode: Map<String, RoiColumn> = templates.flatMap { it.columns }.associateBy { it.code }

    fun template(code: String): RoiTemplate = templates.first { it.code == code }

    fun column(code: String): RoiColumn? = byCode[code]

    /** The closed lists B_99.01 asks the entity to define, in the CIR's order. */
    val definedOptionSets: List<String> = listOf("B_02.01.0020", "B_02.02.0170", "B_06.01.0100", "B_07.01.0050", "B_07.01.0090", "B_07.01.0100")
}

data class RoiTemplate(val code: String, val name: String, val columns: List<RoiColumn>)

/**
 * One column. [code] is the full code (`B_02.02.0100`), [header] the CSV header (`c0100`).
 * [options] is the closed list for [RoiType.Choice] and [RoiType.YesNo]: CIR key to label.
 */
data class RoiColumn(
    val code: String,
    val header: String,
    val name: String,
    val type: RoiType,
    val need: RoiNeed,
    val options: List<RoiOption> = emptyList(),
) {
    val template: String get() = code.substringBeforeLast('.')
    fun label(key: String?): String? = options.firstOrNull { it.key == key }?.label
}

data class RoiOption(val key: String, val label: String)

enum class RoiType { Text, Lei, Country, Currency, Date, Monetary, NaturalNumber, YesNo, Choice, CodeType, FunctionId }

/** When a column must be filled, as the CIR's fill-in option says. */
enum class RoiNeed(val description: String) {
    Mandatory("Mandatory"),
    Optional("Optional"),
    IfReporting("Mandatory in case of reporting"),
    IfTotalAssets("Mandatory only if the value of total assets is reported"),
    IfFinancialEntity("Mandatory if the entity is a financial entity"),
    IfSubsequent("Mandatory for a subsequent or associated arrangement"),
    IfTerminated("Mandatory if the contractual arrangement is terminated"),
    IfCritical("Mandatory if the ICT service supports a critical or important function"),
    IfStoresData("Mandatory if 'Yes' is reported in B_02.02.0140"),
    IfProcessesData("Mandatory if the ICT service is based on or foresees data processing"),
    IfStoresAndCritical("Mandatory if the provider stores data and the service supports a critical or important function"),
    IfBranch("Mandatory if the entity making use of the service is a branch"),
    IfAdditionalCode("Mandatory if B_05.01.0030 is reported"),
    IfAmountReported("Mandatory if B_05.01.0100 is reported"),
    IfDirectProvider("Mandatory if the provider is a direct ICT third-party service provider"),
    IfNotUltimateParent("Mandatory if the provider is not the ultimate parent undertaking"),
    IfRankAboveOne("Mandatory, not applicable for rank 1"),
    IfHardToSubstitute("Mandatory if 'not substitutable' or 'highly complex substitutability' is reported"),
}

private class TemplateBuilder(private val template: String) {
    val columns = mutableListOf<RoiColumn>()
    fun col(number: String, name: String, type: RoiType, need: RoiNeed = RoiNeed.Mandatory, options: List<RoiOption> = emptyList(), prefix: String = "") {
        val opts = if (type == RoiType.YesNo) YES_NO else options
        columns += RoiColumn("$template.$prefix$number", "c$number", name, type, need, opts)
    }
}

private fun template(code: String, name: String, build: TemplateBuilder.() -> Unit) =
    RoiTemplate(code, name, TemplateBuilder(code).apply(build).columns.toList())

private fun numbered(vararg labels: String) = labels.mapIndexed { i, l -> RoiOption("${i + 1}", l) }

private val YES_NO = listOf(RoiOption("true", "Yes"), RoiOption("false", "No"))

private val ENTITY_TYPES = numbered(
    "Credit institution", "Payment institution", "Account information service provider", "Electronic money institution",
    "Investment firm", "Crypto-asset service provider", "Issuer of asset-referenced tokens", "Central securities depository",
    "Central counterparty", "Trading venue", "Trade repository", "Manager of alternative investment funds",
    "Management company", "Data reporting service provider", "Insurance or reinsurance undertaking",
    "Insurance, reinsurance or ancillary insurance intermediary", "Institution for occupational retirement provision",
    "Credit rating agency", "Administrator of critical benchmarks", "Crowdfunding service provider",
    "Securitisation repository", "Other financial entity", "Non-financial entity: ICT intra-group service provider",
    "Non-financial entity: other",
)

private val HIERARCHY = numbered(
    "Ultimate parent undertaking in the consolidation",
    "Parent undertaking of a sub-consolidated part",
    "Subsidiary, not a parent of a sub-consolidated part",
    "Not part of a group",
    "Service provider to which the entity outsources all its operational activities",
)

private val ARRANGEMENT_TYPES = numbered("Standalone arrangement", "Overarching or master arrangement", "Subsequent or associated arrangement")

val SERVICE_TYPES: List<RoiOption> = listOf(
    "ICT project management", "ICT development", "ICT help desk and first level support", "ICT security management services",
    "Provision of data", "Data analysis", "ICT, facilities and hosting services (excluding cloud services)", "Computation",
    "Non-cloud data storage", "Telecom carrier", "Network infrastructure", "Hardware and physical devices",
    "Software licensing (excluding SaaS)", "ICT operation management", "ICT consulting", "ICT risk management",
    "Cloud services: IaaS", "Cloud services: PaaS", "Cloud services: SaaS",
).mapIndexed { i, l -> RoiOption("S" + "${i + 1}".padStart(2, '0'), l) }

private val TERMINATION_REASONS = numbered(
    "Termination not for cause: expired or ended and not renewed",
    "Termination for cause: provider in breach of law, regulation or contract",
    "Termination for cause: impediments of the provider capable of altering the supported function",
    "Termination for cause: weaknesses in the management and security of sensitive data",
    "Termination following a request by a competent authority",
    "Other",
)

private val LOW_MEDIUM_HIGH = numbered("Low", "Medium", "High")

private val RELIANCE = numbered("Not significant", "Low reliance", "Material reliance", "Full reliance")

private val BRANCH_NATURE = numbered("Branch of a financial entity", "Not a branch")

private val PERSON_TYPES = numbered("Legal person, excluding individuals acting in business capacity", "Individual acting in a business capacity")

private val CRITICALITY = numbered("Yes", "No", "Assessment not performed")

private val IMPACT = numbered("Low", "Medium", "High", "Assessment not performed")

private val SUBSTITUTABILITY = numbered("Not substitutable", "Highly complex substitutability", "Medium complexity in terms of substitutability", "Easily substitutable")

private val NOT_SUBSTITUTABLE_REASONS = numbered(
    "Lack of real alternatives, even partial",
    "Difficulties in migrating data and workloads",
    "Both reasons",
)

private val REINTEGRATION = numbered("Easy", "Difficult", "Highly complex")

private val ALTERNATIVES = listOf(RoiOption("1", "Yes"), RoiOption("2", "No"), RoiOption("7", "Assessment not performed"))

