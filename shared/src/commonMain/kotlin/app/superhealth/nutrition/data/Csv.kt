package app.superhealth.nutrition.data

import app.superhealth.platform.*

import app.superhealth.platform.LocalDate
import app.superhealth.platform.Locale

/** Exports CSV (séparateur virgule, décimales point) et import de pesées. */
object Csv {
    private fun d(v: Double?) = v?.let { String.format(Locale.US, "%.2f", it) } ?: ""
    private fun q(s: String) = "\"" + s.replace("\"", "\"\"") + "\""
    private val meals = listOf("Petit-déjeuner", "Déjeuner", "Dîner", "Collation")

    fun foodLog(entries: List<FoodEntryEntity>) = buildString {
        appendLine("date,repas,aliment,grammes,kcal,proteines_g,lipides_g,glucides_g,fibres_g")
        entries.forEach {
            appendLine(listOf(LocalDate.ofEpochDay(it.epochDay).toString(), meals.getOrElse(it.meal) { "?" }, q(it.name),
                d(it.grams), d(it.kcal), d(it.protein), d(it.fat), d(it.carbs), d(it.fiber)).joinToString(","))
        }
    }

    fun weights(a: Analytics) = buildString {
        appendLine("date,poids_kg,tendance_kg,masse_grasse_pct")
        a.days.filter { a.trendByDay.containsKey(it.epochDay) }.forEach {
            appendLine(listOf(LocalDate.ofEpochDay(it.epochDay).toString(), d(it.weightKg), d(a.trendByDay[it.epochDay]), d(it.bodyFatPct)).joinToString(","))
        }
    }

    fun expenditure(a: Analytics) = buildString {
        appendLine("date,depense_kcal,statut,apport_kcal,cible_kcal")
        a.expenditure.forEach { p ->
            val day = a.days.firstOrNull { it.epochDay == p.epochDay }
            appendLine(listOf(LocalDate.ofEpochDay(p.epochDay).toString(), d(p.tdee), p.status.name, d(day?.intakeKcal),
                d(a.targetFor(p.epochDay).macros.kcal)).joinToString(","))
        }
    }

    /**
     * Import de pesées : lignes « date,poids[,masse grasse] » ; date ISO (2026-10-17) ou JJ/MM/AAAA ;
     * séparateur , ou ; ; décimale point ou virgule (si séparateur ;). Les en-têtes sont ignorés.
     */
    fun parseWeights(text: String): List<WeighInEntity> = text.lineSequence().mapNotNull { line ->
        val sep = if (line.contains(';')) ';' else ','
        val cols = line.split(sep).map { it.trim().trim('"') }
        if (cols.size < 2) return@mapNotNull null
        val date = parseDate(cols[0]) ?: return@mapNotNull null
        val w = cols[1].replace(',', '.').toDoubleOrNull()?.takeIf { it in 20.0..400.0 } ?: return@mapNotNull null
        val bf = cols.getOrNull(2)?.replace(',', '.')?.toDoubleOrNull()?.takeIf { it in 2.0..70.0 }
        WeighInEntity(date.toEpochDay(), w, bf)
    }.toList()

    private fun parseDate(s: String): LocalDate? = runCatching { LocalDate.parse(s.take(10)) }.getOrNull()
        ?: runCatching {
            val p = s.split('/', '.', '-').map { it.toInt() }
            LocalDate.of(p[2], p[1], p[0])
        }.getOrNull()
}
