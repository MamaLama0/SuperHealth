package app.superhealth.health.data

import app.superhealth.bridge.Bridge
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind

/**
 * Une nuit résumée (celle qui se termine le jour [date]) : bornes, temps par phase et réveils. Base de l'écran Sommeil
 * et des cartes Sommeil, Horaires, Qualité et Cœur la nuit. Le léger regroupe aussi le sommeil sans phase.
 */
class NightSummary(val date: LocalDate, val phases: List<HealthRepository.SleepPhase>) {
    val staged: Boolean = Bridge.hasStages(phases)
    val bedMs: Long = phases.minOf { it.start }
    val wakeMs: Long = phases.maxOf { it.end }
    private fun minutes(vararg k: ActivityKind) = phases.filter { it.kind in k }.sumOf { it.end - it.start } / 60_000.0
    val awakeMin: Double = minutes(ActivityKind.AWAKE_SLEEP)
    val asleepMin: Double = phases.sumOf { it.end - it.start } / 60_000.0 - awakeMin
    val deepMin: Double = minutes(ActivityKind.DEEP_SLEEP)
    val remMin: Double = minutes(ActivityKind.REM_SLEEP)
    val lightMin: Double = (asleepMin - deepMin - remMin).coerceAtLeast(0.0)
    val inBedMin: Double = (wakeMs - bedMs) / 60_000.0
    /** Endormi / au lit, comme la partie « Efficacité » de Repos (endormi + éveils notés). */
    val efficiency: Double = if (asleepMin + awakeMin > 0) asleepMin / (asleepMin + awakeMin) else 0.0

    /** Réveils : périodes d'éveil entre le premier et le dernier sommeil (l'éveil du coucher et du lever ne compte pas). */
    val awakenings: Int = phases.filter { it.kind != ActivityKind.AWAKE_SLEEP }.let { s ->
        if (s.isEmpty()) 0 else phases.count { it.kind == ActivityKind.AWAKE_SLEEP && it.start >= s.minOf { p -> p.start } && it.end <= s.maxOf { p -> p.end } }
    }

    /** Minutes du coucher et du lever comptées depuis midi la veille (pour moyenner des heures de part et d'autre de minuit). */
    val bedClock: Double get() = (bedMs - noon(date)) / 60_000.0
    val wakeClock: Double get() = (wakeMs - noon(date)) / 60_000.0

    companion object {
        fun noon(date: LocalDate) = HealthRepository.startMs(date) - 12 * 3_600_000L
    }
}

object Nights {
    /** Nuit qui se termine le jour [date] ; null sans nuit enregistrée. */
    suspend fun of(date: LocalDate): NightSummary? =
        runCatching { Bridge.sleepNight(date) }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { NightSummary(date, it) }

    /** Nuits de [from] à [to] inclus, lues en parallèle (null : pas de nuit ce jour-là). */
    suspend fun range(from: LocalDate, to: LocalDate): List<Pair<LocalDate, NightSummary?>> = withContext(Dispatchers.IO) {
        coroutineScope {
            generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.toList()
                .map { d -> async { d to of(d) } }.awaitAll()
        }
    }

    /** Pouls minute par minute (ms, bpm) entre le coucher et le lever. */
    suspend fun heart(n: NightSummary): List<Pair<Long, Int>> = withContext(Dispatchers.IO) {
        (runCatching { Bridge.minuteHeart(n.date.minusDays(1)) }.getOrDefault(emptyList()) + runCatching { Bridge.minuteHeart(n.date) }.getOrDefault(emptyList()))
            .map { it.first * 1000 to it.second }.filter { it.first in n.bedMs..n.wakeMs }.sortedBy { it.first }
    }

    /**
     * Pouls le plus bas de la nuit : la plus basse des moyennes glissantes sur 5 minutes (un battement isolé ne compte
     * pas), et son heure. Null sous 30 minutes mesurées.
     */
    fun lowest(heart: List<Pair<Long, Int>>): Pair<Long, Double>? {
        if (heart.size < 30) return null
        var best: Pair<Long, Double>? = null
        var i = 0
        for (j in heart.indices) {
            while (heart[j].first - heart[i].first >= 5 * 60_000L) i++
            if (heart[j].first - heart[i].first < 4 * 60_000L) continue
            val avg = heart.subList(i, j + 1).map { it.second }.average()
            if (best == null || avg < best.second) best = heart[(i + j) / 2].first to avg
        }
        // mesures espacées (une toutes les 5 à 10 min, fréquent via Health Connect) : aucune fenêtre de 4 min pleine,
        // le point bas est alors la tranche de 5 min la plus basse
        return best ?: fiveMinutes(heart).minByOrNull { it.second }
    }

    /** Pouls moyen par tranche de 5 minutes (ms, bpm), pour une courbe lisible. */
    fun fiveMinutes(heart: List<Pair<Long, Int>>): List<Pair<Long, Double>> =
        heart.groupBy { it.first / 300_000L }.map { (k, v) -> k * 300_000L + 150_000L to v.map { it.second }.average() }.sortedBy { it.first }

    /** Médiane d'une liste (null si vide). */
    fun median(xs: List<Double>): Double? = xs.sorted().takeIf { it.isNotEmpty() }?.let { s ->
        if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2
    }

    /** Minutes depuis midi → « 23:15 » (heure de la journée). */
    fun clock(minutesFromNoon: Double): String = app.superhealth.ui.timeOf(NightSummary.noon(LocalDate.now()) + minutesFromNoon.toLong() * 60_000L)

    /** Heures pleines locales comprises entre [from] et [to] (ms). */
    fun hourMarks(from: Long, to: Long): List<Long> {
        val day = HealthRepository.startMs(app.superhealth.ui.localDate(from))
        return generateSequence(day) { it + 3_600_000L }.dropWhile { it <= from }.takeWhile { it < to }.toList()
    }
}
