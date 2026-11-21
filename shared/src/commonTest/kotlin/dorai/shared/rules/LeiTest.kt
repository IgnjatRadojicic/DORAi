package dorai.shared.rules

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Builds a valid LEI from an 18 character body by computing its check digits. */
internal fun leiFrom(body: String): String {
    var r = 0
    for (c in body + "00") r = if (c in '0'..'9') (r * 10 + (c - '0')) % 97 else (r * 100 + (c - 'A') + 10) % 97
    return body + (98 - r).toString().padStart(2, '0')
}

class LeiTest {

    private val bodies = listOf("TEST00FINPAY000001", "TESTABFINPAY000001", "ABCDEFGHIJKLMNOPQR", "123456789012345678", "000000000000000000")

    @Test
    fun generatedLeisWithCorrectCheckDigitsAreValid() = bodies.forEach { assertTrue(Lei.isValid(leiFrom(it)), it) }

    @Test
    fun realWorldLeiIsValid() = assertTrue(Lei.isValid("5299000J2N45DDNE4Y28"))

    @Test
    fun missingValuesAreInvalid() = listOf(null, "", " ").forEach { assertFalse(Lei.isValid(it)) }

    @Test
    fun wrongLengthIsInvalid() {
        val lei = leiFrom(bodies[0])
        assertFalse(Lei.isValid(lei.dropLast(1)))
        assertFalse(Lei.isValid(lei + "0"))
    }

    @Test
    fun unsupportedCharactersAreInvalid() {
        val lei = leiFrom(bodies[0])
        for (c in listOf('a', '-', ' ', '\t', '\n', 'é', '１')) {
            assertFalse(Lei.isValid(lei.substring(0, 5) + c + lei.substring(6)), "char ${c.code}")
        }
    }

    @Test
    fun aLetterInTheCheckDigitsIsInvalid() {
        val lei = leiFrom(bodies[0])
        assertFalse(Lei.isValid(lei.substring(0, 18) + "A" + lei[19]))
        assertFalse(Lei.isValid(lei.substring(0, 19) + "A"))
    }

    @Test
    fun wrongChecksumIsInvalid() {
        val lei = leiFrom(bodies[0])
        val other = if (lei[19] == '9') '0' else lei[19] + 1
        assertFalse(Lei.isValid(lei.substring(0, 19) + other))
    }

    @Test
    fun noncanonicalCheckDigitsAreInvalid() {
        // 00 and 01 satisfy MOD 97 exactly when 97 and 98 do, 99 when 02 does; only 02..98 are canonical.
        for ((invalid, valid) in listOf("00" to "97", "01" to "98", "99" to "02")) {
            val body = (0..9999).map { "TESTFINPAY" + it.toString().padStart(8, '0') }.first { leiFrom(it).endsWith(valid) }
            assertTrue(Lei.isValid(body + valid))
            assertFalse(Lei.isValid(body + invalid), "$body$invalid")
        }
    }
}
