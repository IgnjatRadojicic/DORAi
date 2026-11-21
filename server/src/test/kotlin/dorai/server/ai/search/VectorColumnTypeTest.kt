package dorai.server.ai.search

import com.pgvector.PGvector
import org.postgresql.util.PGobject
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

/** The pgvector column type without a database: every shape the driver may hand back. */
class VectorColumnTypeTest {
    private val type = VectorColumnType(3)
    private val values = floatArrayOf(0.1f, 0.2f, 0.3f)

    @Test
    fun declaresAVectorOfItsSize() {
        assertEquals("vector(3)", type.sqlType())
    }

    @Test
    fun readsAFloatArrayAPgVectorOrTheDriversTextObject() {
        val driverObject = PGobject().apply {
            this.type = "vector"
            value = "[0.1,0.2,0.3]"
        }

        assertContentEquals(values, type.valueFromDB(values))
        assertContentEquals(values, type.valueFromDB(PGvector(values)))
        assertContentEquals(values, type.valueFromDB(driverObject))
    }

    @Test
    fun writesAPgVectorAndRefusesAnotherSize() {
        assertIs<PGvector>(type.notNullValueToDB(values))
        assertFailsWith<IllegalArgumentException> { type.notNullValueToDB(floatArrayOf(1f)) }
    }
}
