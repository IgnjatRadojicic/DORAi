package dorai.shared.rules

import dorai.shared.model.Finding
import dorai.shared.model.Register
import dorai.shared.model.ValidationReport

/**
 * Reads a Terraform plan (`terraform show -json`) and runs the rules against the register.
 * Lives in `shared` so the server and the CLI produce identical findings.
 * Only rules create findings; the AI may explain them afterwards.
 */
fun interface PlanChecker {
    /** @throws InvalidPlanException when [planJson] is not a Terraform plan. */
    fun check(planJson: String, register: Register): List<Finding>
}

class InvalidPlanException(message: String) : IllegalArgumentException(message)

/** Checks the register itself: LEI format, mandatory fields, links between entries. */
fun interface RegisterValidator {
    fun validate(register: Register): ValidationReport
}
