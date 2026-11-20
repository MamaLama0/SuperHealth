package app.superhealth.training.domain

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

/**
 * Import de l'historique d'autres apps de musculation (exports CSV) : Strong, Hevy et FitNotes. Chaque exercice est rapproché
 * de la base par son nom ; un exercice introuvable devient un exercice personnel du même nom, pour ne perdre aucune série.
 * Les séances déjà présentes (même heure de début, à une minute près) sont ignorées : importer deux fois ne double rien.
 */
object WorkoutImport {
    enum class Source(val label: String) { STRONG("Strong"), HEVY("Hevy"), FITNOTES("FitNotes") }

    /** Exercice du fichier → exercice de la base (null : créé comme exercice personnel). */
    data class Match(val sourceName: String, val exercise: Exercise?, val sets: Int)

    data class Preview(
        val source: Source,
        val workouts: List<Workout>,
        val matches: List<Match>,
        val newExercises: List<Exercise>,
        val duplicates: Int,
        val skippedRows: Int,
    ) {
        val sets get() = workouts.sumOf { w -> w.exercises.sumOf { it.sets.size } }
    }

    class UnknownFormat : IllegalArgumentException("Format non reconnu : export CSV de Strong, Hevy ou FitNotes attendu")

    // ----- Lecture du CSV -----

    /** Lignes du CSV (guillemets, retours à la ligne dans un champ, séparateur , ou ; détecté sur l'en-tête). */
    fun parseCsv(text: String): List<List<String>> {
        val body = text.removePrefix("﻿")
        val firstLine = body.substringBefore('\n')
        val sep = if (firstLine.count { it == ';' } > firstLine.count { it == ',' }) ';' else ','
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        while (i < body.length) {
            val c = body[i]
            when {
                quoted && c == '"' && i + 1 < body.length && body[i + 1] == '"' -> { cell.append('"'); i++ }
                c == '"' -> quoted = !quoted
                !quoted && c == sep -> { row += cell.toString(); cell.clear() }
                !quoted && (c == '\n' || c == '\r') -> {
                    if (c == '\r' && i + 1 < body.length && body[i + 1] == '\n') i++
                    row += cell.toString(); cell.clear()
                    if (row.any { it.isNotBlank() }) rows += row
                    row = mutableListOf()
                }
                else -> cell.append(c)
            }
            i++
        }
        row += cell.toString()
        if (row.any { it.isNotBlank() }) rows += row
        return rows
    }

    private class Table(rows: List<List<String>>) {
        val header = rows.first().map { it.trim().lowercase(Locale.ROOT) }
        val data = rows.drop(1)
        fun col(vararg names: String): Int = names.firstNotNullOfOrNull { n -> header.indexOf(n).takeIf { it >= 0 } } ?: -1
        fun colStarting(prefix: String): Int = header.indexOfFirst { it.startsWith(prefix) }
    }

    private fun List<String>.at(i: Int) = if (i in indices) this[i].trim() else ""
    private fun num(s: String): Double? = s.trim().replace(',', '.').takeIf { it.isNotEmpty() }?.toDoubleOrNull()

    // ----- Lignes communes aux trois formats -----

    private data class Row(
        val key: String, val start: LocalDateTime, val workoutName: String, val durationSec: Long, val workoutNote: String,
        val exercise: String, val type: SetType, val weightKg: Double?, val reps: Int?, val seconds: Int?, val rpe: Double?, val note: String,
    )

    private const val LB = 0.45359237

    fun detect(rows: List<List<String>>): Source? {
        val h = rows.firstOrNull()?.map { it.trim().lowercase(Locale.ROOT) } ?: return null
        return when {
            "exercise_title" in h && "start_time" in h -> Source.HEVY
            "exercise name" in h && ("set order" in h || "workout name" in h) -> Source.STRONG
            "exercise" in h && "category" in h && h.any { it.startsWith("weight") } -> Source.FITNOTES
            else -> null
        }
    }

