package app.superhealth.health.data

import android.content.Context
import app.superhealth.SuperPrefs
import app.superhealth.bridge.Bridge
import app.superhealth.nutrition.NutriApp
import app.superhealth.nutrition.data.Micros
import app.superhealth.nutrition.domain.Sex
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Objectif d'eau et repère de sodium du jour, pensés pour la musculation (3.8).
 *
 * Boissons visées = besoin de base + sueur des séances du jour (remplacée à 125 %) + eau pour éliminer l'urée d'un
 * apport protéique élevé. Le sodium perdu dans la sueur s'ajoute au plafond de base.
 *
 * Repères :
 * - Besoin de base : 35 ml/kg, jamais sous l'apport adéquat EFSA (2010) diminué des ~20 % apportés par les aliments
 *   (2,5 L → 2,0 L de boissons chez l'homme, 2,0 L → 1,6 L chez la femme).
 * - Sueur : 0,3–2,4 L/h selon l'effort (Sawka et al., ACSM 2007) ; ~0,6 L/h retenu par défaut en musculation, ou le
 *   taux mesuré par pesée avant/après séance. Remplacer 125–150 % de la perte après l'effort (ACSM 2007 ; Shirreffs 1996).
 * - Sodium de la sueur : ~950 mg/L en moyenne, 230–2 300 mg/L selon les personnes (Baker 2017).
 * - Protéines : chaque gramme au-delà de ~1 g/kg ajoute de l'urée à éliminer (~5,7 mmol/g) ; à 600–700 mOsm/L
 *   d'urine, cela demande ~8 ml d'eau (Martin et al. 2006, apport hydrique et régime hyperprotéiné).
 * - Sodium de base : < 2 000 mg/j (OMS 2012) ; potassium ≥ 3 510 mg/j ; rapport molaire Na:K ≤ 1.
 */
object Hydration {
    /** Facteur de sudation de chaque type de séance par rapport à la musculation. */
    private fun kindFactor(k: Bridge.Kind) = when (k) {
        Bridge.Kind.STRENGTH -> 1.0
        Bridge.Kind.RUN -> 1.6
        Bridge.Kind.BIKE -> 1.3
        Bridge.Kind.SWIM -> 0.8
        Bridge.Kind.HIKE -> 1.0
        Bridge.Kind.WALK, Bridge.Kind.YOGA -> 0.5
        Bridge.Kind.OTHER -> 1.0
    }

    const val DEFAULT_SWEAT_LH = 0.6
    private const val REPLACE = 1.25
    private const val ML_PER_EXTRA_PROTEIN_G = 8.0
    const val BASE_SODIUM_MG = 2000.0

    data class Session(val title: String, val kind: Bridge.Kind, val minutes: Long, val sweatL: Double)

    data class Plan(
        val date: LocalDate,
        val weightKg: Double?,
        val female: Boolean,
        /** Boissons visées (ml), et leur décomposition. */
        val goalMl: Double,
        val baseMl: Double,
        val trainingMl: Double,
        val proteinMl: Double,
        val sessions: List<Session>,
        /** Sueur estimée (L) et taux utilisé (L/h, musculation). */
        val sweatL: Double,
        val sweatRateLh: Double,
        val measuredRate: Boolean,
        /** Bu (saisies) et eau des aliments et boissons du journal (ml). */
        val drunkMl: Double,
        val foodWaterMl: Double,
        val proteinG: Double,
        /** Sodium et potassium du journal (mg), repère de sodium du jour (mg). */
        val sodiumMg: Double?,
        val potassiumMg: Double?,
        val sodiumTargetMg: Double,
        val sweatSodiumMg: Double,
        /** Eau fixée par le plan nutrition ce jour-là (avant la sueur des séances), sinon null. */
        val plannedMl: Double? = null,
        /** Potassium visé (plan nutrition ou réglage des nutriments), sinon null. */
        val potassiumTargetMg: Double? = null,
    ) {
        /** Rapport molaire Na:K (Na 23 g/mol, K 39,1 g/mol). */
        val naK: Double? get() = if (sodiumMg != null && potassiumMg != null && potassiumMg > 0) (sodiumMg / 23.0) / (potassiumMg / 39.1) else null
        val progress: Float get() = if (goalMl > 0) (drunkMl / goalMl).toFloat() else 0f
    }

    private val _today = MutableStateFlow<Plan?>(null)
    /** Dernier plan calculé pour aujourd'hui (pour les écrans qui lisent l'objectif sans attendre). */
    val today: StateFlow<Plan?> = _today

    /** Objectif du jour calculé, ou null (mode fixe, ou pas encore calculé). */
    fun cachedGoal(): Double? = _today.value?.takeIf { (SuperPrefs.settings.value.hydrationAuto || it.plannedMl != null) && it.date == LocalDate.now() }?.goalMl

    /** Objectif fixe : base EFSA de boissons selon le sexe, ou le réglage de la montre. */
    fun baseMl(weightKg: Double?, female: Boolean): Double {
        val efsa = if (female) 1600.0 else 2000.0
        return max(efsa, (weightKg ?: 0.0) * 35.0).coerceAtMost(4000.0)
    }

    suspend fun plan(ctx: Context, date: LocalDate = LocalDate.now()): Plan {
        val s = SuperPrefs.settings.value
        val repo = runCatching { NutriApp.repository }.getOrNull()
        val profile = repo?.prefs?.profile?.value
        val female = profile?.sex == Sex.FEMALE
        val an = repo?.let { r -> withTimeoutOrNull(1500) { r.analytics().first() } }
        val weight = an?.let { it.trendByDay[date.toEpochDay()] ?: it.currentTrendKg }
            ?: runCatching { HealthRepository.latestValue(ctx, Metric.WEIGHT, date, 60)?.second }.getOrNull()

        val from = HealthRepository.startMs(date); val to = HealthRepository.endMs(date)
        val rate = s.sweatRateLh ?: DEFAULT_SWEAT_LH
        val sessions = runCatching { Bridge.activityFacts(from, minOf(to, System.currentTimeMillis())) }.getOrDefault(emptyList())
            .filter { it.minutes >= 10 && !(it.kind == Bridge.Kind.WALK && it.minutes < 30) }
            .map { a -> Session(a.title, a.kind, a.minutes, a.minutes / 60.0 * rate * kindFactor(a.kind)) }
        val sweat = sessions.sumOf { it.sweatL }

        val entries = repo?.let { r -> withTimeoutOrNull(1500) { r.entries.rangeFlow(date.toEpochDay(), date.toEpochDay()).first() } }.orEmpty()
        val micros = Micros.sum(entries.map { it.microsJson })
        val protein = entries.sumOf { it.protein }
        val extraProtein = weight?.let { (protein - it * 1.0).coerceAtLeast(0.0) } ?: 0.0

        val base = baseMl(weight, female)
        val training = sweat * 1000 * REPLACE
        val proteinMl = (extraProtein * ML_PER_EXTRA_PROTEIN_G).coerceAtMost(800.0)
        // Plan nutrition : l'eau et le sodium prévus pour ce jour (ou sa semaine) l'emportent sur le calcul ; la sueur des
        // séances s'y ajoute sauf si la semaine l'exclut.
        val NP = app.superhealth.nutrition.domain.NutritionPlan
        val prog = repo?.prefs?.program?.value
        val e = date.toEpochDay()
        val plannedWater = prog?.let { NP.waterAt(it, e) }
        val plannedNa = prog?.let { NP.sodiumAt(it, e) }
        val addTraining = prog?.let { NP.addsTraining(it, e) } ?: true
        val goal = ((plannedWater?.let { it + if (addTraining) training else 0.0 } ?: (base + training + proteinMl)) / 50.0).roundToInt() * 50.0
        val drunk = ManualLogStore.get(ctx).query(Metric.HYDRATION, from, to).sumOf { it.value }
        val sweatNa = sweat * s.sweatSodiumMgL
        val micro = repo?.prefs?.settings?.value?.microTargets.orEmpty()
        val naTarget = (plannedNa ?: micro["sodium"] ?: BASE_SODIUM_MG) + if (addTraining) sweatNa else 0.0
        val kTarget = prog?.let { NP.potassiumAt(it, e) } ?: micro["potassium"]

        return Plan(date, weight, female, goal, base, training, proteinMl, sessions, sweat, rate, s.sweatRateLh != null, drunk,
            micros["water"] ?: 0.0, protein, micros["sodium"].takeIf { entries.isNotEmpty() }, micros["potassium"].takeIf { entries.isNotEmpty() },
            naTarget, if (addTraining) sweatNa else 0.0, plannedWater, kTarget,
        ).also { if (date == LocalDate.now()) _today.value = it }
    }

    /**
     * Taux de sudation mesuré par pesée (méthode de référence de terrain, ACSM) : (poids avant − poids après + bu pendant
     * − urine) / durée. Null si les valeurs sont incohérentes.
     */
    fun sweatRate(beforeKg: Double, afterKg: Double, drankMl: Double, minutes: Int): Double? {
        if (minutes < 15 || beforeKg <= 0 || afterKg <= 0) return null
        val lossL = beforeKg - afterKg + drankMl / 1000.0
        val r = lossL / (minutes / 60.0)
        return r.takeIf { it in 0.1..3.5 }
    }
}
