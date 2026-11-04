package app.superhealth.league

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import app.superhealth.Feature
import app.superhealth.SuperPrefs
import app.superhealth.bridge.Bridge
import app.superhealth.has
import app.superhealth.health.data.Metric
import app.superhealth.training.WorkoutApp
import app.superhealth.training.domain.Analytics
import app.superhealth.training.domain.ExerciseDb
import app.superhealth.training.domain.Workout
import app.superhealth.ui.tr
import app.superhealth.ui.trf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * Bilan de la semaine (lundi–dimanche) : séances, charges, progrès de force, cardio, pas, sommeil, poids, XP et quêtes.
 * Proposé le dimanche soir et le lundi matin ; l'image ([render]) se partage au Groupe comme une photo.
 */
object WeeklyRecap {
    data class Lift(val exerciseId: String, val name: String, val best: Double, val change: Double?, val sessions: List<Double>)

    /** Sport de la semaine hors musculation : nombre de sorties, distance et durée. */
    data class Sport(val kind: Bridge.Kind, val count: Int, val km: Double, val minutes: Long) {
        val label get() = when (kind) {
            Bridge.Kind.RUN -> "Course"; Bridge.Kind.WALK -> "Marche"; Bridge.Kind.HIKE -> "Randonnée"; Bridge.Kind.BIKE -> "Vélo"
            Bridge.Kind.SWIM -> "Natation"; Bridge.Kind.YOGA -> "Yoga"; else -> "Autres activités"
        }
    }

    /** Chiffre clé affiché en tête du bilan et sur l'image. */
    data class Stat(val value: String, val label: String)

    data class Data(
        val weekStart: Long,
        val workouts: Int, val volumeKg: Double, val volumes: List<Double>,
        val heaviest: Pair<String, Double>?, val lifts: List<Lift>,
        val sports: List<Sport>, val mindMinutes: Int, val steps: Double?, val sleepMin: Double?,
        val weightChange: Double?, val xpDays: List<Int>, val weekXp: Int, val index: Int?,
        val questsDone: Int, val quests: Int, val mirror: Boolean,
    ) {
        val runKm get() = sports.firstOrNull { it.kind == Bridge.Kind.RUN }?.km ?: 0.0
        /** Aucune activité suivie dans la semaine (musculation, sports, méditation, étirements) : pas de bilan. */
        val empty get() = workouts == 0 && sports.isEmpty() && mindMinutes == 0

        /** Trois chiffres selon ce qui a été pratiqué : musculation, puis sports par durée, puis esprit, XP en dernier. */
        fun headline(): List<Stat> {
            val locale = app.superhealth.ui.Lang.locale
            return buildList {
                if (workouts > 0) { add(Stat("$workouts", tr("séances"))); add(Stat("%,d".format(locale, volumeKg.roundToInt()), tr("kg soulevés"))) }
                sports.sortedByDescending { it.minutes }.forEach { s ->
                    add(if (s.km >= 0.5) Stat("%.1f".format(locale, s.km), trf("km · %1\$s", tr(s.label).lowercase(locale)))
                        else Stat(minutes(s.minutes), tr(s.label).lowercase(locale)))
                }
                if (mindMinutes > 0) add(Stat("$mindMinutes", tr("min d'esprit et mobilité")))
                add(Stat("$weekXp", tr("XP")))
            }.take(3)
        }
    }

    fun minutes(m: Long) = if (m >= 60) "%d h %02d".format(m / 60, m % 60) else "$m min"

    /** Regroupe les activités par sport, la plus longue d'abord. */
    internal fun sports(acts: List<Bridge.Activity>): List<Sport> = acts.filter { it.kind != Bridge.Kind.STRENGTH }.groupBy { it.kind }
        .map { (kind, list) -> Sport(kind, list.size, list.sumOf { it.distanceM?.takeIf { d -> d.isFinite() && d > 0 } ?: 0.0 } / 1000, list.sumOf { it.minutes.coerceAtLeast(0) }) }
        .sortedByDescending { it.minutes }

