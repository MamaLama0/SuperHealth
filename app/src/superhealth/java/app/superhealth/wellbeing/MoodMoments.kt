package app.superhealth.wellbeing

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Random
import kotlin.math.pow

/**
 * Moment du moral appris pour chaque personne : à quelle heure répond-elle vraiment, et bien ?
 *
 * Une case par heure, de midi à 22 h. Chaque case garde une loi Bêta (réussites, échecs) :
 * - bandeau montré puis moral noté dans les 30 min → 1 ; plus tard dans la journée → 0,5 ; une note écrite en plus → +0,25 ;
 * - bandeau replié (« pas aujourd'hui ») ou ignoré jusqu'au soir → 0 ;
 * - moral noté de soi-même, sans bandeau → 1 à l'heure de la saisie (le meilleur signe d'un bon moment).
 * Les jours anciens pèsent moins (×0,95 par jour, 6 semaines) et la semaine et le week-end se séparent dès qu'ils ont assez
 * d'observations. Au départ, un léger a priori sur 17–19 h reprend l'ancien comportement.
 *
 * Chaque jour, un tirage de Thompson (stable pour la journée) choisit l'heure du soir : la meilleure case le plus souvent,
 * une autre de temps en temps pour continuer d'apprendre. Contre le surapprentissage, trois garde-fous gardent de la
 * variance : un jour sur sept environ, l'heure est tirée au hasard entre midi et 22 h ; chaque case plafonne sa confiance
 * ([CAP] observations pondérées), si bien qu'une habitude qui change se réapprend en quelques jours ; et l'heure retenue
 * bouge de ± 30 min. Les événements (activité, pas, méditation, dîner) ne déclenchent que dans une case au moins moitié
 * aussi bonne que la meilleure.
 */
object MoodMoments {
    const val FIRST = 12
    const val LAST = 21
    private const val DECAY = 0.95
    private const val HORIZON = 42
    private const val QUICK_MS = 30 * 60_000L
    /** Observations pondérées à partir desquelles semaine et week-end s'apprennent à part. */
    private const val SPLIT = 6.0
    /** Confiance maximale d'une case (a + b) : au-delà, les observations sont ramenées à cette masse. */
    const val CAP = 12.0
    /** Part des jours où l'heure est tirée au hasard, pour continuer d'explorer. */
    const val EXPLORE = 0.15

    @Serializable data class Shown(val day: Long, val at: Long, val trigger: String = "")

    /** Une observation : case horaire, récompense dans [0, 1], jour. */
    data class Obs(val day: Long, val hour: Int, val reward: Double)

    class Model(private val a: DoubleArray, private val b: DoubleArray, val weight: Double) {
        fun mean(hour: Int): Double = slot(hour)?.let { a[it] / (a[it] + b[it]) } ?: 0.0
        fun best(): Int = (FIRST..LAST).maxBy { mean(it) }
        /** Case assez bonne pour qu'un événement y déclenche le moral. */
        fun welcome(hour: Int): Boolean = slot(hour) != null && mean(hour) >= 0.5 * mean(best())
        fun sample(rng: Random): Int = (FIRST..LAST).maxBy { h -> slot(h)!!.let { beta(a[it], b[it], rng) } }
        private fun slot(hour: Int) = (hour - FIRST).takeIf { it in 0..(LAST - FIRST) }
    }

    private fun weekend(day: Long) = LocalDate.ofEpochDay(day).dayOfWeek.value >= 6

    /** Récompenses tirées des bandeaux montrés, du journal et des refus. */
    fun observations(shown: List<Shown>, journal: Map<Long, Journal.Entry>, skips: Collection<Long>, zone: ZoneId): List<Obs> {
        fun hour(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).hour
        val prompted = shown.groupBy { it.day }.mapValues { e -> e.value.minBy { it.at } }
        val fromPrompts = prompted.values.map { s ->
            val e = journal[s.day]
            val r = when {
                s.day in skips && (e == null || e.at <= s.at) -> 0.0
                e == null || e.at <= 0 -> 0.0
                e.at < s.at -> return@map null
                else -> (if (e.at - s.at <= QUICK_MS) 1.0 else 0.5) + (if (e.note.isNotBlank()) 0.25 else 0.0)
            }
            Obs(s.day, hour(s.at), r.coerceAtMost(1.0))
        }.filterNotNull()
        // saisi avant tout bandeau, ou sans bandeau ce jour-là : moment choisi par la personne
        val spontaneous = journal.mapNotNull { (d, e) ->
            if (e.at <= 0) return@mapNotNull null
            val p = prompted[d]
            if (p != null && e.at >= p.at) null
            else Instant.ofEpochMilli(e.at).atZone(zone).takeIf { it.toLocalDate().toEpochDay() == d }?.let { Obs(d, it.hour, 1.0) }
        }
        return fromPrompts + spontaneous
    }

