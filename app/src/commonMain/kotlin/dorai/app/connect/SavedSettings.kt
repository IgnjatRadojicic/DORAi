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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** The server an app talks to: its address and, for `https`, the certificate fingerprint from the invite link. */
data class ServerLink(val address: String, val fingerprint: String? = null)

/** A server the app has reached through an invite, with the last username used there. */
data class SavedServer(val link: ServerLink, val username: String? = null)

/**
 * What the app remembers between starts. None of it is secret; the tokens live in a [dorai.app.auth.TokenVault],
 * one session per server.
 */
data class SavedSettings(
    /** The server in use, and the username last used on it. */
    val server: ServerLink? = null,
    val username: String? = null,
    val theme: ThemeMode? = null,
    val language: AppLanguage? = null,
    /** The workspace section open when the app closed; it opens again after the next sign-in. */
    val section: String? = null,
    /** Every server reached through an invite, so switching back needs no new invite. */
    val servers: List<SavedServer> = emptyList(),
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
        val server = prefs[SERVER]?.let { ServerLink(it, prefs[FINGERPRINT]) }
        SavedSettings(
            server = server,
            username = prefs[USERNAME],
            // Earlier versions kept only the server in use; it becomes the first on the list.
            servers = prefs[SERVERS]?.let(::decodeServers) ?: listOfNotNull(server?.let { SavedServer(it, prefs[USERNAME]) }),
            theme = prefs[THEME]?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } },
            language = prefs[LANGUAGE]?.let { name -> AppLanguage.entries.firstOrNull { it.name == name } },
            section = prefs[SECTION],
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
                    prefs.put(SERVERS, encodeServers(settings.servers))
                    prefs.put(THEME, settings.theme?.name)
                    prefs.put(LANGUAGE, settings.language?.name)
                    prefs.put(SECTION, settings.section)
                }
            }
        }
    }

    private fun MutablePreferences.put(key: Preferences.Key<String>, value: String?) {
        if (value == null) remove(key) else set(key, value)
    }

    private fun encodeServers(servers: List<SavedServer>): String = buildJsonArray {
        servers.forEach { saved ->
            add(buildJsonObject {
                put("address", saved.link.address)
                saved.link.fingerprint?.let { put("fingerprint", it) }
                saved.username?.let { put("username", it) }
            })
        }
    }.toString()

    /** A list that does not parse is dropped, like the rest of an unreadable file. */
    private fun decodeServers(text: String): List<SavedServer> = runCatching {
        Json.parseToJsonElement(text).jsonArray.mapNotNull { element ->
            val fields = element.jsonObject
            val address = fields["address"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            SavedServer(
                ServerLink(address, fields["fingerprint"]?.jsonPrimitive?.contentOrNull),
                fields["username"]?.jsonPrimitive?.contentOrNull,
            )
        }
    }.getOrDefault(emptyList())

    private companion object {
        val SERVERS = stringPreferencesKey("servers")
        val SERVER = stringPreferencesKey("server")
        val FINGERPRINT = stringPreferencesKey("fingerprint")
        val USERNAME = stringPreferencesKey("username")
        val THEME = stringPreferencesKey("theme")
        val LANGUAGE = stringPreferencesKey("language")
        val SECTION = stringPreferencesKey("section")
    }
}
