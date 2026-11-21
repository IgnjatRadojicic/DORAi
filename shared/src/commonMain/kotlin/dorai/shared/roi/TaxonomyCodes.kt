package dorai.shared.roi

/**
 * What the EBA DORA taxonomy fixes and the CIR does not: the module the report points to, the package
 * layout and the codes closed lists are reported with. The member codes are generated into
 * [TaxonomyCodeLists] by scripts/taxonomy-codes.py, so a new release changes only these two files.
 *
 * [verified]: on 2026-11-21 Arelle 2.47.5 validated the FinPay package offline against this release (with the
 * Eurofiling base files) with no errors; 93 EBA validation rules ran. Set it back to false when the
 * release or the export changes and the check has not been run again.
 */
object TaxonomyCodes {
    const val verified: Boolean = true

    /** EBA reporting framework release that holds the DORA module (4.0, errata 5; module version 1.1.0). */
    const val frameworkRelease = "4.0"

    /** Entry point of the DORA module that report.json extends. */
    const val moduleEntryPoint = "http://www.eba.europa.eu/eu/fr/xbrl/crr/fws/dora/4.0/mod/dora.json"

    const val reportPackageType = "https://xbrl.org/report-package/2023"
    const val xbrlCsvType = "https://xbrl.org/2021/xbrl-csv"

    /** Decimals of monetary values. The CIR asks for amounts in units (not thousands); cents are kept. */
    const val decimalsMonetary = 2

    /** Decimals of whole numbers (RTO, RPO, counts). */
    const val decimalsInteger = 0

    /**
     * The code a closed-list value is reported with: the taxonomy member of the option ("eba_ZZ:x791"),
     * the value under its domain prefix for countries and currencies ("eba_GA:IE"), or the value itself
     * for columns the taxonomy keeps open (the Link booleans report "true").
     */
    fun encode(column: RoiColumn, key: String): String {
        TaxonomyCodeLists.members[column.code]?.get(key)?.let { return it }
        val domain = TaxonomyCodeLists.domains[column.code] ?: return key
        return if (column.type == RoiType.CodeType) "$domain:${codeType(key)}" else "$domain:$key"
    }

    /** The columns that identify a row of [template] in the taxonomy, or null when it has no key. */
    fun primaryKey(template: String): List<String>? = TaxonomyCodeLists.primaryKeys[template]

    /** The column that holds the entity's definition of an option in the single B_99.01 row, or null. */
    fun definitionColumn(column: String, key: String): String? = TaxonomyCodeLists.definitionColumns["$column|$key"]

    private fun codeType(key: String): String = when {
        key == "LEI" -> "qx2000"
        key == "EUID" -> "qx2002"
        key.endsWith("_CRN") -> "qx2003"
        key.endsWith("_VAT") -> "qx2004"
        key.endsWith("_PNR") -> "qx2005"
        key.endsWith("_NIN") -> "qx2001"
        else -> key
    }

    /** Package file name from the EBA filing rules: entity, module, reference date and creation time. */
    fun packageName(lei: String, country: String, referenceDate: String, createdAt: String): String =
        "$lei.CON_${country}_DORA010100_DORA_${referenceDate}_$createdAt"
}