    private fun rows(source: Source, t: Table, defaultKg: Boolean): Pair<List<Row>, Int> {
        var skipped = 0
        val out = mutableListOf<Row>()
        when (source) {
            Source.STRONG -> {
                val date = t.col("date"); val name = t.col("workout name"); val ex = t.col("exercise name"); val order = t.col("set order")
                val wKg = t.col("weight (kg)", "weight (kgs)"); val wLb = t.col("weight (lbs)", "weight (lb)"); val w = t.col("weight")
                val reps = t.col("reps"); val secs = t.col("seconds"); val rpe = t.col("rpe"); val note = t.col("notes"); val wNote = t.col("workout notes")
                val dur = t.col("duration"); val durSec = t.col("duration (sec)", "duration (seconds)")
                t.data.forEach { r ->
                    val start = parseDate(r.at(date)); val exercise = r.at(ex); val o = r.at(order)
                    if (start == null || exercise.isEmpty() || o.equals("Rest Timer", true)) { skipped++; return@forEach }
                    val weight = num(r.at(wKg)) ?: num(r.at(wLb))?.times(LB) ?: num(r.at(w))?.let { if (defaultKg) it else it * LB }
                    val type = when (o.uppercase(Locale.ROOT)) { "W" -> SetType.WARMUP; "D" -> SetType.DROP; "F" -> SetType.FAILURE; else -> SetType.STANDARD }
                    val duration = num(r.at(durSec))?.toLong() ?: parseDuration(r.at(dur))
                    out += Row(r.at(date) + "|" + r.at(name), start, r.at(name).ifEmpty { "Strong" }, duration, r.at(wNote), exercise, type,
                        weight, num(r.at(reps))?.roundToInt(), num(r.at(secs))?.roundToInt(), num(r.at(rpe)), r.at(note))
                }
            }
            Source.HEVY -> {
                val title = t.col("title"); val st = t.col("start_time"); val end = t.col("end_time"); val desc = t.col("description")
                val ex = t.col("exercise_title"); val exNote = t.col("exercise_notes"); val type = t.col("set_type")
                val wKg = t.col("weight_kg"); val wLb = t.col("weight_lbs"); val reps = t.col("reps"); val secs = t.col("duration_seconds"); val rpe = t.col("rpe")
                t.data.forEach { r ->
                    val start = parseDate(r.at(st)); val exercise = r.at(ex)
                    if (start == null || exercise.isEmpty()) { skipped++; return@forEach }
                    val stop = parseDate(r.at(end))
                    val setType = when (r.at(type).lowercase(Locale.ROOT)) { "warmup" -> SetType.WARMUP; "dropset" -> SetType.DROP; "failure" -> SetType.FAILURE; else -> SetType.STANDARD }
                    out += Row(r.at(st) + "|" + r.at(title), start, r.at(title).ifEmpty { "Hevy" },
                        stop?.let { java.time.Duration.between(start, it).seconds.coerceAtLeast(0) } ?: 0, r.at(desc), exercise, setType,
                        num(r.at(wKg)) ?: num(r.at(wLb))?.times(LB), num(r.at(reps))?.roundToInt(), num(r.at(secs))?.roundToInt(), num(r.at(rpe)), r.at(exNote))
                }
            }
            Source.FITNOTES -> {
                val date = t.col("date"); val ex = t.col("exercise"); val reps = t.col("reps"); val time = t.col("time"); val comment = t.col("comment")
                val wCol = t.colStarting("weight"); val lbs = t.header.getOrNull(wCol)?.contains("lb") == true
                t.data.forEach { r ->
                    val day = runCatching { LocalDate.parse(r.at(date)) }.getOrNull(); val exercise = r.at(ex)
                    if (day == null || exercise.isEmpty()) { skipped++; return@forEach }
                    val weight = num(r.at(wCol))?.let { if (lbs) it * LB else it }
                    out += Row(day.toString(), day.atTime(12, 0), "FitNotes", 0, "", exercise, SetType.STANDARD, weight,
                        num(r.at(reps))?.roundToInt(), parseClock(r.at(time)), null, r.at(comment))
                }
            }
        }
        // Lignes sans charge, ni répétitions, ni durée (cardio en distance seule) : rien à enregistrer en musculation.
        val kept = out.filter { it.weightKg != null || it.reps != null || (it.seconds ?: 0) > 0 }
        return kept to skipped + (out.size - kept.size)
    }

