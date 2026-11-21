package dorai.app.connect

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import dorai.app.components.AppLanguage
import dorai.app.theme.ThemeMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okio.Path.Companion.toPath
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class DataStoreSettingsTest {
    private val file = Files.createTempDirectory("dorai-settings").resolve("settings.preferences_pb")
    private val settings = DataStoreSettings(PreferenceDataStoreFactory.createWithPath(produceFile = { file.toString().toPath() }))

    /** Saves run in the background; waits until the file shows [expected]. */
    private fun eventually(expected: SavedSettings) = runBlocking {
        withTimeout(5_000) { while (settings.load() != expected) delay(10) }
        assertEquals(expected, settings.load())
    }

    @Test
    fun anEmptyFileMeansNothingSaved() = runBlocking {
        assertEquals(SavedSettings(), settings.load())
    }

    @Test
    fun everythingSavedComesBack() {
        val saved = SavedSettings(ServerLink("https://dorai.firm.local:8443", "AB".repeat(32)), "luka", ThemeMode.LIGHT, AppLanguage.GERMAN)
        settings.save(saved)
        eventually(saved)
    }

    @Test
    fun theLastSaveWinsAndClearedValuesAreRemoved() {
        settings.save(SavedSettings(ServerLink("https://a:8443", "AB".repeat(32)), "luka", ThemeMode.LIGHT))
        settings.save(SavedSettings(ServerLink("http://localhost:8080"), username = null))
        eventually(SavedSettings(ServerLink("http://localhost:8080")))
    }
}
