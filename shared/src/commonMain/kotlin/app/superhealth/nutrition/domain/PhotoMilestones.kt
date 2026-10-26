package app.superhealth.nutrition.domain

import app.superhealth.platform.*

import app.superhealth.SuperPrefs
import app.superhealth.nutrition.data.Analytics
import app.superhealth.nutrition.data.ProgressPhotoEntity
import kotlin.math.abs

/**
 * Photos de progression proposées aux paliers de l'objectif plutôt qu'à intervalle régulier : une photo de départ, puis
 * à 25, 50, 75 et 100 % du chemin entre le poids de départ et le poids visé. L'avancée se lit sur le poids tendance
 * (celui de l'app), pas sur une pesée isolée : un palier atteint par une pesée basse un matin ne compte pas.
 */
object PhotoMilestones {
    val STEPS = listOf(0, 25, 50, 75, 100)

    /** Part du chemin parcourue (0…1) par le poids tendance du jour [day] ; null en maintien ou sans poids visé. */
    fun progressAt(an: Analytics, day: Long = an.today): Double? {
        val g = an.goalAt(day) ?: return null
        val target = g.targetWeightKg ?: return null
        if (g.type == GoalType.MAINTENANCE) return null
        val span = g.startWeightKg - target
        if (abs(span) < 0.1) return null
        val w = an.trend.lastOrNull { it.epochDay <= day }?.trend ?: return null
        return ((g.startWeightKg - w) / span).coerceIn(0.0, 1.0)
    }

    private fun done(an: Analytics): List<Int> {
        val g = an.currentGoal ?: return emptyList()
        val s = SuperPrefs.settings.value
        return if (s.photoGoalStart == g.startDay) s.photoMilestones else emptyList()
    }

    /** Palier à photographier maintenant (le plus haut atteint pas encore fait), ou null. 0 : la photo de départ. */
    fun due(an: Analytics, photos: List<ProgressPhotoEntity>): Int? {
        val g = an.currentGoal ?: return null
        val p = progressAt(an)
        val reached = if (p == null) listOf(0) else STEPS.filter { p * 100 >= it - 1e-6 }
        val m = reached.lastOrNull { it !in done(an) } ?: return null
        // photo de départ déjà là (prise depuis le début de l'objectif)
        if (m == 0 && photos.any { it.epochDay >= g.startDay }) return null
        return m
    }

    /** Photo prise ou proposition écartée : tous les paliers atteints jusqu'à [m] sont faits. */
    fun markDone(an: Analytics, m: Int) {
        val g = an.currentGoal ?: return
        val prev = done(an)
        SuperPrefs.update { it.copy(photoGoalStart = g.startDay, photoMilestones = (prev + STEPS.filter { s -> s <= m }).distinct()) }
    }
}
