package app.superhealth.bridge

import app.superhealth.HcSession

/** Identités des séances et des mesures associées écrites par les applications sources. */
internal object HcSessions {
    fun identity(session: HcSession) = session.app to (session.clientRecordId ?: session.id)

    /** La fenêtre relue remplace son ancien contenu. Les imports plus anciens restent dans l'historique. */
    fun merge(previous: List<HcSession>, incoming: List<HcSession>, fromMs: Long): List<HcSession> =
        (previous.filter { it.start < fromMs } + incoming)
            .associateBy(::identity).values.sortedByDescending { it.start }

    fun metadataChanged(previous: List<HcSession>, recent: List<HcSession>, fromMs: Long): Boolean {
        val cached = previous.filter { it.start >= fromMs }.associateBy { it.app to it.id }
        val current = recent.filter { it.start >= fromMs }
        return cached.size != current.size || current.any { session ->
            val old = cached[session.app to session.id]
            old == null || old.modifiedAt != session.modifiedAt
        }
    }

    /** null indique un fournisseur dont la convention d'identifiants n'est pas connue. */
    fun metricClientId(app: String, sessionClientId: String?, distance: Boolean): String? {
        if (sessionClientId.isNullOrBlank()) return null
        return when (app) {
            "com.garmin.android.apps.connectmobile" -> sessionClientId
            "com.strava" -> sessionClientId.removePrefix("strava-exercise-")
                .takeIf { sessionClientId.startsWith("strava-exercise-") && it.isNotEmpty() && it.all(Char::isDigit) }
                ?.let { "strava-${if (distance) "distance" else "calories"}-$it" }
            else -> null
        }
    }
}
