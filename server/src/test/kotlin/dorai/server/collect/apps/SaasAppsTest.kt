package dorai.server.collect.apps

import dorai.server.collect.LiveConfig
import dorai.shared.model.Resource
import dorai.shared.model.ServiceType
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SaasAppsTest {

    private fun obj(text: String) = SaasApps.parse(text) as JsonObject

    @Test
    fun `third-party enterprise apps become SaaS resources with their publisher`() {
        val sp = obj("""{"id":"sp1","displayName":"Salesforce","publisherName":"salesforce.com","appOwnerOrganizationId":"aaaa",
            "verifiedPublisher":{"displayName":"Salesforce, Inc."},"accountEnabled":true}""")
        val r = SaasApps.entra(sp, ownTenant = "bbbb")!!
        assertEquals("entra:sp1" to ServiceType.S19, r.id to r.serviceType)
        assertEquals("Salesforce, Inc.", r.tags[SaasApps.PUBLISHER_TAG])
    }

    @Test
    fun `microsoft, own-tenant and disabled apps are left out`() {
        assertNull(SaasApps.entra(obj("""{"id":"1","displayName":"Graph","appOwnerOrganizationId":"f8cdef31-a31e-4b4a-93e4-5f571e91255a"}"""), null))
        assertNull(SaasApps.entra(obj("""{"id":"2","displayName":"Our app","appOwnerOrganizationId":"BBBB"}"""), "bbbb"))
        assertNull(SaasApps.entra(obj("""{"id":"3","displayName":"Old","appOwnerOrganizationId":"cccc","accountEnabled":false}"""), null))
    }

    @Test
    fun `okta apps use their label as publisher and follow the link header`() {
        val r = SaasApps.okta(obj("""{"id":"0oa1","label":"Slack","name":"slack","status":"ACTIVE"}"""))!!
        assertEquals("okta:0oa1" to "Slack", r.id to r.tags[SaasApps.PUBLISHER_TAG])
        val next = SaasApps.nextLink(listOf("<https://x.okta.com/api/v1/apps?limit=200>; rel=\"self\", <https://x.okta.com/api/v1/apps?after=0oa9>; rel=\"next\""))
        assertEquals("https://x.okta.com/api/v1/apps?after=0oa9", next)
        assertNull(SaasApps.nextLink(listOf("<https://x.okta.com/api/v1/apps>; rel=\"self\"")))
    }

    @Test
    fun `a failing identity provider adds no apps and does not fail the scan`() = runTest {
        val ok = object : SaasAppsCollector.AppSource {
            override val name = "ok"
            override suspend fun apps() = listOf(SaasApps.okta(obj("""{"id":"a","label":"Slack"}"""))!!)
        }
        val broken = object : SaasAppsCollector.AppSource {
            override val name = "broken"
            override suspend fun apps(): List<Resource> = error("401")
        }
        assertEquals(listOf("okta:a"), SaasAppsCollector(listOf(ok, broken)).collect().resources.map { it.id })
    }

    @Test
    fun `apps config is read from the environment`() {
        val base = mapOf("DORAI_DATA_SOURCE" to "live", "DORAI_CLOUDS" to "azure")
        val config = LiveConfig.fromEnv(base + ("DORAI_APPS" to "entra,okta") + ("DORAI_OKTA_URL" to "https://firm.okta.com"))!!
        assertEquals(setOf(LiveConfig.App.Entra, LiveConfig.App.Okta), config.apps)
        assertTrue(LiveConfig.fromEnv(base)!!.apps.isEmpty())
        assertFailsWith<IllegalArgumentException> { LiveConfig.fromEnv(base + ("DORAI_APPS" to "okta")) }
    }
}
