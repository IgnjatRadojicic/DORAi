package dorai.server.register.lei

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * GLEIF's public API (no key, free). Only provider names and LEIs leave the server, and only when a
 * reviewer asks for a lookup. [baseUrl] comes from DORAI_GLEIF_URL so a firm can point it at a mirror.
 */
class GleifDirectory(private val baseUrl: String = DEFAULT_URL) : LeiDirectory {
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()

    override suspend fun search(legalName: String) =
        get("/lei-records?page[size]=10&filter[entity.legalName]=" + URLEncoder.encode(legalName, Charsets.UTF_8))
            ?.let(GleifJson::records).orEmpty()

    override suspend fun record(lei: String) = get("/lei-records/$lei")?.let(GleifJson::records)?.firstOrNull()

    override suspend fun ultimateParent(lei: String) = get("/lei-records/$lei/ultimate-parent")?.let(GleifJson::records)?.firstOrNull()?.lei

    /** The body, or null on 404 (GLEIF answers 404 for "no parent"). Other failures throw. */
    private suspend fun get(path: String): String? = withContext(Dispatchers.IO) {
        val request = HttpRequest.newBuilder(URI.create(baseUrl.trimEnd('/') + path))
            .header("Accept", "application/vnd.api+json").timeout(Duration.ofSeconds(20)).GET().build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        when (response.statusCode()) {
            200 -> response.body()
            404 -> null
            else -> error("GLEIF answered ${response.statusCode()}")
        }
    }

    companion object {
        const val DEFAULT_URL = "https://api.gleif.org/api/v1"
    }
}

/** GLEIF JSON:API bodies to records. Pure, so it is tested without the network. */
object GleifJson {
    private val json = Json { ignoreUnknownKeys = true }

    fun records(body: String): List<LeiRecord> {
        val data = json.parseToJsonElement(body).obj()?.get("data") ?: return emptyList()
        val items = (data as? JsonArray)?.toList() ?: listOf(data)
        return items.mapNotNull { item ->
            val attributes = item.obj()?.get("attributes").obj() ?: return@mapNotNull null
            val entity = attributes["entity"].obj()
            LeiRecord(
                lei = attributes.text("lei") ?: return@mapNotNull null,
                legalName = entity?.get("legalName").obj()?.text("name") ?: return@mapNotNull null,
                headquartersCountry = entity?.get("headquartersAddress").obj()?.text("country"),
                status = entity?.text("status") ?: "",
            )
        }
    }

    private fun JsonElement?.obj() = this as? JsonObject
    private fun JsonObject.text(key: String) = (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { it.isString }?.content
}
