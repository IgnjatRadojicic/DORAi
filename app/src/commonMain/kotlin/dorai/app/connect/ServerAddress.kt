package dorai.app.connect

/** Address the Connect screen starts with: the local development server as this platform reaches it. */
expect val defaultServerAddress: String

/**
 * The typed address as the API client expects it (no trailing slash, no `/api`),
 * or null when it is not an http or https URL with a host.
 */
fun normalizeServerAddress(input: String): String? {
    val address = input.trim().trimEnd('/').removeSuffix("/api").trimEnd('/')
    if (address.any { it.isWhitespace() }) return null
    val scheme = listOf("http://", "https://").firstOrNull { address.startsWith(it, ignoreCase = true) } ?: return null
    val host = address.substring(scheme.length).substringBefore('/').substringBefore(':')
    return if (host.isEmpty()) null else address
}
