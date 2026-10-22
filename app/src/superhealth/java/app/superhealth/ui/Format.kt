package app.superhealth.ui

import app.superhealth.health.data.Metric
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

fun formatNumber(v: Double, digits: Int = 0): String =
    NumberFormat.getNumberInstance(Lang.locale).apply {
        minimumFractionDigits = digits
        maximumFractionDigits = digits
    }.format(v)

/** « 7 h 32 » / « 7h 32m ». */
fun formatDuration(minutes: Double): String {
    val m = minutes.toInt()
    return if (m < 60) trf("%1\$s min", m) else trf("%1\$s h %2\$s", m / 60, "%02d".format(m % 60))
}

/** Valeur sans unité : « 8 432 », « 7 h 12 », « 62,5 ». */
fun formatValue(metric: Metric, v: Double): String = when (metric) {
    Metric.SLEEP, Metric.SLEEP_DEBT -> formatDuration(v)
    else -> formatNumber(v, metric.digits)
}

fun unitOf(metric: Metric): String? = if (metric == Metric.SLEEP || metric == Metric.SLEEP_DEBT) null else metric.unit?.let(::tr)

fun formatWithUnit(metric: Metric, v: Double): String {
    val unit = unitOf(metric)
    val value = formatValue(metric, v)
    return if (unit == null) value else "$value $unit"
}

fun dayLetter(d: LocalDate): String = d.dayOfWeek.getDisplayName(TextStyle.NARROW, Lang.locale).uppercase(Lang.locale)

fun localDate(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()

/** « Aujourd'hui », « Hier », sinon « lun. 13 oct. ». */
fun relativeDay(d: LocalDate): String {
    val today = LocalDate.now()
    return when (d) {
        today -> tr("Aujourd'hui")
        today.minusDays(1) -> tr("Hier")
        else -> d.format(DateTimeFormatter.ofPattern("EEE d MMM", Lang.locale))
    }
}

fun timeOf(ms: Long): String = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalTime()
    .format(DateTimeFormatter.ofPattern(if (Lang.english) "h:mm a" else "HH:mm", Lang.locale))

fun longToday(): String = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE d MMMM", Lang.locale)).replaceFirstChar { it.uppercase(Lang.locale) }
