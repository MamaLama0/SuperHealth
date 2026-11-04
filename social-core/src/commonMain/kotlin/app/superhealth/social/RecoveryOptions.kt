package app.superhealth.social

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable data class SocialSharing(val index: Boolean = false, val parts: Boolean = false, val activities: Boolean = false,
    val progress: Boolean = false, val photo: Boolean = false)
@Serializable data class SocialReading(val hidden: Set<String> = emptySet(), val muted: Set<String> = emptySet(),
    val quiet: Set<String> = emptySet(), val silent: Set<String> = emptySet(), val blocked: Set<String> = emptySet(),
    val aliases: Map<String, String> = emptyMap(), val revocations: Set<String> = emptySet(), val circles: Set<String> = emptySet())
@Serializable data class SocialAlertOptions(val friends: Boolean = true, val posts: Boolean = true, val replies: Boolean = true,
    val reactions: Boolean = true, val progress: Boolean = true, val frequency: Int = 4, val onOpen: Boolean = false)
@Serializable data class SocialArchive(val kind: String = "superhealth-social", val version: Int = 1, val profile: JsonObject,
    val events: List<JsonObject>, val proofs: Map<String, String> = emptyMap(), val spaces: SpaceStore? = null)
