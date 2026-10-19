package app.localhealth.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import java.text.NumberFormat
import java.util.Locale
import nodomain.freeyourgadget.gadgetbridge.R

fun formatInt(v: Number): String = NumberFormat.getIntegerInstance(Locale.getDefault()).format(v.toLong())

fun formatDecimal(v: Double, digits: Int = 1): String =
    NumberFormat.getNumberInstance(Locale.getDefault()).apply {
        minimumFractionDigits = digits
        maximumFractionDigits = digits
    }.format(v)

@Composable
fun formatDuration(minutes: Double): String =
    stringResource(R.string.lh_hours_minutes, minutes.toInt() / 60, minutes.toInt() % 60)
