package app.superhealth.social

import kotlinx.serialization.Serializable

/** Track independent gaps so refreshing the head cannot erase progress through older pages. */
@Serializable data class PageProgress(val pending: Set<String> = emptySet(), val visited: Set<String> = emptySet()) {
    val next: String? get() = pending.maxOrNull()
    fun advance(requested: String?, following: String?): PageProgress {
        val seen = (visited + listOfNotNull(requested)).toList().takeLast(10000).toSet()
        return PageProgress(((pending - listOfNotNull(requested).toSet()) + listOfNotNull(following)).filterNot { it in seen }.toSet(), seen)
    }
    companion object { fun legacy(cursor: String?) = PageProgress(listOfNotNull(cursor).toSet()) }
}