    private val DATE_FORMATS = listOf("yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm", "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd'T'HH:mm",
        "d MMM yyyy, HH:mm", "d MMM yyyy HH:mm", "MMM d, yyyy, HH:mm", "dd/MM/yyyy HH:mm", "yyyy/MM/dd HH:mm:ss")
        .map { DateTimeFormatter.ofPattern(it, Locale.ENGLISH) }

    internal fun parseDate(s: String): LocalDateTime? {
        val v = s.trim().removeSuffix("Z").substringBefore('.').takeIf { it.isNotEmpty() } ?: return null
        DATE_FORMATS.forEach { f -> runCatching { return LocalDateTime.parse(v, f) } }
        return runCatching { LocalDate.parse(v).atTime(12, 0) }.getOrNull()
    }

    /** « 1h 5m », « 45m », « 1h », « 90s » → secondes. */
    internal fun parseDuration(s: String): Long {
        var total = 0L
        Regex("(\\d+)\\s*([hms])").findAll(s.lowercase(Locale.ROOT)).forEach { m ->
            val n = m.groupValues[1].toLong()
            total += when (m.groupValues[2]) { "h" -> n * 3600; "m" -> n * 60; else -> n }
        }
        return total
    }

    /** « 0:01:30 », « 1:30 » ou « 90 » → secondes. */
    private fun parseClock(s: String): Int? {
        val parts = s.trim().split(':').mapNotNull { it.toIntOrNull() }
        if (parts.isEmpty()) return null
        return parts.fold(0) { acc, p -> acc * 60 + p }.takeIf { it > 0 }
    }

    // ----- Rapprochement des noms -----

    private val STOP = setOf("flat", "standard", "the", "a", "with", "of", "exercise", "regular", "normal", "bar")
    private val SYNONYMS = mapOf(
        "db" to "dumbbell", "dumbbells" to "dumbbell", "bb" to "barbell", "kb" to "kettlebell", "ohp" to "overhead press",
        "military" to "overhead", "pullup" to "pull up", "pullups" to "pull up", "chinup" to "chin up", "chinups" to "chin up",
        "pushup" to "push up", "pushups" to "push up", "situp" to "sit up", "tricep" to "triceps", "bicep" to "biceps",
        "skullcrusher" to "skull crusher", "skullcrushers" to "skull crusher", "rdl" to "romanian deadlift", "presses" to "press",
        "curls" to "curl", "raises" to "raise", "rows" to "row", "squats" to "squat", "deadlifts" to "deadlift", "extensions" to "extension",
        "flyes" to "fly", "flys" to "fly", "pulldowns" to "pulldown", "lunges" to "lunge", "dips" to "dip", "shrugs" to "shrug",
        "bodyweight" to "bodyweight", "body" to "body", "pull-down" to "pulldown",
    )

    /** « Bench Press (Barbell) » → [barbell, bench, press] ; synonymes et pluriels ramenés à la forme de la base. */
    internal fun tokens(name: String): List<String> {
        val inParens = Regex("\\(([^)]*)\\)").findAll(name).map { it.groupValues[1] }.joinToString(" ")
        val base = name.replace(Regex("\\([^)]*\\)"), " ")
        return ExerciseDb.norm("$inParens $base").split(' ').filter { it.isNotEmpty() }
            .flatMap { (SYNONYMS[it] ?: it).split(' ') }.filter { it !in STOP }
    }

    /** Noms et variantes de la base, découpés une fois pour tout le fichier. */
    class Index(db: ExerciseDb) {
        internal val entries: List<Triple<Exercise, Boolean, Set<String>>> = db.exercises.flatMap { e ->
            (listOf(e.name) + e.alternatives).mapIndexed { i, n -> Triple(e, i == 0, tokens(n).toSet()) }
        }.filter { it.third.isNotEmpty() }
    }

