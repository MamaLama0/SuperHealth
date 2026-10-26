package app.superhealth.platform

import app.superhealth.platform.*

import kotlin.time.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Month
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.daysUntil
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn

/*
 * Sous-ensemble de java.time utilisé par l'app Android, posé sur kotlinx-datetime : le code des écrans et des
 * algorithmes reste celui d'Android (mêmes appels), seuls les imports changent.
 */

typealias LocalDate = kotlinx.datetime.LocalDate
typealias LocalDateTime = kotlinx.datetime.LocalDateTime
typealias LocalTime = kotlinx.datetime.LocalTime
typealias DayOfWeek = kotlinx.datetime.DayOfWeek
typealias ZoneId = kotlinx.datetime.TimeZone
typealias Instant = kotlin.time.Instant

fun kotlinx.datetime.TimeZone.Companion.systemDefault(): ZoneId = currentSystemDefault()

fun nowMs(): Long = Clock.System.now().toEpochMilliseconds()

// ---- LocalDate ----------------------------------------------------------------------------------

fun kotlinx.datetime.LocalDate.Companion.now(): LocalDate = Clock.System.todayIn(ZoneId.currentSystemDefault())
fun kotlinx.datetime.LocalDate.Companion.now(zone: ZoneId): LocalDate = Clock.System.todayIn(zone)
fun kotlinx.datetime.LocalDate.Companion.ofEpochDay(day: Long): LocalDate = fromEpochDays(day)
fun kotlinx.datetime.LocalDate.Companion.ofEpochDay(day: Int): LocalDate = fromEpochDays(day.toLong())
fun kotlinx.datetime.LocalDate.Companion.of(year: Int, month: Int, day: Int): LocalDate = LocalDate(year, month, day)
fun kotlinx.datetime.LocalDate.Companion.of(year: Int, month: Month, day: Int): LocalDate = LocalDate(year, month.number, day)
fun kotlinx.datetime.LocalDate.Companion.ofYearDay(year: Int, dayOfYear: Int): LocalDate = LocalDate(year, 1, 1).plus(dayOfYear - 1, DateTimeUnit.DAY)

fun LocalDate.toEpochDay(): Long = toEpochDays()
fun LocalDate.plusDays(n: Long): LocalDate = plus(n, DateTimeUnit.DAY)
fun LocalDate.plusDays(n: Int): LocalDate = plus(n, DateTimeUnit.DAY)
fun LocalDate.minusDays(n: Long): LocalDate = minus(n, DateTimeUnit.DAY)
fun LocalDate.minusDays(n: Int): LocalDate = minus(n, DateTimeUnit.DAY)
fun LocalDate.plusWeeks(n: Long): LocalDate = plus(n * 7, DateTimeUnit.DAY)
fun LocalDate.plusWeeks(n: Int): LocalDate = plus(n * 7, DateTimeUnit.DAY)
fun LocalDate.minusWeeks(n: Long): LocalDate = minus(n * 7, DateTimeUnit.DAY)
fun LocalDate.minusWeeks(n: Int): LocalDate = minus(n * 7, DateTimeUnit.DAY)
fun LocalDate.plusMonths(n: Long): LocalDate = plus(n, DateTimeUnit.MONTH)
fun LocalDate.plusMonths(n: Int): LocalDate = plus(n, DateTimeUnit.MONTH)
fun LocalDate.minusMonths(n: Long): LocalDate = minus(n, DateTimeUnit.MONTH)
fun LocalDate.minusMonths(n: Int): LocalDate = minus(n, DateTimeUnit.MONTH)
fun LocalDate.plusYears(n: Long): LocalDate = plus(n, DateTimeUnit.YEAR)
fun LocalDate.plusYears(n: Int): LocalDate = plus(n, DateTimeUnit.YEAR)
fun LocalDate.minusYears(n: Long): LocalDate = minus(n, DateTimeUnit.YEAR)
fun LocalDate.minusYears(n: Int): LocalDate = minus(n, DateTimeUnit.YEAR)
fun LocalDate.isBefore(o: LocalDate) = this < o
fun LocalDate.isAfter(o: LocalDate) = this > o
fun LocalDate.isEqual(o: LocalDate) = this == o
val LocalDate.monthValue: Int get() = month.number
val LocalDate.dayOfMonth: Int get() = day
fun LocalDate.lengthOfMonth(): Int = LocalDate(year, month.number, 1).plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY).day
fun LocalDate.lengthOfYear(): Int = if (isLeap(year)) 366 else 365
val LocalDate.isLeapYear: Boolean get() = isLeap(year)
private fun isLeap(y: Int) = (y % 4 == 0 && y % 100 != 0) || y % 400 == 0
fun LocalDate.withDayOfMonth(d: Int): LocalDate = LocalDate(year, month.number, d)
fun LocalDate.withDayOfYear(d: Int): LocalDate = LocalDate.ofYearDay(year, d)
fun LocalDate.withMonth(m: Int): LocalDate = LocalDate(year, m, minOf(day, LocalDate(year, m, 1).lengthOfMonth()))
fun LocalDate.withYear(y: Int): LocalDate = LocalDate(y, month.number, minOf(day, LocalDate(y, month.number, 1).lengthOfMonth()))
fun LocalDate.with(adjuster: TemporalAdjuster): LocalDate = adjuster(this)
fun LocalDate.atStartOfDay(zone: ZoneId): ZonedDateTime = ZonedDateTime(atStartOfDayIn(zone), zone)
fun LocalDate.atStartOfDay(): LocalDateTime = LocalDateTime(this, LocalTime(0, 0))
fun LocalDate.atTime(h: Int, m: Int, s: Int = 0): LocalDateTime = LocalDateTime(this, LocalTime(h, m, s))
fun LocalDate.atTime(t: LocalTime): LocalDateTime = LocalDateTime(this, t)
fun LocalDate.datesUntil(endExclusive: LocalDate): Sequence<LocalDate> =
    generateSequence(this) { it.plusDays(1) }.takeWhile { it < endExclusive }