    /** Lundi de la semaine à résumer : la semaine en cours le dimanche, la précédente le lundi et après. */
    fun weekFor(today: LocalDate): Long {
        val monday = Progression.week(today.toEpochDay())
        return if (today.dayOfWeek == java.time.DayOfWeek.SUNDAY) monday else monday - 7
    }

    /** Le bilan s'affiche du dimanche 17 h au lundi midi, tant qu'il n'a pas été ouvert. */
    fun due(now: java.time.LocalDateTime, seenWeek: Long): Boolean {
        val week = weekFor(now.toLocalDate())
        if (seenWeek >= week) return false
        return (now.dayOfWeek == java.time.DayOfWeek.SUNDAY && now.hour >= 17) || (now.dayOfWeek == java.time.DayOfWeek.MONDAY && now.hour < 12)
    }

    private fun Workout.day() = Analytics.day(startTime).toEpochDay()

    /** Progrès de force : meilleur 1RM estimé de la semaine face aux quatre semaines d'avant, trois plus grands écarts. */
    internal fun lifts(history: List<Workout>, db: ExerciseDb, weekStart: Long): List<Lift> {
        val week = history.filter { it.day() in weekStart..weekStart + 6 }
        val before = history.filter { it.day() in weekStart - 28 until weekStart }
        return week.flatMap { w -> w.exercises.map { it.exerciseId } }.distinct().mapNotNull { id ->
            val e = db.exercise(id)?.takeIf { it.usesWeight && it.usesReps && "aw" !in it.metrics } ?: return@mapNotNull null
            fun best(ws: List<Workout>) = ws.flatMap { w -> w.exercises.filter { it.exerciseId == id } }
                .mapNotNull { Analytics.bestPerformance(e, Analytics.workingSets(it)) }.maxOrNull()
            val now = best(week) ?: return@mapNotNull null
            val old = best(before)
            val sessions = history.filter { it.day() <= weekStart + 6 }.sortedBy { it.startTime }
                .mapNotNull { w -> w.exercises.filter { it.exerciseId == id }.mapNotNull { Analytics.bestPerformance(e, Analytics.workingSets(it)) }.maxOrNull() }
                .takeLast(8)
            Lift(id, e.name, now, old?.let { now / it - 1 }, sessions)
        }.sortedWith(compareByDescending<Lift> { it.change ?: -1.0 }.thenByDescending { it.best }).take(3)
    }

    suspend fun compute(ctx: Context, today: LocalDate = LocalDate.now()): Data = withContext(Dispatchers.IO) {
        val weekStart = weekFor(today)
        val sh = SuperPrefs.settings.value
        val zone = ZoneId.systemDefault()
        val history = WorkoutApp.store.state.value.history.distinctBy { it.id }
        val db = WorkoutApp.db.first { it != null }!!
        val week = history.filter { it.day() in weekStart..weekStart + 6 && it.exercises.any { e -> Analytics.workingSets(e).isNotEmpty() } }
        fun volume(from: Long) = history.filter { it.day() in from..from + 6 }.sumOf { w -> w.exercises.sumOf { Analytics.volume(it.sets) } }
        val heaviest = week.flatMap { w -> w.exercises.mapNotNull { e ->
            val kg = Analytics.workingSets(e).mapNotNull { it.weight }.maxOrNull() ?: return@mapNotNull null
            db.exercise(e.exerciseId)?.takeIf { it.usesWeight && "aw" !in it.metrics }?.let { it.name to kg }
        } }.maxByOrNull { it.second }
        val from = LocalDate.ofEpochDay(weekStart).atStartOfDay(zone).toInstant().toEpochMilli()
        val to = LocalDate.ofEpochDay(weekStart + 7).atStartOfDay(zone).toInstant().toEpochMilli()
        val acts = if (sh.has(Feature.HEALTH)) runCatching { Bridge.activityFacts(from, to - 1) }.getOrDefault(emptyList()) else emptyList()
        val days = (0L..6L).map { LocalDate.ofEpochDay(weekStart + it) }.filter { !it.isAfter(today) }
        suspend fun mean(metric: Metric) = if (!sh.has(Feature.HEALTH)) null else
            days.mapNotNull { runCatching { Bridge.healthValue(ctx, metric, it) }.getOrNull()?.takeIf { v -> v > 0 } }.takeIf { it.size >= 3 }?.average()
        val weights = if (sh.has(Feature.NUTRITION)) app.superhealth.nutrition.NutriApp.repository.weighIns.all()
            .filter { it.epochDay in weekStart - 3..weekStart + 6 }.sortedBy { it.epochDay } else emptyList()
        val progress = runCatching { ProgressRepository.compute(ctx) }.getOrNull()
        val xpDays = (0..6).map { d -> progress?.daily?.get(weekStart + d) ?: 0 }
        val quests = League.state.value.questWeeks.firstOrNull { it.start == weekStart }
        Data(
            weekStart, week.size, volume(weekStart), (5 downTo 0).map { volume(weekStart - 7L * it) }, heaviest, lifts(history, db, weekStart),
            sports(acts),
            app.superhealth.meditation.Meditation.minutes(weekStart, weekStart + 6) + app.superhealth.stretch.Stretch.minutes(weekStart, weekStart + 6),
            mean(Metric.STEPS), mean(Metric.SLEEP),
            if (weights.size >= 2) weights.last().weightKg - weights.first().weightKg else null,
            xpDays, xpDays.sum(), League.mine.value?.takeIf { it.ready }?.total,
            quests?.quests?.count { it.complete } ?: 0, quests?.quests?.size ?: 0, quests?.mirror?.answered == true,
        )
    }

