package dorai.shared.roi

import dorai.shared.model.BusinessFunction
import dorai.shared.model.ProvenanceLabel
import dorai.shared.model.Redundancy
import dorai.shared.model.Register
import dorai.shared.model.Resource
import dorai.shared.model.ValidationIssue
import dorai.shared.model.ValidationSeverity

/**
 * DORA Art. 12 against the register: does what the cloud actually does match what the entity declared for
 * its critical or important functions (B_06.01)? Deterministic and warning-only: the scan sees platform
 * backups and redundancy, not every backup a firm may run elsewhere.
 */
object ResilienceChecks {

    fun check(register: Register, resources: List<Resource>): List<ValidationIssue> = buildList {
        register.functions.forEach { f ->
            if (!isCritical(f)) return@forEach
            val own = resources.filter { it.functionId == f.id }
            if (own.isEmpty()) return@forEach
            val stores = own.filter { ResourceKinds.storesData(it.type) }

            val rpo = f.roi["B_06.01.0100"]?.value?.toIntOrNull()?.takeIf { it > 0 }
            if (rpo != null) {
                stores.filter { it.backup == null }.forEach {
                    add(warn("art12-no-backup", "B_06.01.0100", "${f.id} declares an RPO of $rpo h, but the scan sees no backup on ${it.name}.", f.id))
                }
                stores.filter { (it.backup?.intervalHours ?: 0) > rpo }.forEach {
                    add(warn("art12-rpo-not-met", "B_06.01.0100", "${f.id} declares an RPO of $rpo h, but ${it.name} is backed up every ${it.backup!!.intervalHours} h.", f.id))
                }
            }

            val single = stores.filter { it.redundancy == Redundancy.Local && it.replicaCountries.isEmpty() && it.backup?.geoRedundant != true }
            single.forEach {
                add(warn("art12-single-location", "B_06.01.0090", "Data of critical function ${f.id} on ${it.name} is kept in one location only, with no copy in another zone or region.", f.id))
            }
        }
    }

    /** The reviewer's criticality (B_06.01.0060, option 1) when set, otherwise the function's own flag. */
    fun isCritical(f: BusinessFunction): Boolean =
        f.roi["B_06.01.0060"]?.takeIf { it.provenance.label != ProvenanceLabel.AiSuggested }?.value?.let { it == "1" } ?: f.isCritical

    private fun warn(code: String, field: String, message: String, id: String) =
        ValidationIssue(ValidationSeverity.Warning, code, "B_06.01", field, message, id)
}
