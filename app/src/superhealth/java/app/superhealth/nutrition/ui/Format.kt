package app.superhealth.nutrition.ui

import app.superhealth.nutrition.data.FoodEntryEntity
import app.superhealth.nutrition.domain.EnergyUnit
import app.superhealth.nutrition.domain.Macros
import app.superhealth.nutrition.domain.Settings
import app.superhealth.nutrition.domain.WeightUnit
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

const val KG_PER_LB = 0.45359237
const val KJ_PER_KCAL = 4.184

private val FR get() = Lang.locale
private val dayFmt get() = DateTimeFormatter.ofPattern("EEE d MMM", FR)
private val shortFmt get() = DateTimeFormatter.ofPattern("d MMM", FR)

fun Settings.weight(kg: Double?, decimals: Int = 1): String {
    if (kg == null) return "—"
    val v = if (weightUnit == WeightUnit.LB) kg / KG_PER_LB else kg
    return String.format(FR, "%.${decimals}f %s", v, weightUnit.label)
}

fun Settings.weightValue(kg: Double) = if (weightUnit == WeightUnit.LB) kg / KG_PER_LB else kg
fun Settings.toKg(v: Double) = if (weightUnit == WeightUnit.LB) v * KG_PER_LB else v

fun Settings.energy(kcal: Double?): String {
    if (kcal == null) return "—"
    return if (energyUnit == EnergyUnit.KJ) "${(kcal * KJ_PER_KCAL).roundToInt()} kJ" else "${kcal.roundToInt()} kcal"
}

fun Settings.energyValue(kcal: Double) = if (energyUnit == EnergyUnit.KJ) kcal * KJ_PER_KCAL else kcal
fun Settings.toKcal(v: Double) = if (energyUnit == EnergyUnit.KJ) v / KJ_PER_KCAL else v

fun grams(g: Double) = "${g.roundToInt()} g"
fun num(v: Double, decimals: Int = 1) = String.format(FR, "%.${decimals}f", v)

fun dayLabel(epochDay: Long): String {
    val d = LocalDate.ofEpochDay(epochDay)
    val today = LocalDate.now()
    return when (d) {
        today -> tr("Aujourd'hui")
        today.minusDays(1) -> tr("Hier")
        today.plusDays(1) -> tr("Demain")
        else -> d.format(dayFmt)
    }
}

fun shortDate(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).format(shortFmt)

/** Analyse tolérante d'un nombre saisi (virgule ou point). */
fun String.toNum(): Double? = trim().replace(',', '.').toDoubleOrNull()

fun hourLabel(hour: Int) = trf("%1\$s h", hour)
fun hourMinuteLabel(minute: Int) = String.format(FR, "%d:%02d", minute / 60, minute % 60)

fun FoodEntryEntity.macros() = Macros(kcal, protein, fat, carbs)

/** Minute de l'entrée ; les entrées v1 sans heure la déduisent de createdAt. */
fun FoodEntryEntity.minuteOf(): Int = if (minuteOfDay >= 0) minuteOfDay else
    Instant.ofEpochMilli(createdAt).atZone(ZoneId.systemDefault()).toLocalTime().let { it.hour * 60 + it.minute }

/** Initiale du jour (1 = lundi) dans la langue de l'interface. */
fun dayLetter(dow: Int): String = java.time.DayOfWeek.of(dow).getDisplayName(java.time.format.TextStyle.NARROW, Lang.locale)
