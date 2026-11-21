package dorai.app.shell

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import dorai.app.auth.KeyringTokenVault
import dorai.app.connect.DataStoreSettings
import okio.Path.Companion.toPath
import java.io.File

/** Settings in the user's application data folder, tokens in the operating system's keychain. */
fun desktopStorage(): AppStorage {
    val file = File(settingsFolder(), "settings.preferences_pb")
    val store = PreferenceDataStoreFactory.createWithPath(produceFile = { file.absolutePath.toPath() })
    return AppStorage(DataStoreSettings(store), KeyringTokenVault())
}

/** `%APPDATA%\DORAi` on Windows, `~/Library/Application Support/DORAi` on macOS, `~/.config/dorai` elsewhere. */
private fun settingsFolder(): File {
    val home = System.getProperty("user.home")
    val os = System.getProperty("os.name").lowercase()
    return when {
        os.startsWith("windows") -> File(System.getenv("APPDATA") ?: home, "DORAi")
        os.startsWith("mac") -> File(home, "Library/Application Support/DORAi")
        else -> File(System.getenv("XDG_CONFIG_HOME") ?: "$home/.config", "dorai")
    }
}
