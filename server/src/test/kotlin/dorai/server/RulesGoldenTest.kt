package dorai.server

import dorai.server.fixtures.FixtureData
import dorai.shared.model.DoraiJson
import dorai.shared.model.Finding
import dorai.shared.rules.RulesPlanChecker
import kotlin.test.Test
import kotlin.test.assertEquals

/** The eastus demo plan against the FinPay register must give exactly the expected findings, in order. */
class RulesGoldenTest {

    @Test
    fun eastusPlanGivesTheExpectedFindings() {
        val data = FixtureData.load()
        val expected = DoraiJson.decodeFromString<List<Finding>>(
            javaClass.getResource("/fixtures/findings-eastus.json")!!.readText(),
        )
        val actual = RulesPlanChecker().check(data.planEastus.decodeToString(), data.register)
        assertEquals(expected, actual)
    }
}
