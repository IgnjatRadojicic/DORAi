package dorai.server.check

import dorai.server.ApiProblem
import dorai.server.core.AiAnswer
import dorai.server.core.CheckedPlan
import dorai.server.register.who
import dorai.server.core.Services
import dorai.shared.model.Finding
import dorai.shared.rules.InvalidPlanException
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.utils.io.readBuffer
import kotlinx.io.readByteArray
import kotlin.time.Clock

/** At most this many findings get an AI explanation per check. */
private const val MAX_EXPLAINED = 20

/**
 * Runs the rules on a Terraform plan sent as multipart field `plan`; without it, on the demo plan.
 * A valid plan that was sent is kept, so the PDF report checks the firm's latest plan.
 * Findings come from the rules only; the explainer may add text and a citation afterwards, best effort.
 */
fun Route.checkRoutes(services: Services) {
    post("check") {
        var plan: ByteArray? = null
        call.receiveMultipart().forEachPart { part ->
            if (part is PartData.FileItem && part.name == "plan") plan = part.provider().readBuffer().readByteArray()
            part.release()
        }
        val planJson = plan?.decodeToString() ?: services.demoPlan
        val findings = try {
            services.planChecker.check(planJson, services.register.register())
        } catch (e: InvalidPlanException) {
            throw ApiProblem(HttpStatusCode.BadRequest, "Invalid plan", e.message)
        }
        // Only a plan the firm sent is kept for the report; the demo plan never is.
        if (plan != null) services.plans.save(CheckedPlan(planJson, call.who(), Clock.System.now()))
        call.respond(explain(services, findings))
    }
}

private suspend fun explain(services: Services, findings: List<Finding>): List<Finding> =
    findings.mapIndexed { index, finding ->
        if (index >= MAX_EXPLAINED) return@mapIndexed finding
        when (val answer = services.explainer.explain(finding)) {
            is AiAnswer.Answer -> finding.copy(explanation = answer.value, citation = answer.citations.first())
            else -> finding
        }
    }
