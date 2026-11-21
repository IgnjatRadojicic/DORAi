package dorai.server.collect.apps

import com.azure.core.credential.TokenRequestContext
import com.azure.identity.DefaultAzureCredentialBuilder
import dorai.server.core.CloudCollector
import dorai.server.core.CollectResult
import dorai.shared.model.ChangeAction
import dorai.shared.model.Redundancy
import dorai.shared.model.Resource
import dorai.shared.model.ResourceSource
import dorai.shared.model.ScanMode
import dorai.shared.model.ServiceType
import dorai.shared.roi.VendorChecks
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * SaaS the firm signs in to through its identity provider (Entra ID enterprise apps, Okta apps). Each app
 * is an ICT service (S19) from a third party, which DORA Art. 28(3) wants in the register. Best effort: an
 * identity provider that refuses the call adds no apps, with a warning, and never fails the cloud scan.
 */
class SaasAppsCollector(private val sources: List<AppSource>) : CloudCollector {
    override val provider = SaasApps.PROVIDER

    interface AppSource {
        val name: String
        suspend fun apps(): List<Resource>
    }

    override suspend fun collect(): CollectResult {
        val apps = sources.flatMap { source ->
            try {
                source.apps()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.warn("{} apps unavailable, scanning without them: {}", source.name, e.message)
                emptyList()
            }
        }
        return CollectResult(apps.distinctBy { it.id }, ScanMode.Live)
    }

    private companion object {
        val log = LoggerFactory.getLogger(SaasAppsCollector::class.java)
    }
}

/** Microsoft Graph service principals of integrated (gallery and custom) apps; needs Application.Read.All. */
class EntraApps(private val ownTenant: String?) : SaasAppsCollector.AppSource {
    override val name = "Entra ID"
    private val credential by lazy { DefaultAzureCredentialBuilder().build() }

    override suspend fun apps(): List<Resource> {
        val token = withContext(Dispatchers.IO) {
            credential.getTokenSync(TokenRequestContext().addScopes("https://graph.microsoft.com/.default")).token
        }
        var next: String? = "https://graph.microsoft.com/v1.0/servicePrincipals?\$top=999" +
            "&\$filter=tags/any(t:t%20eq%20'WindowsAzureActiveDirectoryIntegratedApp')" +
            "&\$select=id,appId,displayName,publisherName,appOwnerOrganizationId,verifiedPublisher,accountEnabled"
        val rows = mutableListOf<JsonObject>()
        while (next != null) {
            val page = SaasApps.json(Http.get(next, "Bearer $token"))
            rows += (page["value"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
            next = SaasApps.text(page, "@odata.nextLink")
        }
        return rows.mapNotNull { SaasApps.entra(it, ownTenant) }
    }
}

/** Okta's active apps; needs an API token with okta.apps.read (DORAI_OKTA_TOKEN). */
class OktaApps(private val baseUrl: String, private val token: String) : SaasAppsCollector.AppSource {
    override val name = "Okta"

    override suspend fun apps(): List<Resource> {
        var next: String? = baseUrl.trimEnd('/') + "/api/v1/apps?limit=200&filter=status%20eq%20%22ACTIVE%22"
        val rows = mutableListOf<JsonObject>()
        while (next != null) {
            val response = Http.send(next, "SSWS $token")
            rows += (SaasApps.parse(response.body()) as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
            next = SaasApps.nextLink(response.headers().allValues("link"))
        }
        return rows.mapNotNull(SaasApps::okta)
    }
}

private object Http {
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()

    suspend fun send(url: String, authorization: String): HttpResponse<String> = withContext(Dispatchers.IO) {
        val request = HttpRequest.newBuilder(URI.create(url)).header("Authorization", authorization)
            .header("Accept", "application/json").timeout(Duration.ofSeconds(30)).GET().build()
        client.send(request, HttpResponse.BodyHandlers.ofString()).also {
            check(it.statusCode() == 200) { "answered ${it.statusCode()}" }
        }
    }

    suspend fun get(url: String, authorization: String) = send(url, authorization).body()
}

/** Identity-provider app records to resources. Pure, so it is tested without the network. */
object SaasApps {
    const val PROVIDER = "SaaS"
    const val PUBLISHER_TAG = VendorChecks.PUBLISHER_TAG
    const val TYPE = "saas/app"

    /** Microsoft's own tenants: their first-party apps are the cloud itself, not a separate vendor. */
    private val MICROSOFT_TENANTS = setOf("f8cdef31-a31e-4b4a-93e4-5f571e91255a", "72f988bf-86f1-41af-91ab-2d7cd011db47")

    private val parser = Json { ignoreUnknownKeys = true }

    fun parse(body: String): JsonElement = parser.parseToJsonElement(body)
    fun json(body: String): JsonObject = parse(body) as? JsonObject ?: JsonObject(emptyMap())
    fun text(o: JsonObject?, key: String) = (o?.get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

    /** An enterprise app, unless Microsoft or the firm's own tenant publishes it, or it is switched off. */
    fun entra(sp: JsonObject, ownTenant: String?): Resource? {
        val owner = text(sp, "appOwnerOrganizationId")?.lowercase()
        if (owner != null && (owner in MICROSOFT_TENANTS || owner == ownTenant?.lowercase())) return null
        if ((sp["accountEnabled"] as? JsonPrimitive)?.content == "false") return null
        val name = text(sp, "displayName") ?: return null
        val publisher = text(sp["verifiedPublisher"] as? JsonObject, "displayName") ?: text(sp, "publisherName")
        return app("entra:${text(sp, "id") ?: return null}", name, publisher, "entra")
    }

    /** An Okta app; Okta names no publisher, so the app's label stands in for it. */
    fun okta(app: JsonObject): Resource? {
        val label = text(app, "label") ?: return null
        return app("okta:${text(app, "id") ?: return null}", label, label, "okta")
    }

    /** The next page from an RFC 8288 Link header (Okta paginates this way). */
    fun nextLink(headers: List<String>): String? = headers.flatMap { it.split(',') }
        .firstOrNull { "rel=\"next\"" in it }?.substringAfter('<')?.substringBefore('>')

    private fun app(id: String, name: String, publisher: String?, source: String) = Resource(
        id = id,
        name = name,
        type = TYPE,
        provider = PROVIDER,
        location = "",
        source = ResourceSource.Live,
        changeAction = ChangeAction.None,
        tags = buildMap {
            put("dorai-source", source)
            publisher?.let { put(PUBLISHER_TAG, it) }
        },
        serviceType = ServiceType.S19,
        redundancy = Redundancy.Unknown,
    )
}
