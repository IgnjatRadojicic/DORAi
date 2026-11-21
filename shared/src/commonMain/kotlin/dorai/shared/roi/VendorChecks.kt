package dorai.shared.roi

import dorai.shared.model.Register
import dorai.shared.model.ValidationIssue
import dorai.shared.model.ValidationSeverity
import kotlinx.serialization.Serializable

/** What the entity paid one seller through a cloud bill (Marketplace, third-party resellers) over the last 12 months. */
@Serializable
data class VendorSpend(val seller: String, val cloud: String, val amount: Double, val currency: String)

/**
 * DORA Art. 28(3): the register holds every contractual arrangement on ICT services. A seller the cloud bill
 * pays but the register does not name is likely a missing provider. Warning only: some sellers are not ICT.
 */
object VendorChecks {

    fun check(register: Register, vendors: List<VendorSpend>): List<ValidationIssue> {
        val known = register.providers.flatMap { p ->
            listOfNotNull(p.name, p.roi["B_05.01.0050"]?.value, p.roi["B_05.01.0060"]?.value)
        }.map(::normalize).filter { it.isNotEmpty() }
        return vendors.groupBy { it.seller to it.cloud }
            .filter { (key, _) -> val n = normalize(key.first); n.isNotEmpty() && known.none { matches(it, n) } }
            .map { (key, rows) ->
                val amount = rows.sumOf { it.amount }
                ValidationIssue(
                    ValidationSeverity.Warning, "unregistered-vendor", "B_05.01", "B_05.01.0050",
                    "${key.first} was paid ${RoiTables.money(amount)} ${rows.first().currency} over 12 months through the ${key.second} bill, " +
                        "but is not a provider in the register (DORA Art. 28(3)).",
                    key.first,
                )
            }
    }

    private fun matches(a: String, b: String) =
        a == b || (minOf(a.length, b.length) >= 4 && (a.contains(b) || b.contains(a)))

    /** Lowercase, no punctuation, no legal form (Ltd, GmbH, N.V.), so names from different sources compare. */
    fun normalize(name: String): String =
        name.lowercase().replace(Regex("[^a-z0-9 ]"), " ").split(' ')
            .filter { it.isNotBlank() && it !in LEGAL_FORMS }.joinToString(" ")

    private val LEGAL_FORMS = setOf(
        "inc", "ltd", "llc", "limited", "gmbh", "ag", "sa", "sarl", "sas", "bv", "nv", "plc", "corp", "corporation",
        "co", "company", "srl", "spa", "oy", "ab", "as", "aps", "kg", "doo", "the",
    )
}