fun LocalDate.until(end: LocalDate, unit: ChronoUnit): Long = unit.between(this, end)

typealias TemporalAdjuster = (LocalDate) -> LocalDate

object TemporalAdjusters {
    fun previousOrSame(d: DayOfWeek): TemporalAdjuster = { x -> x.minusDays(((x.dayOfWeek.isoDayNumber - d.isoDayNumber) + 7) % 7) }
    fun nextOrSame(d: DayOfWeek): TemporalAdjuster = { x -> x.plusDays(((d.isoDayNumber - x.dayOfWeek.isoDayNumber) + 7) % 7) }
    fun previous(d: DayOfWeek): TemporalAdjuster = { x -> x.minusDays(((x.dayOfWeek.isoDayNumber - d.isoDayNumber) + 6) % 7 + 1) }
    fun next(d: DayOfWeek): TemporalAdjuster = { x -> x.plusDays(((d.isoDayNumber - x.dayOfWeek.isoDayNumber) + 6) % 7 + 1) }
    fun firstDayOfMonth(): TemporalAdjuster = { x -> x.withDayOfMonth(1) }
    fun lastDayOfMonth(): TemporalAdjuster = { x -> x.withDayOfMonth(x.lengthOfMonth()) }
    fun firstDayOfYear(): TemporalAdjuster = { x -> LocalDate(x.year, 1, 1) }
}

enum class ChronoUnit {
    DAYS, WEEKS, MONTHS, YEARS, HOURS, MINUTES, SECONDS, MILLIS;

    fun between(a: LocalDate, b: LocalDate): Long = when (this) {
        DAYS -> a.daysUntil(b).toLong()
        WEEKS -> a.daysUntil(b).toLong() / 7
        MONTHS -> monthsBetween(a, b)
        YEARS -> monthsBetween(a, b) / 12
        else -> error("unité de date inattendue")
    }

    fun between(a: Instant, b: Instant): Long {
        val ms = b.toEpochMilliseconds() - a.toEpochMilliseconds()
        return when (this) { HOURS -> ms / 3_600_000; MINUTES -> ms / 60_000; SECONDS -> ms / 1000; MILLIS -> ms; DAYS -> ms / 86_400_000; else -> error("unité") }
    }

