package dorai.shared

import kotlin.test.Test
import kotlin.test.assertEquals

class DoraiTest {
    @Test
    fun apiPrefixIsStable() = assertEquals("/api", Dorai.API_PREFIX)
}
