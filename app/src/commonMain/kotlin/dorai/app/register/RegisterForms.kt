package dorai.app.register

import dorai.shared.model.BusinessFunction
import dorai.shared.model.Contract
import dorai.shared.model.ContractService
import dorai.shared.model.FinancialEntity
import dorai.shared.model.Provenance
import dorai.shared.model.ProvenanceLabel
import dorai.shared.model.Provider
import dorai.shared.model.Register
import dorai.shared.model.RegisterField
import dorai.shared.model.RoiRow
import dorai.shared.roi.RoiOption
import dorai.shared.roi.RoiSpec
import dorai.shared.roi.SERVICE_TYPES
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/** The kinds of register items a person creates and edits by hand. */
enum class ItemKind(val title: String) {
    ENTITY("Entity"),
    PROVIDERS("Providers"),
    CONTRACTS("Contracts"),
    FUNCTIONS("Functions"),
    ROWS("Other rows"),
}

/** One input of a form; [choices] turns it into a pick list. */
data class FormField(
    val key: String,
    val label: String,
    val text: String = "",
    val choices: List<RoiOption> = emptyList(),
    val required: Boolean = false,
)

/** A form for one item: [id] is null for a new item; [template] is set for a row of a template. */
data class ItemForm(val kind: ItemKind, val id: String?, val fields: List<FormField>, val template: String? = null) {
    val isNew: Boolean get() = id == null
    fun text(key: String): String = fields.firstOrNull { it.key == key }?.text?.trim().orEmpty()
    fun with(key: String, text: String) = copy(fields = fields.map { if (it.key == key) it.copy(text = text) else it })
}

/**
 * Builds the forms from register items and the items back from the forms. Only what a person decides is here:
 * identifiers, names, dates, amounts and links. RoI columns are set cell by cell on the RoI page, and every
 * value saved here becomes `human-confirmed` (an unchanged value keeps where it came from).
 */
object RegisterForms {
    /** Templates that have no register item of their own, so their rows are typed by hand. */
    val handTemplates = listOf("B_01.02", "B_01.03", "B_02.03", "B_03.03", "B_05.02", "B_99.01")

    private const val ID = "id"
    private val yesNo = listOf(RoiOption("true", "Yes"), RoiOption("false", "No"))

    fun entity(e: FinancialEntity) = ItemForm(ItemKind.ENTITY, e.lei, listOf(
        FormField("lei", "LEI, the Legal Entity Identifier (20 characters)", e.lei, required = true),
        FormField("name", "Legal name", e.name, required = true),
        FormField("country", "Country of the head office (two letters, for example IE)", e.country, required = true),
    ))

    fun provider(p: Provider?) = ItemForm(ItemKind.PROVIDERS, p?.id, idField(p?.id, "PRV-01") + listOf(
        FormField("name", "Name", p?.name.orEmpty(), required = true),
        FormField("lei", "LEI, the Legal Entity Identifier (blank if it has none)", p?.lei?.value.orEmpty()),
        FormField("country", "Country of the head office (two letters)", p?.headquartersCountry?.value.orEmpty()),
    ))

    fun function(f: BusinessFunction?) = ItemForm(ItemKind.FUNCTIONS, f?.id, idField(f?.id, "F-PAYMENTS") + listOf(
        FormField("name", "Name", f?.name.orEmpty(), required = true),
        FormField("critical", "Critical or important function", f?.isCritical?.toString() ?: "false", yesNo, required = true),
    ))

    /** A contract and its first service; further services of a scanned contract stay as they are. */
    fun contract(c: Contract?, register: Register): ItemForm {
        val service = c?.services?.firstOrNull()
        return ItemForm(ItemKind.CONTRACTS, c?.id, idField(c?.id, "CTR-2026-01") + listOf(
            FormField("provider", "Provider", c?.providerId.orEmpty(), register.providers.map { RoiOption(it.id, it.name) }, required = true),
            FormField("start", "Start date (YYYY-MM-DD)", c?.startDate?.toString().orEmpty(), required = true),
            FormField("end", "End date (YYYY-MM-DD, blank if open-ended)", c?.endDate?.toString().orEmpty()),
            FormField("cost", "Annual cost (for example 120000.00)", c?.annualCost?.value.orEmpty()),
            FormField("currency", "Currency (three letters, for example EUR)", c?.currency.orEmpty()),
            FormField("serviceType", "Type of ICT service", service?.serviceType?.value.orEmpty(), SERVICE_TYPES),
            FormField("function", "Function the service supports", service?.functionId?.value.orEmpty(),
                register.functions.map { RoiOption(it.id, it.name) }),
            FormField("dataLocation", "Country where the data is stored (two letters)", service?.dataLocation?.value.orEmpty()),
        ))
    }