    fun between(a: LocalDateTime, b: LocalDateTime): Long = between(a.toInstant(ZoneId.UTC), b.toInstant(ZoneId.UTC))

    private fun monthsBetween(a: LocalDate, b: LocalDate): Long {
        var m = (b.year - a.year) * 12L + (b.month.number - a.month.number)
        if (m > 0 && b.day < a.day) m-- else if (m < 0 && b.day > a.day) m++
        return m
    }
}

// ---- DayOfWeek / Month --------------------------------------------------------------------------

val DayOfWeek.value: Int get() = isoDayNumber
fun dayOfWeekOf(iso: Int): DayOfWeek = kotlinx.datetime.DayOfWeek(((iso - 1).mod(7)) + 1)
fun DayOfWeek.plus(days: Long): DayOfWeek = dayOfWeekOf((isoDayNumber - 1 + days.mod(7)) % 7 + 1)
fun DayOfWeek.plus(days: Int): DayOfWeek = plus(days.toLong())
fun DayOfWeek.minus(days: Long): DayOfWeek = plus(-days)
fun DayOfWeek.minus(days: Int): DayOfWeek = plus(-days.toLong())
val Month.value: Int get() = number

enum class DateTextStyle { FULL, FULL_STANDALONE, SHORT, SHORT_STANDALONE, NARROW, NARROW_STANDALONE }

fun DayOfWeek.getDisplayName(style: DateTextStyle, locale: Locale): String {
    val i = isoDayNumber - 1
    val fr = locale.language == "fr"
    return when (style) {
        DateTextStyle.FULL, DateTextStyle.FULL_STANDALONE -> (if (fr) DateNames.frDays else DateNames.enDays)[i]
        DateTextStyle.SHORT, DateTextStyle.SHORT_STANDALONE -> (if (fr) DateNames.frDaysShort else DateNames.enDaysShort)[i]
        DateTextStyle.NARROW, DateTextStyle.NARROW_STANDALONE -> (if (fr) DateNames.frDays else DateNames.enDays)[i].take(1).uppercase()
    }
}

fun Month.getDisplayName(style: DateTextStyle, locale: Locale): String {
    val i = number - 1
    val fr = locale.language == "fr"
    return when (style) {
        DateTextStyle.FULL, DateTextStyle.FULL_STANDALONE -> (if (fr) DateNames.frMonths else DateNames.enMonths)[i]
        DateTextStyle.SHORT, DateTextStyle.SHORT_STANDALONE -> (if (fr) DateNames.frMonthsShort else DateNames.enMonthsShort)[i]
        DateTextStyle.NARROW, DateTextStyle.NARROW_STANDALONE -> (if (fr) DateNames.frMonths else DateNames.enMonths)[i].take(1).uppercase()
    }
}

internal object DateNames {
    val frDays = listOf("lundi", "mardi", "mercredi", "jeudi", "vendredi", "samedi", "dimanche")
    val frDaysShort = listOf("lun.", "mar.", "mer.", "jeu.", "ven.", "sam.", "dim.")
    val enDays = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
    val enDaysShort = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    val frMonths = listOf("janvier", "février", "mars", "avril", "mai", "juin", "juillet", "août", "septembre", "octobre", "novembre", "décembre")
    val frMonthsShort = listOf("janv.", "févr.", "mars", "avr.", "mai", "juin", "juil.", "août", "sept.", "oct.", "nov.", "déc.")
    val enMonths = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
    val enMonthsShort = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
}

// ---- Instant / zoned ----------------------------------------------------------------------------

fun kotlin.time.Instant.Companion.ofEpochMilli(ms: Long): Instant = fromEpochMilliseconds(ms)
fun kotlin.time.Instant.Companion.ofEpochSecond(s: Long): Instant = fromEpochSeconds(s)
fun kotlin.time.Instant.Companion.now(): Instant = Clock.System.now()
fun Instant.toEpochMilli(): Long = toEpochMilliseconds()
val Instant.epochSecond: Long get() = epochSeconds
fun Instant.atZone(zone: ZoneId): ZonedDateTime = ZonedDateTime(this, zone)
fun Instant.plusMillis(ms: Long): Instant = Instant.fromEpochMilliseconds(toEpochMilliseconds() + ms)
fun Instant.plusSeconds(s: Long): Instant = plusMillis(s * 1000)
fun Instant.isBefore(o: Instant) = this < o
fun Instant.isAfter(o: Instant) = this > o

