package dorai.app.connect

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dorai.app.components.AppLanguage
import dorai.app.theme.ThemeMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** The server an app talks to: its address and, for `https`, the certificate fingerprint from the invite link. */
data class ServerLink(val address: String, val fingerprint: String? = null)

/** What the app remembers between starts. None of it is secret; the tokens live in a [dorai.app.auth.TokenVault]. */
data class SavedSettings(
    val server: ServerLink? = null,
    val username: String? = null,
    val theme: ThemeMode? = null,
    val language: AppLanguage? = null,
)

interface SettingsStore {
    suspend fun load(): SavedSettings

    /** Writes in the background, in call order; the user interface never waits for the disk. */
    fun save(settings: SavedSettings)
}

class MemorySettingsStore(private var settings: SavedSettings = SavedSettings()) : SettingsStore {
    override suspend fun load() = settings
    override fun save(settings: SavedSettings) { this.settings = settings }
}

/** [SettingsStore] in a DataStore preferences file, the same code on desktop and Android. */
class DataStoreSettings(private val store: DataStore<Preferences>) : SettingsStore {
    // One at a time, so a later save never lands before an earlier one.
    private val writes = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))

    override suspend fun load(): SavedSettings = try {
        val prefs = store.data.first()
        SavedSettings(
            server = prefs[SERVER]?.let { ServerLink(it, prefs[FINGERPRINT]) },
            username = prefs[USERNAME],
            theme = prefs[THEME]?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } },
            language = prefs[LANGUAGE]?.let { name -> AppLanguage.entries.firstOrNull { it.name == name } },
        )
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        // An unreadable file means starting fresh, not a broken app.
        SavedSettings()
    }

    override fun save(settings: SavedSettings) {
        writes.launch {
            runCatching {
                store.edit { prefs ->
                    prefs.put(SERVER, settings.server?.address)
                    prefs.put(FINGERPRINT, settings.server?.fingerprint)
                    prefs.put(USERNAME, settings.username)
                    prefs.put(THEME, settings.theme?.name)
                    prefs.put(LANGUAGE, settings.language?.name)
                }
            }
        }
    }

    private fun MutablePreferences.put(key: Preferences.Key<String>, value: String?) {
        if (value == null) remove(key) else set(key, value)
    }

    private companion object {
        val SERVER = stringPreferencesKey("server")
        val FINGERPRINT = stringPreferencesKey("fingerprint")
        val USERNAME = stringPreferencesKey("username")
        val THEME = stringPreferencesKey("theme")
        val LANGUAGE = stringPreferencesKey("language")
    }
}
