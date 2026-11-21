package dorai.shared.roi

/**
 * What the EBA DORA taxonomy fixes and the CIR does not: the module the report points to, the package
 * layout and the codes closed lists are reported with. Everything here comes from the taxonomy release,
 * so a new release changes only this file.
 *
 * Not yet verified against the taxonomy package (it has not been downloaded): [verified] stays false
 * until each value below is checked, and the readiness check says so on every export.
 */
object TaxonomyCodes {
    const val verified: Boolean = false

    /** EBA reporting framework release DORA is reported under (DORA moved from 4.0 to 4.3). */
    const val frameworkRelease = "4.3"

    /** Entry point of the DORA module that report.json extends. To verify. */
    const val moduleEntryPoint = "http://www.eba.europa.eu/eu/fr/xbrl/crr/fws/dora/4.3/mod/dora.json"

    const val reportPackageType = "https://xbrl.org/report-package/2023"
    const val xbrlCsvType = "https://xbrl.org/2021/xbrl-csv"

    /** Decimals of monetary values. The CIR asks for amounts in units (not thousands); cents are kept. */
    const val decimalsMonetary = 2

    /**
     * The code a closed-list option is reported with. Until the taxonomy is loaded this is the CIR option
     * key itself ("1", "S17"); service types already match the Annex III identifiers.
     */
    fun encode(column: RoiColumn, key: String): String = key

    /** Package file name from the EBA filing rules: entity, module, reference date and creation time. To verify. */
    fun packageName(lei: String, country: String, referenceDate: String, createdAt: String): String =
        "$lei.CON_${country}_DORA010100_DORA_${referenceDate}_$createdAt"
}
