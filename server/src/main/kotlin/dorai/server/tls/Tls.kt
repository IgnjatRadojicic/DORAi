package dorai.server.tls

import io.ktor.network.tls.certificates.buildKeyStore
import io.ktor.network.tls.certificates.saveToFile
import org.slf4j.LoggerFactory
import java.io.File
import java.net.URI
import java.security.KeyStore
import java.security.MessageDigest

/**
 * HTTPS with a self-signed certificate (plan section 5): the apps trust it by pinning its SHA-256
 * fingerprint from the invite link, so no certificate authority is involved.
 */
data class TlsConfig(val port: Int, val keystore: File, val password: String, val domains: List<String>) {
    override fun toString() = "TlsConfig(port=$port, keystore=$keystore, domains=$domains)"

    companion object {
        /** Null when DORAI_TLS_KEYSTORE is not set: HTTP only (development). */
        fun fromEnv(env: Map<String, String> = System.getenv()): TlsConfig? {
            fun get(name: String) = env[name]?.trim()?.takeIf { it.isNotEmpty() }
            val path = get("DORAI_TLS_KEYSTORE") ?: return null
            val password = get("DORAI_TLS_KEYSTORE_PASSWORD") ?: error("DORAI_TLS_KEYSTORE is set but DORAI_TLS_KEYSTORE_PASSWORD is not.")
            val host = get("DORAI_PUBLIC_URL")?.let { runCatching { URI(it).host }.getOrNull() }
            return TlsConfig(
                port = get("DORAI_TLS_PORT")?.toIntOrNull() ?: 8443,
                keystore = File(path),
                password = password,
                domains = listOfNotNull(host, "localhost").distinct(),
            )
        }
    }
}

object Tls {
    const val ALIAS = "dorai"
    private val log = LoggerFactory.getLogger(Tls::class.java)

    /** Loads the keystore, or creates one with a new self-signed certificate on the first start. */
    fun loadOrCreate(config: TlsConfig): KeyStore {
        if (config.keystore.exists()) return KeyStore.getInstance(config.keystore, config.password.toCharArray())
        config.keystore.absoluteFile.parentFile?.mkdirs()
        val store = buildKeyStore {
            certificate(ALIAS) {
                password = config.password
                domains = config.domains
                daysValid = 3650
            }
        }
        store.saveToFile(config.keystore, config.password)
        log.info("Created a self-signed certificate for {} in {}.", config.domains, config.keystore)
        return store
    }

    /** SHA-256 of the certificate, upper-case hex without separators, as in `fp=sha256:...` links. */
    fun fingerprint(store: KeyStore): String =
        MessageDigest.getInstance("SHA-256").digest(store.getCertificate(ALIAS).encoded).joinToString("") { "%02X".format(it) }
}