    /** Meilleur exercice de la base pour [name] : tous ses mots (matériel compris) doivent s'y retrouver ; null sinon. */
    fun match(name: String, index: Index): Exercise? {
        val srcSet = tokens(name).toSet()
        if (srcSet.isEmpty()) return null
        var best: Exercise? = null
        var bestScore = Double.NEGATIVE_INFINITY
        index.entries.forEach { (e, main, c) ->
            if (!c.containsAll(srcSet)) return@forEach
            // Le moins de mots en trop possible, puis le nom principal, puis l'exercice le plus courant de la base.
            val score = -(c.size - srcSet.size).toDouble() + (if (main) 0.5 else 0.0) + (e.searchBoost ?: 0.0) / 1000.0
            if (score > bestScore) { bestScore = score; best = e }
        }
        return best
    }

    // ----- Construction des séances -----

    fun preview(text: String, db: ExerciseDb, existing: List<Workout>, defaultKg: Boolean = true, zone: ZoneId = ZoneId.systemDefault()): Preview {
        val raw = parseCsv(text)
        val source = detect(raw) ?: throw UnknownFormat()
        val (rows, skipped) = rows(source, Table(raw), defaultKg)
        val names = rows.map { it.exercise }.distinct()
        val index = Index(db)
        val matched = names.associateWith { match(it, index) }
        val created = names.filter { matched[it] == null }.associateWith { n ->
            val timed = rows.filter { it.exercise == n }.all { it.reps == null && (it.seconds ?: 0) > 0 }
            val bodyweight = rows.filter { it.exercise == n }.all { it.weightKg == null || it.weightKg == 0.0 }
            Exercise(id = ExerciseDb.CUSTOM_PREFIX + UUID.randomUUID(), name = n.trim(),
                metrics = when { timed -> listOf("d"); bodyweight -> listOf("r"); else -> listOf("w", "r") })
        }
        fun idOf(n: String) = matched[n]?.id ?: created.getValue(n).id

        val starts = existing.map { it.startTime }
        var duplicates = 0
        val workouts = rows.groupBy { it.key }.values.mapNotNull { group ->
            val first = group.first()
            val startMs = first.start.atZone(zone).toInstant().toEpochMilli()
            if (starts.any { kotlin.math.abs(it - startMs) < 60_000 }) { duplicates++; return@mapNotNull null }
            val exercises = mutableListOf<SessionExercise>()
            group.forEach { r ->
                val set = SetLog(UUID.randomUUID().toString(), type = r.type, weight = r.weightKg?.let { (it * 100).roundToInt() / 100.0 },
                    reps = r.reps ?: r.seconds?.takeIf { it > 0 && created[r.exercise]?.metrics == listOf("d") },
                    rir = r.rpe?.let { (10 - it).roundToInt().coerceIn(0, 10) }, done = true, completedAt = startMs)
                val last = exercises.lastOrNull()
                // Séries consécutives d'un même exercice regroupées ; un exercice repris plus loin garde son ordre.
                if (last != null && last.exerciseId == idOf(r.exercise)) {
                    exercises[exercises.lastIndex] = last.copy(sets = last.sets + set,
                        note = if (r.note.isNotBlank() && r.note !in last.note) listOf(last.note, r.note).filter { it.isNotBlank() }.joinToString(" / ") else last.note)
                } else exercises += SessionExercise(UUID.randomUUID().toString(), idOf(r.exercise), listOf(set), note = r.note)
            }
            Workout(id = UUID.randomUUID().toString(), name = first.workoutName, startTime = startMs, durationSec = first.durationSec,
                exercises = exercises, note = listOfNotNull(first.workoutNote.takeIf { it.isNotBlank() }, "Import ${source.label}").joinToString("\n"))
        }.sortedBy { it.startTime }
        val used = workouts.flatMap { w -> w.exercises.map { it.exerciseId } }.toSet()
        val counts = rows.groupingBy { it.exercise }.eachCount()
        return Preview(source, workouts, names.map { Match(it, matched[it], counts[it] ?: 0) }.sortedByDescending { it.sets },
            created.values.filter { it.id in used }, duplicates, skipped)
    }
}
