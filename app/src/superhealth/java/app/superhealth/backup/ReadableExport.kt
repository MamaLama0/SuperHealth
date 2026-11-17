package app.superhealth.backup

import android.content.Context
import app.superhealth.health.data.HealthRepository
import app.superhealth.health.data.Metric
import app.superhealth.nutrition.NutriApp
import app.superhealth.nutrition.data.Csv
import app.superhealth.training.WorkoutApp
import app.superhealth.training.domain.WorkoutCsv
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/** CSV ajoutés à l'export lisible : à ouvrir dans un tableur sans outil SuperHealth. */
internal object ReadableExport {
    /** Valeurs quotidiennes de la montre et des saisies, sur trois ans au plus. */
    private val DAILY = listOf(Metric.STEPS, Metric.DISTANCE, Metric.CALORIES, Metric.ACTIVE_MINUTES, Metric.RESTING_HR, Metric.HRV,
        Metric.SLEEP, Metric.SLEEP_SCORE, Metric.STRESS, Metric.WEIGHT, Metric.BODY_FAT)

    private fun d(v: Double?) = v?.let { String.format(Locale.US, "%.2f", it) } ?: ""
    private fun q(s: String) = "\"" + s.replace("\"", "\"\"") + "\""

    suspend fun csvFiles(ctx: Context, logs: List<Triple<String, Long, Double>>): List<Pair<String, String>> = buildList {
        runCatching { WorkoutCsv.export(WorkoutApp.store.state.value.history, WorkoutApp::exerciseName) }.getOrNull()
            ?.let { add("musculation-series.csv" to it) }
        runCatching {
            val repo = NutriApp.repository
            add("nutrition-journal.csv" to Csv.foodLog(repo.entries.all()))
            add("nutrition-pesees.csv" to buildString {
                appendLine("date,poids_kg,masse_grasse_pct")
                repo.weighIns.all().sortedBy { it.epochDay }.forEach {
                    appendLine("${LocalDate.ofEpochDay(it.epochDay)},${d(it.weightKg)},${d(it.bodyFatPct)}")
                }
            })
            add("nutrition-mesures.csv" to buildString {
                appendLine("date,mesure,valeur_cm")
                repo.body.metrics().sortedBy { it.epochDay }.forEach { appendLine("${LocalDate.ofEpochDay(it.epochDay)},${it.metric},${d(it.value)}") }
            })
        }
        add("sante-saisies.csv" to buildString {
            val zone = ZoneId.systemDefault()
            appendLine("date_heure,mesure,valeur")
            logs.sortedBy { it.second }.forEach { (m, ts, v) -> appendLine("${Instant.ofEpochMilli(ts).atZone(zone).toLocalDateTime()},${q(m)},${d(v)}") }
        })
        runCatching {
            val to = LocalDate.now()
            val from = to.minusYears(3)
            val series = DAILY.mapNotNull { m ->
                runCatching { m to HealthRepository.series(ctx, m, from, to).toMap() }.getOrNull()?.takeIf { (_, s) -> s.values.any { it != null } }
            }
            if (series.isNotEmpty()) add("sante-quotidien.csv" to buildString {
                appendLine((listOf("date") + series.map { (m, _) -> m.name.lowercase() + (m.unit?.let { "_" + it.replace(Regex("[^A-Za-z%]+"), "") } ?: "") }).joinToString(","))
                generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.forEach { day ->
                    val row = series.map { (_, s) -> s[day] }
                    if (row.any { it != null }) appendLine((listOf(day.toString()) + row.map { d(it) }).joinToString(","))
                }
            })
        }
    }
}
