package dorai.shared.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Entry names are the JSON values, so they keep the wire spelling ("High", "S18").

@Serializable
enum class Severity { Low, Medium, High }

@Serializable
enum class ProvenanceLabel {
    @SerialName("scanned") Scanned,
    @SerialName("planned") Planned,
    @SerialName("ai-suggested") AiSuggested,
    @SerialName("human-confirmed") HumanConfirmed,
}

@Serializable
enum class ChangeAction { None, Create, Update, Delete, Replace }

@Serializable
enum class ResourceSource { Live, Planned }

@Serializable
enum class ScanMode { Live, Fixture }

@Serializable
enum class CostSource { Live, Fixture, Mixed }

@Serializable
enum class Redundancy { Unknown, Local, Zone, Geo }

/** ICT service types S01 to S19 from the register templates (Annex III); S17 is IaaS, S18 PaaS. */
@Serializable
enum class ServiceType {
    Unknown, S01, S02, S03, S04, S05, S06, S07, S08, S09, S10,
    S11, S12, S13, S14, S15, S16, S17, S18, S19,
}

@Serializable
enum class SuggestionKind {
    ServiceType,
    BusinessFunction,

    /** A value for one RoI column, named in [Suggestion.column]; accepting it writes the value to the register. */
    RoiValue,
}

@Serializable
enum class SuggestionStatus { Pending, Accepted, Rejected }

@Serializable
enum class ValidationSeverity { Warning, Error }
