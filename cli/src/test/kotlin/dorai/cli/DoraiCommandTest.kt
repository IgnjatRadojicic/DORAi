package dorai.cli

import com.github.ajalt.clikt.testing.test
import dorai.shared.DoraiBuild
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DoraiCommandTest {
    @Test
    fun versionPrintsTheProductVersion() {
        val result = DoraiCommand().test("--version")
        assertEquals(0, result.statusCode)
        assertTrue(result.output.contains(DoraiBuild.VERSION))
    }
}
