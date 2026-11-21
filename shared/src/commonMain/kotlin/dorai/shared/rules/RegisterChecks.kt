package dorai.shared.rules

import dorai.shared.model.Register
import dorai.shared.model.ServiceType
import dorai.shared.model.ValidationIssue
import dorai.shared.model.ValidationReport
import dorai.shared.model.ValidationSeverity

/**
 * Basic checks on the simplified register: mandatory fields, LEIs, unique ids, service types,
 * dates and references between entries. Not the complete set of CIR reporting rules.
 * Field names are the PascalCase names the old API reported, so clients see the same values.
 */
class RulesRegisterValidator : RegisterValidator {

    override fun validate(register: Register): ValidationReport {
        val issues = Issues()
        val entity = register.entity
        val entityId = entity.lei.ifBlank { null }

        issues.require(entity.name, "B_01.01", "Name", entityId)
        issues.require(entity.country, "B_01.01", "Country", entityId)
        issues.lei(entity.lei, "B_01.01", entityId, required = true)

        for (p in register.providers) {
            issues.require(p.id, "B_05.01", "Id", p.id)
            issues.require(p.name, "B_05.01", "Name", p.id)
            issues.require(p.headquartersCountry.value, "B_05.01", "HeadquartersCountry", p.id)
            issues.lei(p.lei.value, "B_05.01", p.id, required = false)
        }

        for (f in register.functions) {
            issues.require(f.id, "B_06.01", "Id", f.id)
            issues.require(f.name, "B_06.01", "Name", f.id)
        }

        issues.uniqueIds(register.providers.map { it.id }, "B_05.01")
        issues.uniqueIds(register.functions.map { it.id }, "B_06.01")
        issues.uniqueIds(register.contracts.map { it.id }, "B_02.01")
        issues.uniqueIds(register.contracts.flatMap { c -> c.services.map { it.id } }, "B_02.02")

        val providerIds = register.providers.map { it.id }.filter { it.isNotBlank() }.toSet()
        val functionIds = register.functions.map { it.id }.filter { it.isNotBlank() }.toSet()

        for (c in register.contracts) {
            issues.require(c.id, "B_02.01", "Id", c.id)
            issues.require(c.currency, "B_02.01", "Currency", c.id)
            issues.require(c.annualCost.value, "B_02.01", "AnnualCost", c.id)

            // A null end date means an indefinite contract.
            val end = c.endDate
            if (end != null && end < c.startDate) {
                issues.error("invalid-date-range", "B_02.02", "EndDate", "Contract end date cannot be earlier than its start date.", c.id)
            }

            if (issues.require(c.providerId, "B_02.01", "ProviderId", c.id) && c.providerId !in providerIds) {
                issues.error("unknown-provider", "B_02.01", "ProviderId", "Provider '${c.providerId}' does not exist in the register.", c.id)
            }

            for (s in c.services) {
                issues.require(s.id, "B_02.02", "Id", s.id)

                val type = s.serviceType.value
                if (type.isNullOrBlank() || type == ServiceType.Unknown.name) {
                    issues.require(null, "B_02.02", "ServiceType", s.id)
                } else if (type !in VALID_SERVICE_TYPES) {
                    issues.error("invalid-service-type", "B_02.02", "ServiceType", "Service type must be one of S01 through S19.", s.id)
                }

                if (s.dataLocation.value.isNullOrBlank()) {
                    // Whether storage or processing applies is not in the model, so this stays a warning.
                    issues.warning(
                        "missing-data-location", "B_02.02", "DataLocation",
                        "Data location is missing. Review whether storage or processing location must be reported.", s.id,
                    )
                }

                val functionId = s.functionId.value
                if (issues.require(functionId, "B_02.02", "FunctionId", s.id) && functionId !in functionIds) {
                    issues.error("unknown-function", "B_02.02", "FunctionId", "Business function '$functionId' does not exist in the register.", s.id)
                }
            }
        }

        return issues.report()
    }

    private class Issues {
        private val list = mutableListOf<ValidationIssue>()

        fun error(code: String, template: String, field: String, message: String, id: String?) {
            list += ValidationIssue(ValidationSeverity.Error, code, template, field, message, id)
        }

        fun warning(code: String, template: String, field: String, message: String, id: String?) {
            list += ValidationIssue(ValidationSeverity.Warning, code, template, field, message, id)
        }

        /** Adds a mandatory-field error when [value] is blank; returns whether it was present. */
        fun require(value: String?, template: String, field: String, id: String?): Boolean {
            if (!value.isNullOrBlank()) return true
            error("mandatory-field", template, field, "$field is required.", id)
            return false
        }

        fun uniqueIds(ids: List<String>, template: String) {
            ids.filter { it.isNotBlank() }.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.forEach {
                error("duplicate-key", template, "Id", "Id '$it' occurs more than once.", it)
            }
        }

        fun lei(value: String?, template: String, id: String?, required: Boolean) {
            when {
                value.isNullOrBlank() && required -> require(value, template, "Lei", id)
                // The model has no identifier type or legal-person status, so a missing provider LEI is only a warning.
                value.isNullOrBlank() -> warning(
                    "missing-lei", template, "Lei", "Provider LEI is missing. Review provider identification before export.", id,
                )
                !Lei.isValid(value) -> error(
                    "invalid-lei", template, "Lei",
                    "LEI must have 20 uppercase alphanumeric characters, two numeric check digits, and a valid MOD 97-10 checksum.", id,
                )
            }
        }

        fun report(): ValidationReport {
            val errors = list.count { it.severity == ValidationSeverity.Error }
            return ValidationReport(isValid = errors == 0, errorCount = errors, warningCount = list.size - errors, issues = list.toList())
        }
    }

    private companion object {
        val VALID_SERVICE_TYPES = ServiceType.entries.filter { it != ServiceType.Unknown }.map { it.name }.toSet()
    }
}
