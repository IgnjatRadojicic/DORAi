package dorai.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class WireFormatTest {

    @Test
    fun enumsUseTheirWireNames() {
        assertEquals("\"High\"", DoraiJson.encodeToString(Severity.High))
        assertEquals("\"ai-suggested\"", DoraiJson.encodeToString(ProvenanceLabel.AiSuggested))
        assertEquals("\"human-confirmed\"", DoraiJson.encodeToString(ProvenanceLabel.HumanConfirmed))
        assertEquals("\"S18\"", DoraiJson.encodeToString(ServiceType.S18))
    }

    @Test
    fun instantsWithOffsetParse() {
        val json = """{"label":"scanned","updatedAt":"2026-11-11T00:00:00+00:00"}"""
        val provenance = DoraiJson.decodeFromString<Provenance>(json)
        assertEquals(Instant.parse("2026-11-11T00:00:00Z"), provenance.updatedAt)
    }

    @Test
    fun fieldNamesAreCamelCaseAndUnknownFieldsAreIgnored() {
        val json = """{"ruleId":"untagged","severity":"Medium","title":"t","resourceAddress":"a","detail":"d","extra":1}"""
        val finding = DoraiJson.decodeFromString<Finding>(json)
        assertEquals("untagged", finding.ruleId)
        assertTrue(DoraiJson.encodeToString(finding).contains("\"resourceAddress\":\"a\""))
    }
}
