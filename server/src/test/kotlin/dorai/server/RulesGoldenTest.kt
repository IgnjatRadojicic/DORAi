package dorai.server

import dorai.server.fixtures.FixtureData
import dorai.shared.model.DoraiJson
import dorai.shared.model.Finding
import dorai.shared.rules.RulesPlanChecker
import dorai.shared.rules.RulesRegisterValidator
import kotlin.test.Test
import kotlin.test.assertEquals

/** The FinPay demo data through the real rules: known findings for the eastus plan, a clean register. */
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

    @Test
    fun finpayRegisterHasNoIssues() {
        val report = RulesRegisterValidator().validate(FixtureData.load().register)
        assertEquals(emptyList(), report.issues)
        assertEquals(true, report.isValid)
    }
}