    /** A row of [template]: one input per column of the template, with the CIR's options where it has them. */
    fun row(template: String, row: RoiRow?): ItemForm {
        val spec = RoiSpec.template(template)
        return ItemForm(ItemKind.ROWS, row?.id, idField(row?.id, template.lowercase() + "-1") + spec.columns.map { col ->
            FormField(col.code, "${col.header} ${col.name}", row?.fields?.get(col.code)?.value.orEmpty(), col.options)
        }, template)
    }

    private fun idField(id: String?, example: String) =
        if (id == null) listOf(FormField(ID, "Identifier, unique in the register (for example $example)", required = true)) else emptyList()

    /** What stops a save, in words; empty when the form can be saved. [taken]: identifiers already in use. */
    fun problems(form: ItemForm, taken: Set<String> = emptySet()): List<String> = buildList {
        form.fields.filter { it.required && it.text.isBlank() }.forEach { add("${it.label}: required.") }
        if (form.isNew && form.text(ID) in taken) add("The identifier ${form.text(ID)} is already used.")
        if (form.kind == ItemKind.ENTITY && form.text("lei").length != 20) add("An LEI has 20 characters.")
        if (form.kind == ItemKind.PROVIDERS && form.text("lei").let { it.isNotEmpty() && it.length != 20 }) add("An LEI has 20 characters.")
        listOf("country", "dataLocation").forEach { key ->
            if (form.text(key).let { it.isNotEmpty() && !Regex("[A-Za-z]{2}").matches(it) }) add("Countries are two letters, for example IE.")
        }
        listOf("start", "end").forEach { key ->
            form.text(key).takeIf { it.isNotEmpty() }?.let { if (runCatching { LocalDate.parse(it) }.isFailure) add("$it is not a date like 2026-01-31.") }
        }
        if (form.text("cost").let { it.isNotEmpty() && it.toDoubleOrNull() == null }) add("The annual cost is a number like 120000.00.")
        if (form.text("currency").let { it.isNotEmpty() && !Regex("[A-Za-z]{3}").matches(it) }) add("A currency is three letters, for example EUR.")
        form.fields.filter { it.choices.isNotEmpty() && it.text.isNotBlank() && it.choices.none { c -> c.key == it.text.trim() } }
            .forEach { add("${it.label}: pick one of the listed options.") }
    }

    fun toEntity(form: ItemForm, old: FinancialEntity) =
        old.copy(lei = form.text("lei").uppercase(), name = form.text("name"), country = form.text("country").uppercase())

    fun toProvider(form: ItemForm, old: Provider?, now: Instant) = Provider(
        id = old?.id ?: form.text(ID),
        name = form.text("name"),
        lei = field(old?.lei, form.text("lei").uppercase(), now),
        headquartersCountry = field(old?.headquartersCountry, form.text("country").uppercase(), now),
        roi = old?.roi.orEmpty(),
    )

    fun toFunction(form: ItemForm, old: BusinessFunction?) = BusinessFunction(
        id = old?.id ?: form.text(ID),
        name = form.text("name"),
        isCritical = form.text("critical") == "true",
        roi = old?.roi.orEmpty(),
    )

    fun toContract(form: ItemForm, old: Contract?, now: Instant): Contract {
        val id = old?.id ?: form.text(ID)
        val first = old?.services?.firstOrNull()
        val service = ContractService(
            id = first?.id ?: "$id-s1",
            serviceType = field(first?.serviceType, form.text("serviceType"), now),
            functionId = field(first?.functionId, form.text("function"), now),
            dataLocation = field(first?.dataLocation, form.text("dataLocation").uppercase(), now),
            resourceIds = first?.resourceIds.orEmpty(),
            roi = first?.roi.orEmpty(),
        )
        val serviceTyped = listOf("serviceType", "function", "dataLocation").any { form.text(it).isNotEmpty() }
        return Contract(
            id = id,
            providerId = form.text("provider"),
            startDate = LocalDate.parse(form.text("start")),
            endDate = form.text("end").ifEmpty { null }?.let(LocalDate::parse),
            annualCost = field(old?.annualCost, form.text("cost"), now),
            currency = form.text("currency").uppercase().ifEmpty { null },
            services = if (first != null || serviceTyped) listOf(service) + old?.services.orEmpty().drop(1) else emptyList(),
            roi = old?.roi.orEmpty(),
        )
    }

    fun toRow(form: ItemForm, old: RoiRow?, now: Instant) = RoiRow(
        id = old?.id ?: form.text(ID),
        fields = form.fields.filter { it.key != ID && it.text.isNotBlank() }
            .associate { it.key to field(old?.fields?.get(it.key), it.text.trim(), now) },
    )

    /** An unchanged value keeps its provenance; a changed one is the person's (the server stamps who). */
    private fun field(old: RegisterField?, text: String, now: Instant): RegisterField {
        val value = text.trim().ifEmpty { null }
        return if (old != null && old.value == value) old else RegisterField(value, Provenance(ProvenanceLabel.HumanConfirmed, now))
    }
}
