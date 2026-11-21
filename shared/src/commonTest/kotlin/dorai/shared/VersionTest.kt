package dorai.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VersionTest {
    @Test
    fun parsesPlainVersions() {
        assertEquals(Version(1, 7, 0), Version.parse("1.7.0"))
        assertEquals(Version(12, 0, 3), Version.parse(" 12.0.3 "))
    }

    @Test
    fun rejectsAnythingElse() {
        listOf("", "1.7", "1.7.0.1", "v1.7.0", "1.7.0-beta", "a.b.c", "99999999999.0.0").forEach {
            assertNull(Version.parse(it), it)
        }
    }

    @Test
    fun sameMajorIsCompatible() {
        assertTrue(Version(1, 6, 0).isCompatibleWith(Version(1, 9, 4)))
        assertFalse(Version(1, 9, 0).isCompatibleWith(Version(2, 0, 0)))
    }

    @Test
    fun ordersByMajorThenMinorThenPatch() {
        assertTrue(Version(1, 10, 0) > Version(1, 9, 9))
        assertTrue(Version(2, 0, 0) > Version(1, 99, 99))
        assertTrue(Version(1, 7, 1) > Version(1, 7, 0))
    }

    @Test
    fun currentIsTheBuildVersion() {
        assertEquals(DoraiBuild.VERSION, Version.CURRENT.toString())
    }
}