    /** Texte d'accompagnement de la publication. */
    fun summary(d: Data): String = buildList {
        if (d.workouts > 0) add(trf("%1\$s séance(s), %2\$s kg soulevés", d.workouts, "%,d".format(app.superhealth.ui.Lang.locale, d.volumeKg.roundToInt())))
        d.lifts.firstOrNull { (it.change ?: 0.0) > 0.005 }?.let { add(trf("%1\$s : +%2\$s %%", tr(it.name), "%.1f".format(app.superhealth.ui.Lang.locale, it.change!! * 100))) }
        d.sports.firstOrNull()?.let { s -> add(if (s.km >= 0.5) trf("%1\$s km de %2\$s", "%.1f".format(app.superhealth.ui.Lang.locale, s.km), tr(s.label).lowercase(app.superhealth.ui.Lang.locale))
            else trf("%1\$s de %2\$s", minutes(s.minutes), tr(s.label).lowercase(app.superhealth.ui.Lang.locale))) }
        if (d.mindMinutes > 0) add(trf("%1\$s min d'esprit et mobilité", d.mindMinutes))
        if (d.questsDone > 0) add(trf("%1\$s quête(s) bouclée(s)", d.questsDone))
    }.joinToString(" · ").ifBlank { tr("Une semaine de plus dans les jambes.") }

