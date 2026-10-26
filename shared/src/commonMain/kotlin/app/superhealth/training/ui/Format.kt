package app.superhealth.training.ui

import app.superhealth.platform.*

import app.superhealth.training.domain.Settings
import app.superhealth.training.domain.WeightUnit
import app.superhealth.platform.Instant
import app.superhealth.platform.LocalDate
import app.superhealth.platform.ZoneId
import app.superhealth.platform.DateTimeFormatter
import app.superhealth.platform.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

const val KG_PER_LB = 0.45359237
private val FR get() = Lang.locale
private val dayFmt get() = DateTimeFormatter.ofPattern("EEEE d MMMM", FR)
private val shortFmt get() = DateTimeFormatter.ofPattern("d MMM", FR)

fun num(v: Double, decimals: Int = 1) = String.format(FR, "%.${decimals}f", v)

/** Nombre sans décimales inutiles : 82,5 / 80. */
fun clean(v: Double): String = if (abs(v - v.roundToInt()) < 1e-6) v.roundToInt().toString() else num(v, if (abs(v * 10 - (v * 10).roundToInt()) < 1e-6) 1 else 2)

fun Settings.w(kg: Double): Double = if (unit == WeightUnit.LB) kg / KG_PER_LB else kg
fun Settings.toKg(v: Double) = if (unit == WeightUnit.LB) v * KG_PER_LB else v
fun Settings.weight(kg: Double?): String = if (kg == null) "—" else "${clean(w(kg))} ${unit.label}"
/** Estimation (1RM) arrondie au demi-kilo / demi-livre. */
fun Settings.estimate(kg: Double?): String = if (kg == null) "—" else "${clean(Math.round(w(kg) * 2) / 2.0)} ${unit.label}"
fun Settings.volume(kg: Double): String = "${String.format(FR, "%,d", w(kg).roundToInt())} ${unit.label}"

fun String.toNum(): Double? = trim().replace(',', '.').toDoubleOrNull()

fun localDate(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()
fun longDate(ms: Long): String = localDate(ms).format(dayFmt).replaceFirstChar { it.uppercase() }
fun shortDate(ms: Long): String = localDate(ms).format(shortFmt)

fun duration(sec: Long): String {
    val h = sec / 3600; val m = (sec % 3600) / 60; val s = sec % 60
    return if (h > 0) String.format(FR, "%d:%02d:%02d", h, m, s) else String.format(FR, "%d:%02d", m, s)
}

fun durationWords(sec: Long): String {
    val h = sec / 3600; val m = (sec % 3600) / 60
    return if (h > 0) trf("%1\$s h %2\$s min", h, m) else trf("%1\$s min", m)
}

fun restLabel(sec: Int) = if (sec >= 60) String.format(FR, "%d:%02d", sec / 60, sec % 60) else trf("%1\$s s", sec)

/** Initiale du jour (1 = lundi) dans la langue de l'interface. */
fun dayLetter(dow: Int): String = dayOfWeekOf(dow).getDisplayName(DateTextStyle.NARROW, Lang.locale)
