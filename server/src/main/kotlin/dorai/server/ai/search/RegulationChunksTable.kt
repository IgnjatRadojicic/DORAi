package dorai.server.ai.search

import com.pgvector.PGvector
import dorai.server.ai.embed.TextEmbedder
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ColumnType
import org.jetbrains.exposed.v1.core.DoubleColumnType
import org.jetbrains.exposed.v1.core.ExpressionWithColumnType
import org.jetbrains.exposed.v1.core.QueryBuilder
import org.jetbrains.exposed.v1.core.Table

/** Table `regulation_chunks` (migration V2). The embedding never leaves the database in a query result. */
object RegulationChunksTable : Table("regulation_chunks") {
    val id = long("id").autoIncrement()
    val citationId = text("citation_id")
    val regulation = text("regulation")
    val article = text("article")
    val paragraph = text("paragraph").nullable()
    val text = text("text")
    val page = integer("page")
    val contentHash = varchar("content_hash", 64)
    val embeddingModel = text("embedding_model")
    val embedding = registerColumn("embedding", VectorColumnType(TextEmbedder.DIMENSIONS))

    override val primaryKey = PrimaryKey(id)
}

/** A pgvector `vector(n)` column holding a [FloatArray]. */
class VectorColumnType(private val dimensions: Int) : ColumnType<FloatArray>() {
    override fun sqlType() = "vector($dimensions)"

    override fun valueFromDB(value: Any): FloatArray = when (value) {
        is FloatArray -> value
        is PGvector -> value.toArray()
        // The driver hands back a PGobject whose text is the vector literal, for example `[0.1,0.2]`.
        else -> PGvector(value.toString()).toArray()
    }

    override fun notNullValueToDB(value: FloatArray): Any {
        require(value.size == dimensions) { "Vector has ${value.size} dimensions, $dimensions required" }
        return PGvector(value)
    }
}

/** pgvector cosine distance `column <=> vector`: 0 for the same direction, up to 2 for opposite. */
class CosineDistance(
    private val column: Column<FloatArray>,
    private val vector: FloatArray,
) : ExpressionWithColumnType<Double>() {
    override val columnType = DoubleColumnType()

    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        append(column)
        append(" <=> ")
        registerArgument(column, vector)
    }
}
