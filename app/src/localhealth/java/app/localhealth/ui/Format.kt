package app.localhealth.ui

import android.content.Context
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import app.localhealth.data.Metric
import nodomain.freeyourgadget.gadgetbridge.R

fun formatNumber(v: Double, digits: Int = 0): String =
    NumberFormat.getNumberInstance(Locale.getDefault()).apply {
        minimumFractionDigits = digits
        maximumFractionDigits = digits
    }.format(v)

fun formatDuration(context: Context, minutes: Double): String =
    context.getString(R.string.lh_hours_minutes, minutes.toInt() / 60, minutes.toInt() % 60)

/** Value without unit, e.g. "8 432", "7 h 12", "62,5". */
fun formatValue(context: Context, metric: Metric, v: Double): String = when (metric) {
    Metric.SLEEP -> formatDuration(context, v)
    else -> formatNumber(v, metric.digits)
}

fun unitOf(context: Context, metric: Metric): String? = metric.unit?.let { context.getString(it) }

/** Value with its unit separated by a no-break space ("133 kg"). */
fun formatWithUnit(context: Context, metric: Metric, v: Double): String {
    val unit = unitOf(context, metric)
    val value = formatValue(context, metric, v)
    return if (unit == null || metric == Metric.SLEEP) value else "$value $unit"
}

/** Monday-first single-letter weekday labels in the user's locale (L M M J V S D). */
fun dayLetters(days: List<LocalDate>): List<String> =
    days.map { it.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()).uppercase(Locale.getDefault()) }
