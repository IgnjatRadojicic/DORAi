package dorai.app.shell

import dorai.app.auth.MemoryTokenVault
import dorai.app.auth.TokenVault
import dorai.app.connect.MemorySettingsStore
import dorai.app.connect.SettingsStore

/** What the app keeps between starts; each platform builds its own (desktop: keychain, Android: Keystore). */
class AppStorage(val settings: SettingsStore, val tokens: TokenVault) {
    companion object {
        /** Forgets everything when the app closes: tests and previews. */
        fun inMemory() = AppStorage(MemorySettingsStore(), MemoryTokenVault())
    }
}
