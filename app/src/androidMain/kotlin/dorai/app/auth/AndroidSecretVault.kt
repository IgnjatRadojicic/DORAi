package dorai.app.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/** In memory for now; the Android Keystore vault replaces this in the next change. */
@Composable
actual fun rememberSecretVault(): SecretVault = remember { MemoryVault() }
