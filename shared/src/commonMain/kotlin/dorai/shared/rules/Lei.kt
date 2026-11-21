package dorai.shared.rules

/**
 * LEI format and ISO 7064 MOD 97-10 check digits (ISO 17442).
 * This does not tell whether an LEI is issued or currently registered.
 */
object Lei {

    fun isValid(lei: String?): Boolean {
        if (lei == null || lei.length != 20) return false
        val d1 = lei[18]
        val d2 = lei[19]
        if (d1 !in '0'..'9' || d2 !in '0'..'9') return false

        // The canonical check-digit calculation can only produce 02 through 98.
        val checkDigits = (d1 - '0') * 10 + (d2 - '0')
        if (checkDigits !in 2..98) return false

        var remainder = 0
        for (c in lei) {
            remainder = when (c) {
                in '0'..'9' -> (remainder * 10 + (c - '0')) % 97
                // A = 10, B = 11, ..., Z = 35: a letter contributes two digits.
                in 'A'..'Z' -> (remainder * 100 + (c - 'A') + 10) % 97
                else -> return false
            }
        }
        return remainder == 1
    }
}
