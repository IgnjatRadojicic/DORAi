package dorai.server.check

import dorai.server.ApiProblem
import dorai.server.fixtures.FixtureData
import dorai.shared.model.DoraiJson
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.utils.io.readBuffer
import kotlinx.io.readByteArray
import kotlinx.serialization.json.JsonObject

/**
 * Runs the rules on a Terraform plan sent as multipart field `plan`; without it, on the built-in demo plan.
 * Phase 1: the plan is only checked to be a JSON object and the demo findings are returned.
 * The real rules from `dorai.shared.rules` replace this in phase 2.
 */
fun Route.checkRoutes(fixtures: FixtureData) {
    post("check") {
        var plan: ByteArray? = null
        call.receiveMultipart().forEachPart { part ->
            if (part is PartData.FileItem && part.name == "plan") plan = part.provider().readBuffer().readByteArray()
            part.release()
        }
        plan?.let { bytes ->
            val valid = runCatching { DoraiJson.parseToJsonElement(bytes.decodeToString()) is JsonObject }.getOrDefault(false)
            if (!valid) throw ApiProblem(HttpStatusCode.BadRequest, "Invalid plan", "The plan must be the JSON from `terraform show -json`.")
        }
        call.respond(fixtures.findingsEastus)
    }
}
