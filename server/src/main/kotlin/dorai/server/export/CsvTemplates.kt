package dorai.server.export

import dorai.shared.model.Register

internal enum class CsvTemplate(val code: String, val columns: List<Int>, val unavailable: List<Int>) {
    ENTITY("B_01.01", (1..6).toList(), listOf(4, 5, 6)),
    CONTRACTS("B_02.01", (1..5).toList(), listOf(2, 3)),
    SERVICES("B_02.02", (1..18).toList(), listOf(9, 10, 11, 12, 13, 16, 17, 18)),
    PROVIDERS("B_05.01", (1..12).toList(), listOf(3, 4, 7, 11, 12)),
    FUNCTIONS("B_06.01", listOf(1, 2, 3, 4, 6, 7, 8, 9, 10, 11), listOf(2, 7, 8, 9, 10, 11));

    val fileName: String get() = "$code.csv"
    fun column(number: Int): String = "$code.${(number * 10).toString().padStart(4, '0')}"
    val headers: List<String> get() = columns.map(::column)

    fun rowCount(register: Register): Int = when (this) {
        ENTITY -> 1
        CONTRACTS -> register.contracts.size
        SERVICES -> register.contracts.sumOf { it.services.size }
        PROVIDERS -> register.providers.size
        FUNCTIONS -> register.functions.size
    }
}

/** Rows are emitted in identifier order, independent of database enumeration order. */
internal fun CsvTemplate.rows(register: Register): Sequence<List<Any?>> = sequence {
    when (this@rows) {
        CsvTemplate.ENTITY -> yield(listOf(register.entity.lei, register.entity.name, register.entity.country, "", "", ""))
        CsvTemplate.CONTRACTS -> register.contracts.sortedBy { it.id }.forEach { contract ->
            yield(listOf(contract.id, "", "", contract.currency, ExportCells.amount(contract.annualCost)))
        }
        CsvTemplate.SERVICES -> {
            val providers = register.providers.associateBy { it.id }
            register.contracts.sortedBy { it.id }.forEach { contract ->
                val lei = providers[contract.providerId]?.let { ExportCells.text(it.lei) }.orEmpty()
                contract.services.sortedBy { it.id }.forEach { service ->
                    val location = ExportCells.text(service.dataLocation)
                    val serviceType = ExportCells.text(service.serviceType).takeUnless { it == "Unknown" }.orEmpty()
                    yield(listOf(
                        contract.id, register.entity.lei, lei, if (lei.isBlank()) "" else "LEI",
                        ExportCells.text(service.functionId), serviceType,
                        contract.startDate.toString(), contract.endDate?.toString() ?: "9999-12-31",
                        "", "", "", "", "", if (location.isBlank()) "" else "Yes", location, "", "", "",
                    ))
                }
            }
        }
        CsvTemplate.PROVIDERS -> register.providers.sortedBy { it.id }.forEach { provider ->
            val lei = ExportCells.text(provider.lei)
            val (currency, amount) = ExportCells.providerTotal(provider.id, register)
            yield(listOf(
                lei, if (lei.isBlank()) "" else "LEI", "", "", provider.name,
                provider.name.takeIf { name -> name.all { it.code < 128 } }.orEmpty(), "",
                ExportCells.text(provider.headquartersCountry), currency, amount, "", "",
            ))
        }
        CsvTemplate.FUNCTIONS -> register.functions.sortedBy { it.id }.forEach { function ->
            yield(listOf(function.id, "", function.name, register.entity.lei, if (function.isCritical) "Yes" else "No", "", "", "", "", ""))
        }
    }
}
