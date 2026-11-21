package dorai.shared.rules

import dorai.shared.model.ChangeAction
import dorai.shared.model.Redundancy
import dorai.shared.model.ServiceType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TerraformPlanReaderTest {

    private fun change(
        address: String,
        type: String,
        actions: String,
        before: String = "null",
        after: String = "null",
        provider: String = "registry.terraform.io/hashicorp/azurerm",
        mode: String = "managed",
    ) = """{"address":"$address","mode":"$mode","type":"$type","name":"${address.substringAfterLast('.')}",
        "provider_name":"$provider","change":{"actions":$actions,"before":$before,"after":$after}}"""

    private fun plan(vararg changes: String, extra: String = "") =
        """{"format_version":"1.2","resource_changes":[${changes.joinToString(",")}]$extra}"""

    @Test
    fun actionsMapToChangeActions() {
        assertEquals(ChangeAction.None, TerraformPlanReader.toAction(listOf("no-op")))
        assertEquals(ChangeAction.Create, TerraformPlanReader.toAction(listOf("create")))
        assertEquals(ChangeAction.Update, TerraformPlanReader.toAction(listOf("update")))
        assertEquals(ChangeAction.Delete, TerraformPlanReader.toAction(listOf("delete")))
        assertEquals(ChangeAction.Replace, TerraformPlanReader.toAction(listOf("delete", "create")))
        assertEquals(ChangeAction.Replace, TerraformPlanReader.toAction(listOf("create", "delete")))
        assertNull(TerraformPlanReader.toAction(listOf("read")))
    }

    @Test
    fun azureCreateReadsLocationTagsTypeAndRedundancy() {
        val after = """{"name":"stexports","location":"West Europe","account_replication_type":"LRS",
            "tags":{"dora-function":"F1","dora-contract":"C1","owner":"ops"}}"""
        val r = TerraformPlanReader.read(plan(change("azurerm_storage_account.exports", "azurerm_storage_account", """["create"]""", after = after))).single()

        assertEquals("azurerm_storage_account.exports", r.id)
        assertEquals("stexports", r.name)
        assertEquals("Azure", r.provider)
        assertEquals("West Europe", r.location)
        assertEquals("NL", r.country)
        assertEquals(ServiceType.S17, r.serviceType)
        assertEquals(Redundancy.Local, r.redundancy)
        assertEquals("F1", r.functionId)
        assertEquals("C1", r.contractId)
        assertEquals(ChangeAction.Create, r.changeAction)
    }

    @Test
    fun deleteReadsFromBefore() {
        val before = """{"name":"psql-old","location":"eastus","tags":{"dora-function":"F1"}}"""
        val r = TerraformPlanReader.read(
            plan(change("azurerm_postgresql_flexible_server.old", "azurerm_postgresql_flexible_server", """["delete"]""", before = before)),
        ).single()
        assertEquals(ChangeAction.Delete, r.changeAction)
        assertEquals("psql-old", r.name)
        assertEquals("eastus", r.location)
        assertEquals("F1", r.functionId)
    }

    @Test
    fun updateKeepsThePreviousRedundancy() {
        val r = TerraformPlanReader.read(
            plan(
                change(
                    "azurerm_storage_account.dr", "azurerm_storage_account", """["update"]""",
                    before = """{"name":"stdr","location":"swedencentral","account_replication_type":"GRS"}""",
                    after = """{"name":"stdr","location":"swedencentral","account_replication_type":"LRS"}""",
                ),
            ),
        ).single()
        assertEquals(Redundancy.Geo, r.previousRedundancy)
        assertEquals(Redundancy.Local, r.redundancy)
    }

    @Test
    fun dataSourcesReadActionsAndHelperTypesAreSkipped() {
        val resources = TerraformPlanReader.read(
            plan(
                change("data.azurerm_client_config.me", "azurerm_client_config", """["read"]""", mode = "data"),
                change("random_password.db", "random_password", """["create"]""", after = "{}"),
                change("aws_iam_role.app", "aws_iam_role", """["create"]""", after = "{}", provider = "registry.terraform.io/hashicorp/aws"),
                change("azurerm_key_vault.kv", "azurerm_key_vault", """["read"]""", after = "{}"),
            ),
        )
        assertTrue(resources.isEmpty())
    }

    @Test
    fun awsRegionComesFromTheProviderBlock() {
        val config = ""","configuration":{
            "provider_config":{"aws.us":{"name":"aws","alias":"us","expressions":{"region":{"constant_value":"us-east-1"}}}},
            "root_module":{"resources":[{"address":"aws_db_instance.replica","provider_config_key":"aws.us"}]}}"""
        val r = TerraformPlanReader.read(
            plan(
                change(
                    "aws_db_instance.replica", "aws_db_instance", """["create"]""",
                    after = """{"identifier":"payments-replica"}""", provider = "registry.terraform.io/hashicorp/aws",
                ),
                extra = config,
            ),
        ).single()
        assertEquals("us-east-1", r.location)
        assertEquals("US", r.country)
        assertEquals("AWS", r.provider)
        assertEquals("payments-replica", r.name)
        assertEquals(ServiceType.S18, r.serviceType)
    }

    @Test
    fun awsRegionFromAVariableOnTheDefaultBlock() {
        val config = ""","variables":{"region":{"value":"eu-central-1"}},
            "configuration":{"provider_config":{"aws":{"name":"aws","expressions":{"region":{"references":["var.region"]}}}}}"""
        val r = TerraformPlanReader.read(
            plan(
                change("aws_s3_bucket.statements[0]", "aws_s3_bucket", """["create"]""", after = """{"bucket":"finpay-statements"}""",
                    provider = "registry.terraform.io/hashicorp/aws"),
                extra = config,
            ),
        ).single()
        assertEquals("eu-central-1", r.location)
        assertEquals("finpay-statements", r.name)
    }

    @Test
    fun unknownRegionStaysEmpty() {
        val r = TerraformPlanReader.read(
            plan(change("aws_sqs_queue.jobs", "aws_sqs_queue", """["create"]""", after = """{"name":"jobs"}""", provider = "registry.terraform.io/hashicorp/aws")),
        ).single()
        assertEquals("", r.location)
        assertNull(r.country)
    }

    @Test
    fun notAPlanIsRejected() {
        assertFailsWith<InvalidPlanException> { TerraformPlanReader.read("not json") }
        assertFailsWith<InvalidPlanException> { TerraformPlanReader.read("[1,2]") }
        assertFailsWith<InvalidPlanException> { TerraformPlanReader.read("""{"format_version":"1.2"}""") }
    }
}
