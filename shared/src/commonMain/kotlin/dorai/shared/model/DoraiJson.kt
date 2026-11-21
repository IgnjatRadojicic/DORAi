package dorai.shared.model

import kotlinx.serialization.json.Json

/** The one JSON setup for server, apps and CLI, so both sides read and write the same wire format. */
val DoraiJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}
