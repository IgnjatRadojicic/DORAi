package dorai.app.register

import dorai.app.components.runImmediate
import dorai.shared.model.BusinessFunction
import dorai.shared.model.Contract
import dorai.shared.model.FinancialEntity
import dorai.shared.model.Provenance
import dorai.shared.model.ProvenanceLabel
import dorai.shared.model.Provider
import dorai.shared.model.Register
import dorai.shared.model.RegisterField
import dorai.shared.model.RoiRow
import dorai.shared.roi.RoiOption
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class RegisterViewModelTest {
    private val now = Instant.fromEpochSeconds(1_800_000_000)
    private val scanned = RegisterField("IE", Provenance(ProvenanceLabel.Scanned, Instant.fromEpochSeconds(0)))

    private class FakeSource(var register: Register) : RegisterSource {
        val saved = mutableListOf<Any>()
        val deleted = mutableListOf<String>()
        override suspend fun load() = register
        override suspend fun saveEntity(entity: FinancialEntity) { saved += entity; register = register.copy(entity = entity) }
        override suspend fun saveProvider(provider: Provider) { saved += provider; register = register.copy(providers = register.providers.filterNot { it.id == provider.id } + provider) }
        override suspend fun deleteProvider(id: String) { deleted += id; register = register.copy(providers = register.providers.filterNot { it.id == id }) }
        override suspend fun saveContract(contract: Contract) { saved += contract }
        override suspend fun deleteContract(id: String) { deleted += id }
        override suspend fun saveFunction(function: BusinessFunction) { saved += function }
        override suspend fun deleteFunction(id: String) { deleted += id }
        override suspend fun saveRow(template: String, row: RoiRow) { saved += template to row }
        override suspend fun deleteRow(template: String, id: String) { deleted += "$template/$id" }
    }

    private fun register() = Register(
        entity = FinancialEntity("529900T8BM49AURSDO55", "FinPay", "IE"),
        providers = listOf(Provider("P1", "Cloudy", RegisterField(null, scanned.provenance), scanned)),
        contracts = emptyList(),
        functions = listOf(BusinessFunction("F1", "Payments", isCritical = true)),
        asOf = now,
    )

    private fun model(source: FakeSource, canEdit: Boolean = true) =
        RegisterViewModel(source, canEdit = { canEdit }, clock = object : Clock { override fun now() = now }).also { runImmediate { it.refresh() } }

    @Test
    fun aNewProviderIsSavedAsThePersonsValue() {
        val source = FakeSource(register())
        val model = model(source)
        model.startNew()
        model.type("id", "P2")
        model.type("name", "Hosting Ltd")
        model.type("lei", "5493001KJTIIGC8Y1R12")
        runImmediate { model.save() }
        val saved = source.saved.single() as Provider
        assertEquals("P2", saved.id)
        assertEquals(ProvenanceLabel.HumanConfirmed, saved.lei.provenance.label)
        assertNull(model.state.form)
        assertEquals("Saved.", model.state.message)
    }

    @Test
    fun anUnchangedValueKeepsWhereItCameFrom() {
        val source = FakeSource(register())
        val model = model(source)
        model.startEdit("P1")
        model.type("name", "Cloudy Europe")
        runImmediate { model.save() }
        val saved = source.saved.single() as Provider
        assertEquals(scanned, saved.headquartersCountry)
    }

    @Test
    fun problemsStopTheSaveAndSayWhy() {
        val source = FakeSource(register())
        val model = model(source)
        model.startNew()
        model.type("id", "P1")
        model.type("name", "Copy")
        model.type("lei", "SHORT")
        runImmediate { model.save() }
        assertTrue(source.saved.isEmpty())
        assertTrue(model.state.problems.any { it.contains("already used") })
        assertTrue(model.state.problems.any { it.contains("20 characters") })
    }

    @Test
    fun deleteAsksFirst() {
        val source = FakeSource(register())
        val model = model(source)
        runImmediate { model.delete("P1") }
        assertTrue(source.deleted.isEmpty())
        assertEquals("P1", model.state.confirmDelete)
        runImmediate { model.delete("P1") }
        assertEquals(listOf("P1"), source.deleted)
        assertTrue(model.state.lines.isEmpty())
    }

    @Test
    fun viewersCannotOpenAForm() {
        val model = model(FakeSource(register()), canEdit = false)
        model.startNew()
        model.startEdit("P1")
        assertNull(model.state.form)
    }

    @Test
    fun aContractNeedsADateAndAListedProvider() {
        val source = FakeSource(register())
        val model = model(source)
        model.selectKind(ItemKind.CONTRACTS)
        model.startNew()
        model.type("id", "C9")
        model.type("provider", "P9")
        model.type("start", "31.01.2026")
        runImmediate { model.save() }
        assertTrue(model.state.problems.any { it.contains("not a date") })
        assertTrue(model.state.problems.any { it.contains("Provider") })

        model.type("provider", "P1")
        model.type("start", "2026-01-31")
        model.type("function", "F1")
        runImmediate { model.save() }
        val contract = source.saved.single() as Contract
        assertEquals(LocalDate(2026, 1, 31), contract.startDate)
        assertEquals("F1", contract.services.single().functionId.value)
    }

    @Test
    fun aHandRowKeepsOnlyFilledColumnsOfItsTemplate() {
        val source = FakeSource(register())
        val model = model(source)
        model.selectKind(ItemKind.ROWS)
        model.selectTemplate("B_01.03")
        model.startNew()
        model.type("id", "branch-de")
        model.type("B_01.03.0030", "Frankfurt branch")
        runImmediate { model.save() }
        val (template, row) = source.saved.single() as Pair<*, *>
        assertEquals("B_01.03", template)
        assertEquals(setOf("B_01.03.0030"), (row as RoiRow).fields.keys)
    }

    @Test
    fun searchMatchesEveryTypedWordInKeyOrLabel() {
        val options = listOf(RoiOption("S18", "Cloud services: IaaS"), RoiOption("S19", "Cloud services: PaaS"), RoiOption("S08", "Computation"))
        assertEquals(listOf("S19"), matching(options, "cloud paas").map { it.key })
        assertEquals(3, matching(options, "").size)
        assertEquals(listOf("S08"), matching(options, "s08").map { it.key })
    }

    @Test
    fun switchingAwayFromAChangedFormAsksFirst() {
        val model = model(FakeSource(register()))
        model.startNew()
        model.type("name", "Hosting Ltd")
        model.selectKind(ItemKind.CONTRACTS)
        assertTrue(model.state.askDiscard)
        assertEquals(ItemKind.PROVIDERS, model.state.kind)
        model.keepEditing()
        assertEquals("Hosting Ltd", model.state.form?.fields?.first { it.key == "name" }?.text)
        model.selectKind(ItemKind.CONTRACTS)
        model.discardChanges()
        assertEquals(ItemKind.CONTRACTS, model.state.kind)
        assertNull(model.state.form)
    }

    @Test
    fun anUnchangedFormIsLeftWithoutAsking() {
        val model = model(FakeSource(register()))
        model.startNew()
        model.selectKind(ItemKind.CONTRACTS)
        assertFalse(model.state.askDiscard)
        assertEquals(ItemKind.CONTRACTS, model.state.kind)
    }
}
