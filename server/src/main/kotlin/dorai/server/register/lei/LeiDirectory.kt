package dorai.server.register.lei

/** One LEI record as the public register (GLEIF) holds it. */
data class LeiRecord(
    val lei: String,
    val legalName: String,
    /** ISO country of the headquarters address. */
    val headquartersCountry: String?,
    val status: String,
)

/** The public LEI register. Tests use a fake; the server uses [GleifDirectory]. */
interface LeiDirectory {
    suspend fun search(legalName: String): List<LeiRecord>
    suspend fun record(lei: String): LeiRecord?
    /** LEI of the ultimate parent, or null when the entity reports none (it is its own ultimate parent, or an exception). */
    suspend fun ultimateParent(lei: String): String?
}