    /** Image 1080 × 1350 du bilan : chiffres clés, volume des six semaines, progrès de force et XP par jour. */
    fun render(ctx: Context, d: Data, accent: Int): File {
        val w = 1080; val h = 1350
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val bg = Paint().apply { shader = LinearGradient(0f, 0f, 0f, h.toFloat(), 0xFF15171C.toInt(), 0xFF0C0D10.toInt(), Shader.TileMode.CLAMP) }
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bg)
        fun text(size: Float, color: Int, bold: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size; this.color = color; typeface = Typeface.create(Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
        }
        val white = 0xFFF2F2F2.toInt(); val grey = 0xFF9AA0A8.toInt()
        val fmt = java.time.format.DateTimeFormatter.ofPattern("d MMM", app.superhealth.ui.Lang.locale)
        val start = LocalDate.ofEpochDay(d.weekStart)
        c.drawText(tr("Ma semaine"), 72f, 140f, text(76f, white, true))
        c.drawText(start.format(fmt) + " – " + start.plusDays(6).format(fmt), 72f, 200f, text(36f, grey))
        val stats = d.headline().map { it.value to it.label }
        stats.forEachIndexed { i, (v, label) ->
            val x = 72f + i * 320f
            c.drawText(v, x, 330f, text(72f, accent, true))
            c.drawText(label, x, 378f, text(32f, grey))
        }
        val bar = Paint(Paint.ANTI_ALIAS_FLAG)
        var y = 470f
        if (d.volumes.any { it > 0 }) {
            // volume par semaine
            c.drawText(tr("Volume des six dernières semaines"), 72f, y, text(34f, white, true))
            val maxV = d.volumes.maxOrNull()?.takeIf { it > 0 } ?: 1.0
            d.volumes.forEachIndexed { i, v ->
                val x = 72f + i * 156f; val top = y + 230f - (200f * (v / maxV)).toFloat()
                bar.color = if (i == d.volumes.lastIndex) accent else 0xFF3A3F48.toInt()
                c.drawRoundRect(RectF(x, top, x + 120f, y + 230f), 18f, 18f, bar)
            }
            y += 310f
        }
        // activités : barre proportionnelle à la durée
        if (d.sports.isNotEmpty() || d.mindMinutes > 0) {
            val rows = d.sports.map { tr(it.label) to it.minutes } + (if (d.mindMinutes > 0) listOf(tr("Esprit et mobilité") to d.mindMinutes.toLong()) else emptyList())
            val max = rows.maxOf { it.second }.coerceAtLeast(1)
            c.drawText(tr("Activités"), 72f, y, text(34f, white, true))
            rows.take(if (d.lifts.isEmpty()) 5 else 2).forEach { (label, m) ->
                y += 64f
                c.drawText(label, 72f, y, text(32f, white))
                bar.color = 0xFF2A2E35.toInt(); c.drawRoundRect(RectF(420f, y - 26f, 860f, y), 12f, 12f, bar)
                bar.color = accent; c.drawRoundRect(RectF(420f, y - 26f, 420f + 440f * m / max, y), 12f, 12f, bar)
                c.drawText(minutes(m), 880f, y, text(30f, grey))
            }
            y += 70f
        }
        // progrès de force avec mini-courbes
        if (d.lifts.isNotEmpty()) c.drawText(tr("Progrès de force"), 72f, y, text(34f, white, true))
        d.lifts.take(if (d.sports.isEmpty()) 3 else 2).forEach { lift ->
            y += 80f
            c.drawText(tr(lift.name).take(26), 72f, y, text(36f, white))
            val change = lift.change
            val label = if (change == null) "%.0f kg".format(lift.best) else (if (change >= 0) "+" else "") + "%.1f %%".format(app.superhealth.ui.Lang.locale, change * 100)
            c.drawText(label, 640f, y, text(36f, if ((change ?: 0.0) >= 0) accent else grey, true))
            if (lift.sessions.size >= 2) {
                val lo = lift.sessions.min(); val hi = lift.sessions.max().coerceAtLeast(lo + 1e-6)
                val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent; strokeWidth = 6f; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
                val path = android.graphics.Path()
                lift.sessions.forEachIndexed { i, v ->
                    val px = 860f + i * (150f / (lift.sessions.size - 1)); val py = y - 6f - ((v - lo) / (hi - lo) * 44f).toFloat()
                    if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                c.drawPath(path, line)
            }
        }
        // XP par jour
        val maxXp = d.xpDays.maxOrNull()?.takeIf { it > 0 } ?: 1
        val days = listOf("L", "M", "M", "J", "V", "S", "D").let { if (app.superhealth.ui.Lang.english) listOf("M", "T", "W", "T", "F", "S", "S") else it }
        c.drawText(trf("%1\$s XP cette semaine", d.weekXp), 72f, 1170f, text(34f, white, true))
        d.xpDays.forEachIndexed { i, xp ->
            val x = 72f + i * 134f; val top = 1290f - 80f * xp / maxXp
            bar.color = if (xp > 0) accent else 0xFF2A2E35.toInt()
            c.drawRoundRect(RectF(x, top.coerceAtMost(1282f), x + 96f, 1290f), 12f, 12f, bar)
            c.drawText(days[i], x + 36f, 1330f, text(26f, grey))
        }
        c.drawText("SuperHealth", w - 300f, 140f, text(34f, grey, true))
        // cache/social : dossier déjà exposé par le FileProvider de l'app, pour l'aperçu et l'envoi comme une photo choisie
        val file = File(File(ctx.cacheDir, "social").apply { mkdirs() }, "semaine-${d.weekStart}.jpg")
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bmp.recycle()
        return file
    }
}
