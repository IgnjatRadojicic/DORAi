package dorai.app.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * Desktop keeps the session in memory only, so nothing secret reaches the disk unprotected.
 * Slot for the OS keychain: once `java-keyring` is in the app module, return a vault backed by it here.
 */
@Composable
actual fun rememberSecretVault(): SecretVault = remember { MemoryVault() }