class ZonedDateTime(val instant: Instant, val zone: ZoneId) {
    private val ldt: LocalDateTime = instant.toLocalDateTime(zone)
    fun toInstant(): Instant = instant
    fun toLocalDate(): LocalDate = ldt.date
    fun toLocalDateTime(): LocalDateTime = ldt
    fun toLocalTime(): LocalTime = ldt.time
    fun toEpochSecond(): Long = instant.epochSeconds
    val hour: Int get() = ldt.hour
    val minute: Int get() = ldt.minute
    val second: Int get() = ldt.second
    val year: Int get() = ldt.year
    val monthValue: Int get() = ldt.month.number
    val dayOfMonth: Int get() = ldt.day
    val dayOfWeek: DayOfWeek get() = ldt.dayOfWeek
    fun format(f: DateTimeFormatter): String = f.format(ldt.date, ldt.time)
    private fun with(t: LocalTime) = ZonedDateTime(LocalDateTime(ldt.date, t).toInstant(zone), zone)
    fun withHour(h: Int) = with(LocalTime(h, ldt.minute, ldt.second))
    fun withMinute(m: Int) = with(LocalTime(ldt.hour, m, ldt.second))
    fun withSecond(s: Int) = with(LocalTime(ldt.hour, ldt.minute, s))
    fun plusDays(n: Long) = ZonedDateTime(LocalDateTime(ldt.date.plusDays(n), ldt.time).toInstant(zone), zone)
    fun minusSeconds(s: Long) = ZonedDateTime(instant.plusMillis(-s * 1000), zone)
}

// ---- LocalDateTime / LocalTime ------------------------------------------------------------------

fun kotlinx.datetime.LocalDateTime.Companion.now(): LocalDateTime = Clock.System.now().toLocalDateTime(ZoneId.currentSystemDefault())
fun kotlinx.datetime.LocalDateTime.Companion.of(d: LocalDate, t: LocalTime): LocalDateTime = LocalDateTime(d, t)
fun LocalDateTime.atZone(zone: ZoneId): ZonedDateTime = ZonedDateTime(toInstant(zone), zone)
fun LocalDateTime.toLocalDate(): LocalDate = date
fun LocalDateTime.toLocalTime(): LocalTime = time
fun LocalDateTime.format(f: DateTimeFormatter): String = f.format(date, time)
val LocalDateTime.monthValue: Int get() = month.number
val LocalDateTime.dayOfMonth: Int get() = day
fun kotlinx.datetime.LocalTime.Companion.now(): LocalTime = LocalDateTime.now().time
fun kotlinx.datetime.LocalTime.Companion.of(h: Int, m: Int, s: Int = 0): LocalTime = LocalTime(h, m, s)
fun kotlinx.datetime.LocalTime.Companion.ofSecondOfDay(s: Long): LocalTime = fromSecondOfDay(s.toInt())
fun LocalTime.format(f: DateTimeFormatter): String = f.format(null, this)
fun LocalTime.isBefore(o: LocalTime) = this < o
fun LocalTime.isAfter(o: LocalTime) = this > o
fun LocalTime.plusMinutes(m: Long): LocalTime = LocalTime.fromSecondOfDay(((toSecondOfDay() + m * 60).mod(86_400L)).toInt())
fun LocalTime.withHour(h: Int): LocalTime = LocalTime(h, minute, second)
fun LocalTime.withMinute(m: Int): LocalTime = LocalTime(hour, m, second)

fun LocalDate.format(f: DateTimeFormatter): String = f.format(this, null)

// ---- DateTimeFormatter (motifs utilisés par l'app) ---------------------------------------------

