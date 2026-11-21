package dorai.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.path
import dorai.shared.model.DoraiJson
import dorai.shared.model.Finding
import dorai.shared.model.Register
import dorai.shared.rules.InvalidPlanException
import dorai.shared.rules.RulesPlanChecker
import kotlinx.serialization.SerializationException
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.writeText

/** `dorai check plan.json --register register.json`: the server's rules, run offline. */
class CheckCommand : CliktCommand(name = "check") {
    override fun help(context: Context) =
        "Check a Terraform plan against the DORA register. Exits 1 on High findings, 2 on bad input."

    private val plan by argument(help = "Terraform plan JSON: terraform show -json plan.bin > plan.json").path()
    private val register by option("--register", "-r", help = "Register JSON, as GET /api/register returns it.").path().required()
    private val format by option("--format", "-f", help = "text, markdown (the PR comment) or json.")
        .enum<CheckFormat> { it.name.lowercase() }.default(CheckFormat.Text)
    private val output by option("--output", "-o", help = "Write the report to this file instead of standard output.").path()

    override fun run() {
        val findings = try {
            check(plan, register)
        } catch (e: InvalidInput) {
            echo(e.message, err = true)
            throw ProgramResult(ExitCodes.INVALID_INPUT)
        }
        val report = CheckReport.format(findings, format)
        output?.writeText(report) ?: echo(report, trailingNewline = false)
        throw ProgramResult(CheckReport.exitCode(findings))
    }

    private class InvalidInput(override val message: String) : Exception(message)

    private fun check(planPath: Path, registerPath: Path): List<Finding> {
        val register = try {
            DoraiJson.decodeFromString<Register>(read(registerPath, "Register"))
        } catch (e: SerializationException) {
            throw InvalidInput("Register ${registerPath.name} is not valid: ${e.message?.lineSequence()?.first()}")
        } catch (e: IllegalArgumentException) {
            throw InvalidInput("Register ${registerPath.name} is not valid: ${e.message}")
        }
        return try {
            RulesPlanChecker().check(read(planPath, "Plan"), register)
        } catch (e: InvalidPlanException) {
            throw InvalidInput("Plan ${planPath.name}: ${e.message}")
        }
    }

    private fun read(path: Path, what: String): String {
        if (!path.isRegularFile()) throw InvalidInput("$what file not found: $path")
        return try {
            path.readText()
        } catch (e: IOException) {
            throw InvalidInput("$what file ${path.name} cannot be read: ${e.message}")
        }
    }
}
