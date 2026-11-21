package dorai.server

import dorai.server.fixtures.FixtureData
import dorai.shared.model.DoraiJson
import dorai.shared.model.Register
import dorai.shared.rules.RulesPlanChecker
import kotlinx.serialization.json.Json
import kotlin.io.path.Path
import kotlin.io.path.div
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `examples/finpay` is what the GitHub Action checks in this repo: the FinPay register in the API format
 * and a plan with no changes. Run with DORAI_UPDATE_EXAMPLES=1 to rewrite the register from the fixture.
 */
class ExamplesTest {
    private val dir = Path("..") / "examples" / "finpay"
    private val data = FixtureData.load()

    @Test
    fun registerExampleIsTheFixtureRegister() {
        val file = dir / "register.json"
        if (System.getenv("DORAI_UPDATE_EXAMPLES") == "1") {
            file.writeText(Json(DoraiJson) { prettyPrint = true }.encodeToString(data.register) + "\n")
        }
        assertEquals(
            data.register,
            DoraiJson.decodeFromString<Register>(file.readText()),
            "examples/finpay/register.json is out of date: run the server tests with DORAI_UPDATE_EXAMPLES=1.",
        )
    }

    @Test
    fun cleanPlanHasNoFindingsAndTheDemoPlanHasHighOnes() {
        assertEquals(emptyList(), RulesPlanChecker().check((dir / "plan.json").readText(), data.register))
        val demo = RulesPlanChecker().check(data.planEastus.decodeToString(), data.register)
        assertTrue(demo.any { it.severity == dorai.shared.model.Severity.High }, demo.toString())
    }
}