class DateTimeFormatter private constructor(private val pattern: String, private val locale: Locale) {
    companion object {
        fun ofPattern(pattern: String): DateTimeFormatter = DateTimeFormatter(pattern, Locale.getDefault())
        fun ofPattern(pattern: String, locale: Locale): DateTimeFormatter = DateTimeFormatter(pattern, locale)
        val ISO_LOCAL_DATE: DateTimeFormatter = DateTimeFormatter("yyyy-MM-dd", Locale.ROOT)
        val ISO_LOCAL_TIME: DateTimeFormatter = DateTimeFormatter("HH:mm:ss", Locale.ROOT)
    }

    fun withLocale(l: Locale) = DateTimeFormatter(pattern, l)

    internal fun format(d: LocalDate?, t: LocalTime?): String {
        val fr = locale.language == "fr"
        val out = StringBuilder()
        var i = 0
        while (i < pattern.length) {
            val c = pattern[i]
            if (c == '\'') {
                val end = pattern.indexOf('\'', i + 1).let { if (it < 0) pattern.length else it }
                if (end == i + 1) out.append('\'') else out.append(pattern, i + 1, end)
                i = end + 1; continue
            }
            if (!c.isLetter()) { out.append(c); i++; continue }
            var n = 1
            while (i + n < pattern.length && pattern[i + n] == c) n++
            i += n
            out.append(when (c) {
                'd' -> d!!.day.toString().padStart(n, '0')
                'D' -> d!!.dayOfYear.toString()
                'M', 'L' -> when {
                    n >= 4 -> (if (fr) DateNames.frMonths else DateNames.enMonths)[d!!.month.number - 1]
                    n == 3 -> (if (fr) DateNames.frMonthsShort else DateNames.enMonthsShort)[d!!.month.number - 1]
                    else -> d!!.month.number.toString().padStart(n, '0')
                }
                'y', 'u' -> if (n == 2) (d!!.year % 100).toString().padStart(2, '0') else d!!.year.toString()
                'E', 'e', 'c' -> if (n >= 4) (if (fr) DateNames.frDays else DateNames.enDays)[d!!.dayOfWeek.isoDayNumber - 1]
                    else (if (fr) DateNames.frDaysShort else DateNames.enDaysShort)[d!!.dayOfWeek.isoDayNumber - 1]
                'H' -> t!!.hour.toString().padStart(n, '0')
                'h' -> ((t!!.hour + 11) % 12 + 1).toString().padStart(n, '0')
                'm' -> t!!.minute.toString().padStart(n, '0')
                's' -> t!!.second.toString().padStart(n, '0')
                'a' -> if (t!!.hour < 12) "AM" else "PM"
                else -> c.toString().repeat(n)
            })
        }
        return out.toString()
    }

    fun format(d: LocalDate): String = format(d, null)
}

// ---- YearMonth / Period -------------------------------------------------------------------------

data class YearMonth(val year: Int, val monthValue: Int) : Comparable<YearMonth> {
    companion object {
        fun of(year: Int, month: Int) = YearMonth(year, month)
        fun from(d: LocalDate) = YearMonth(d.year, d.month.number)
        fun now() = from(LocalDate.now())
    }
    fun atDay(day: Int): LocalDate = LocalDate(year, monthValue, day)
    fun atEndOfMonth(): LocalDate = atDay(1).let { it.withDayOfMonth(it.lengthOfMonth()) }
    fun lengthOfMonth(): Int = atDay(1).lengthOfMonth()
    fun plusMonths(n: Long): YearMonth = from(atDay(1).plusMonths(n))
    fun minusMonths(n: Long): YearMonth = from(atDay(1).minusMonths(n))
    fun format(f: DateTimeFormatter): String = f.format(atDay(1), null)
    override fun compareTo(other: YearMonth) = compareValuesBy(this, other, { it.year }, { it.monthValue })
}

data class Period(val years: Int, val months: Int, val days: Int) {
    companion object {
        fun between(a: LocalDate, b: LocalDate): Period {
            val m = ChronoUnit.MONTHS.between(a, b)
            val rest = a.plusMonths(m).daysUntil(b)
            return Period((m / 12).toInt(), (m % 12).toInt(), rest)
        }
    }
}
