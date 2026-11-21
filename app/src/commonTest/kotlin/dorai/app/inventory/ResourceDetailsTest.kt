package dorai.app.inventory

import dorai.app.shell.toInventoryResource
import dorai.shared.model.ChangeAction
import dorai.shared.model.Redundancy
import dorai.shared.model.Resource
import dorai.shared.model.ResourceSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The expanded row shows the whole record the server sent, and the table marks missing DORA tags. */
class ResourceDetailsTest {
    private fun resource(
        tags: Map<String, String> = emptyMap(),
        source: ResourceSource = ResourceSource.Live,
        changeAction: ChangeAction = ChangeAction.None,
    ) = Resource(
        id = "/subscriptions/s1/resourceGroups/rg/providers/Microsoft.Storage/storageAccounts/stpay",
        name = "stpay", type = "microsoft.storage/storageaccounts", provider = "Azure",
        location = "westeurope", country = "NL", source = source, changeAction = changeAction,
        tags = tags, redundancy = Redundancy.Geo, contractId = "C-2024-001",
    )

    @Test
    fun detailsKeepTagsRedundancySourceAndContract() {
        val tags = mapOf("dora-function" to "F1", "dora-contract" to "C-2024-001", "env" to "prod")
        val mapped = resource(tags).toInventoryResource(emptyList(), emptyMap())
        assertEquals("/subscriptions/s1/resourceGroups/rg/providers/Microsoft.Storage/storageAccounts/stpay", mapped.id)
        assertEquals(tags, mapped.tags)
        assertEquals("Geo", mapped.redundancy)
        assertEquals("Live", mapped.source)
        assertEquals("C-2024-001", mapped.contractId)
        assertNull(mapped.changeAction)
    }

    @Test
    fun aPlannedChangeShowsItsAction() {
        val mapped = resource(source = ResourceSource.Planned, changeAction = ChangeAction.Replace).toInventoryResource(emptyList(), emptyMap())
        assertEquals("Planned", mapped.source)
        assertEquals("Replace", mapped.changeAction)
    }

    @Test
    fun bothDoraTagsPresentMeansNothingMissing() {
        val mapped = resource(mapOf("dora-function" to "F1", "dora-contract" to "C1")).toInventoryResource(emptyList(), emptyMap())
        assertTrue(mapped.missingDoraTags.isEmpty())
    }

    @Test
    fun missingAndBlankDoraTagsAreListed() {
        val noTags = resource().toInventoryResource(emptyList(), emptyMap())
        assertEquals(listOf("dora-function", "dora-contract"), noTags.missingDoraTags)

        val blankContract = resource(mapOf("dora-function" to "F1", "dora-contract" to " ")).toInventoryResource(emptyList(), emptyMap())
        assertEquals(listOf("dora-contract"), blankContract.missingDoraTags)
    }
}
