package dorai.app.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppLanguageTest {
    @Test
    fun catalogHasUnambiguousKeysAndCompleteTranslations() {
        assertEquals(UiCopy.entries.size, UiCopy.entries.map { it.english }.toSet().size)
        UiCopy.entries.forEach { copy ->
            AppLanguage.entries.forEach { language ->
                assertTrue(language.text(copy).isNotBlank(), "Missing ${language.code} translation for ${copy.name}")
            }
        }
    }

    @Test
    fun translationTemplatesKeepTheirArgumentsAcrossLanguages() {
        val placeholders = Regex("\\{[a-z]+}")
        UiCopy.entries.forEach { copy ->
            val expected = placeholders.findAll(copy.english).map { it.value }.toSet()
            AppLanguage.entries.forEach { language ->
                assertEquals(expected, placeholders.findAll(language.text(copy)).map { it.value }.toSet(), "${copy.name} / ${language.code}")
            }
        }
        assertEquals("2 / 4 constats", AppLanguage.FRENCH.format(UiCopy.FINDING_COUNT_MANY, "shown" to 2, "total" to 4))
        assertEquals("1 Problem", AppLanguage.GERMAN.format(UiCopy.ISSUE_COUNT_ONE, "count" to 1))
    }

    @Test
    fun untranslatedResourceIdentifiersAndSourceExcerptsArePreserved() {
        val sourceValues = listOf("aws_db_instance.payments_replica", "B_02.02", "A quotation from a regulation.")
        AppLanguage.entries.forEach { language ->
            sourceValues.forEach { assertEquals(it, language.translateUi(it)) }
        }
    }
}
