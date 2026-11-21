package dorai.shared.roi

/**
 * Which register item a RoI row belongs to, so one cell can be edited where it is stored. Rows of the
 * other templates are derived from these items (or are extra rows edited as a whole), so they have none.
 */
object RoiItems {
    sealed interface Target {
        data object Entity : Target
        data class Item(val id: String) : Target
    }

    fun target(template: String, rowKey: String): Target? = when (template) {
        "B_01.01", "B_01.02" -> Target.Entity
        "B_02.01", "B_05.01", "B_06.01" -> Target.Item(rowKey)
        "B_02.02" -> Target.Item(rowKey.substringBefore('#'))
        "B_07.01" -> Target.Item(rowKey.substringBefore('|'))
        else -> null
    }
}