    fun model(obs: List<Obs>, today: Long): Model {
        val recent = obs.filter { it.day in today - HORIZON until today + 1 }
        val same = recent.filter { weekend(it.day) == weekend(today) }
        fun w(o: Obs) = DECAY.pow((today - o.day).coerceAtLeast(0).toDouble())
        val used = if (same.sumOf(::w) >= SPLIT) same else recent
        val n = LAST - FIRST + 1
        // a priori : Bêta(1, 1) partout, un peu plus de confiance dans 17–19 h (ancien tirage du soir)
        val a = DoubleArray(n) { if (FIRST + it in 17..19) 1.6 else 1.0 }
        val b = DoubleArray(n) { 1.0 }
        used.forEach { o ->
            // une saisie à 11 h ou 23 h compte pour la case la plus proche
            val i = (o.hour.coerceIn(FIRST, LAST)) - FIRST
            a[i] += w(o) * o.reward; b[i] += w(o) * (1 - o.reward)
        }
        // plafond de confiance : la moyenne reste, la certitude non
        for (i in 0 until n) (a[i] + b[i]).takeIf { it > CAP }?.let { t -> a[i] *= CAP / t; b[i] *= CAP / t }
        return Model(a, b, used.sumOf(::w))
    }

    /**
     * Minute du jour [day], stable pour la journée : case tirée (Thompson, ou au hasard un jour sur sept environ), puis
     * ± 30 min autour du milieu de l'heure, jamais avant midi ni après 22 h 30.
     */
    fun dailyMinute(model: Model, day: Long, seed: Long): Int {
        val rng = Random(seed xor day xor 0x4D4F4F44)
        val hour = if (rng.nextDouble() < EXPLORE) FIRST + rng.nextInt(LAST - FIRST + 1) else model.sample(rng)
        return (hour * 60 + 30 + rng.nextInt(61) - 30).coerceIn(FIRST * 60, LAST * 60 + 90)
    }

    // ---- Tirage d'une loi Bêta (Marsaglia–Tsang pour les lois Gamma) ----

    internal fun beta(a: Double, b: Double, rng: Random): Double {
        val x = gamma(a, rng); val y = gamma(b, rng)
        return if (x + y <= 0) 0.5 else x / (x + y)
    }

    private fun gamma(k: Double, rng: Random): Double {
        if (k < 1) return gamma(k + 1, rng) * rng.nextDouble().pow(1 / k)
        val d = k - 1.0 / 3; val c = 1 / Math.sqrt(9 * d)
        while (true) {
            var x: Double; var v: Double
            do { x = rng.nextGaussian(); v = 1 + c * x } while (v <= 0)
            v = v * v * v
            val u = rng.nextDouble()
            if (u < 1 - 0.0331 * x * x * x * x || Math.log(u) < 0.5 * x * x + d * (1 - v + Math.log(v))) return d * v
        }
    }

    // ---- Stockage ----

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private var file: File? = null
    @Volatile var shown: List<Shown> = emptyList(); private set

    fun init(ctx: Context) {
        val f = File(File(ctx.filesDir, "wellbeing").apply { mkdirs() }, "mood_moments.json").also { file = it }
        shown = runCatching { json.decodeFromString(ListSerializer(Shown.serializer()), f.readText()) }.getOrDefault(emptyList())
    }

    /** Bandeau du moral montré (une fois par jour, au premier affichage). */
    fun record(ctx: Context, day: Long, at: Long, trigger: String) {
        if (file == null) init(ctx)
        if (shown.any { it.day == day }) return
        shown = (shown + Shown(day, at, trigger)).takeLast(120)
        runCatching { file?.writeText(json.encodeToString(ListSerializer(Shown.serializer()), shown)) }
    }

    /** Modèle du jour à partir des données enregistrées. */
    fun current(skips: Collection<Long>, today: Long = LocalDate.now().toEpochDay(), zone: ZoneId = ZoneId.systemDefault()): Model =
        model(observations(shown, Journal.days.value, skips, zone), today)

    /** Heure conseillée en clair, pour l'écran des rappels : null tant que rien n'est appris. */
    fun learnedHour(skips: Collection<Long>): Int? = current(skips).takeIf { it.weight >= 3 }?.best()
}
