package app.superhealth.wellbeing

import android.content.Context
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow

/**
 * Moteur d'attention (plan 3.5, moteur 1) : quand solliciter, et combien de fois.
 *
 * - [preferredMinute] : heure habituelle d'une action, densité à noyau sur le cercle de 24 h (von Mises, κ = 12, ±40 min
 *   environ), jours récents plus lourds (0,9 par jour), semaine et week-end séparés quand chacun a assez d'exemples.
 * - [quiet] : rien de sonore entre le coucher conseillé et le réveil habituel ([Bedtime]), 22 h 30–7 h par défaut.
 * - [alert] : au plus trois notifications sonores par jour hors messages d'amis, espacées de 90 min ; au-delà, elles
 *   arrivent sans son dans le volet.
 */
object Attention {
    private const val KAPPA = 12.0
    private const val DECAY = 0.9
    const val MIN_EXAMPLES = 7

    data class Example(val epochDay: Long, val minute: Int)

    private fun weekend(day: Long) = LocalDate.ofEpochDay(day).dayOfWeek.value >= 6

    /** Densité relative à [minute] (non normalisée). */
    internal fun density(examples: List<Example>, minute: Int, today: Long): Double = examples.sumOf { e ->
        val angle = 2 * PI * (minute - e.minute) / 1440.0
        DECAY.pow((today - e.epochDay).coerceAtLeast(0).toDouble()) * exp(KAPPA * (cos(angle) - 1))
    }

    /**
     * Minute la plus probable dans [window] pour [today], ou null sous [MIN_EXAMPLES] exemples utiles. Les exemples du même
     * type de jour sont préférés s'ils sont assez nombreux.
     */
    fun preferredMinute(examples: List<Example>, window: IntRange, today: Long, horizon: Int = 28): Int? {
        val recent = examples.filter { it.epochDay in today - horizon until today && it.minute in window }
        val same = recent.filter { weekend(it.epochDay) == weekend(today) }
        val used = if (same.size >= MIN_EXAMPLES) same else recent
        if (used.size < MIN_EXAMPLES) return null
        return window.step(5).maxByOrNull { density(used, it, today) }
    }

    // ----- Heures calmes -----

    @Volatile private var quietWindow: Pair<Int, Int> = 22 * 60 + 30 to 7 * 60

    /** Met à jour les heures calmes depuis le plan de sommeil (appelé avant une synchronisation). */
    suspend fun refresh(ctx: Context) {
        runCatching { Bedtime.tonight(ctx) }.getOrNull()?.let { plan ->
            if (plan.nights >= MIN_EXAMPLES) quietWindow = Math.floorMod(plan.bedMinute - 30, 1440) to plan.wakeMinute
        }
    }

    fun quiet(now: Long, zone: ZoneId = ZoneId.systemDefault(), window: Pair<Int, Int> = quietWindow): Boolean {
        val t = Instant.ofEpochMilli(now).atZone(zone)
        val m = t.hour * 60 + t.minute
        val (from, to) = window
        return if (from <= to) m in from until to else m >= from || m < to
    }

    // ----- Budget de notifications -----

    private const val PREFS = "attention"
    private const val DAILY = 3
    private const val SPACING_MS = 90 * 60_000L

    /** Décision pure : [sent] = heures des alertes sonores du jour, hors messages d'amis. */
    internal fun allowed(now: Long, social: Boolean, sent: List<Long>, quiet: Boolean): Boolean = when {
        quiet -> false
        social -> true
        sent.size >= DAILY -> false
        else -> sent.none { now - it < SPACING_MS }
    }

    /** Vrai si la notification peut sonner ; enregistre l'alerte. [social] : message ou réaction d'un ami. */
    @Synchronized fun alert(ctx: Context, social: Boolean, now: Long = System.currentTimeMillis()): Boolean {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val day = LocalDate.now().toEpochDay()
        val sent = if (prefs.getLong("day", -1) == day) prefs.getString("sent", "").orEmpty().split(',').mapNotNull { it.toLongOrNull() } else emptyList()
        val ok = allowed(now, social, sent, quiet(now))
        if (ok && !social) prefs.edit().putLong("day", day).putString("sent", (sent + now).joinToString(",")).apply()
        return ok
    }
}
