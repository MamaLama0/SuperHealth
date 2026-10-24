package app.superhealth.bridge

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Xml
import app.superhealth.health.data.HealthRepository
import app.superhealth.health.data.Lap
import app.superhealth.health.data.TrackPoint
import app.superhealth.health.data.WorkoutDetail
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.time.Instant
import java.time.OffsetDateTime
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.FitFile
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.messages.FitRecord
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.messages.FitSession
import org.slf4j.LoggerFactory
import org.xmlpull.v1.XmlPullParser

/**
 * Courses et autres activités importées depuis les apps de running, sans montre associée :
 * - fichiers GPX (Strava, Nike Run Club via export, Komoot, Runkeeper, Adidas Running…), TCX (Garmin, Polar, Suunto) et FIT
 *   (Garmin, Coros, Suunto, Wahoo, Zwift), éventuellement compressés (.gz) ;
 * - archives ZIP, même imbriquées : export « Vos données » de Strava (activities.csv donne nom et type), de Garmin Connect,
 *   de Polar Flow, de Suunto…
 * Les fichiers .fit vont à la montre Garmin associée quand il y en a une (analyse complète de Gadgetbridge, [FitImport]) ;
 * sinon, comme tout le reste, ils sont lus ici. Chaque séance est gardée dans files/runs (résumé + tracé allégé) et apparaît
 * dans les activités comme celles de la montre. Une séance qui recouvre une activité déjà connue est ignorée.
 */
object RunImport {
    private val LOG = LoggerFactory.getLogger(RunImport::class.java)

    @Serializable
    data class Run(
        /** Négatif : distinct des séances de la montre (identifiants de la base Gadgetbridge). */
        val id: Long,
        val name: String,
        val kind: String,
        val app: String,
        val start: Long,
        val end: Long,
        val activeSec: Double,
        val distanceM: Double? = null,
        val kcal: Double? = null,
        val avgHr: Int? = null,
        val maxHr: Int? = null,
        val ascentM: Double? = null,
        val descentM: Double? = null,
        val avgCadence: Double? = null,
        val maxSpeed: Double? = null,
    )

    @Serializable
    data class P(val t: Double, val d: Double, val lat: Double? = null, val lon: Double? = null, val alt: Double? = null,
                 val hr: Int? = null, val cad: Int? = null, val v: Double? = null)

    @Serializable
    private data class Stored(val run: Run, val points: List<P>)

    data class ImportResult(val imported: Int, val failed: Int, val skipped: Int)

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private lateinit var dir: File
    private val _runs = MutableStateFlow<List<Run>>(emptyList())
    val runs: StateFlow<List<Run>> = _runs

    fun init(ctx: Context) {
        dir = File(ctx.filesDir, "runs").apply { mkdirs() }
        _runs.value = runCatching { json.decodeFromString(ListSerializer(Run.serializer()), File(dir, "index.json").readText()) }
            .getOrDefault(emptyList())
    }

    private fun saveIndex(list: List<Run>) {
        _runs.value = list.sortedByDescending { it.start }
        File(dir, "index.json").writeText(json.encodeToString(ListSerializer(Run.serializer()), _runs.value))
    }

    fun delete(id: Long) {
        File(dir, "$id.json").delete()
        saveIndex(_runs.value.filter { it.id != id })
    }

    // ---- Import -----------------------------------------------------------------------------------------

    /** Fichier lu dans une archive ou choisi directement, avec son nom (pour activities.csv de Strava). */
    private class Source(val name: String, val bytes: ByteArray)

    suspend fun import(ctx: Context, uris: List<Uri>, onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }): ImportResult = withContext(Dispatchers.IO) {
        val files = ArrayList<Source>()
        val csv = HashMap<String, Pair<String, String>>() // fichier → (nom, type) d'après activities.csv
        uris.forEach { uri ->
            val name = displayName(ctx, uri) ?: uri.lastPathSegment ?: "import"
            ctx.contentResolver.openInputStream(uri)?.use { collect(it.buffered(), name, "", files, csv) }
        }
        val garmin = FitImport.supported()
        val fits = if (garmin) files.filter { it.name.lowercase().endsWith(".fit") } else emptyList()
        val own = files - fits.toSet()
        var ok = 0; var ko = 0; var skipped = 0
        // .fit avec une montre Garmin : analyse de Gadgetbridge (dynamique de course, effet d'entraînement…)
        if (fits.isNotEmpty()) {
            val r = FitImport.importFiles(ctx, fits.map { it.name.substringAfterLast('/') to it.bytes })
            ok += r.imported; ko += r.failed; skipped += r.skipped
        }
        // séances déjà connues : montre (toutes dates), Health Connect et imports précédents
        val known = ArrayList<Pair<Long, Long>>()
        known += runCatching { HealthRepository.workouts(0L, Long.MAX_VALUE) }.getOrDefault(emptyList()).map { it.start.time to it.end.time }
        known += app.superhealth.SuperPrefs.hc.value.sessions.map { it.start to it.end }
        known += _runs.value.map { it.start to it.end }
        val added = ArrayList<Run>()
        own.forEachIndexed { i, f ->
            onProgress(i, own.size)
            val parsed = runCatching { parse(f) }.onFailure { LOG.warn("Unreadable activity file {}", f.name, it) }.getOrNull()
            if (parsed == null) { ko++; return@forEachIndexed }
            var (run, points) = parsed
            csv[f.name]?.let { (n, type) -> run = run.copy(name = n.ifBlank { run.name }, kind = kindOf(type) ?: run.kind, app = "Strava") }
            // même séance déjà là (recouvrement de plus de la moitié de la plus courte)
            if (known.any { (s, e) -> overlap(s, e, run.start, run.end) > 0.5 * minOf(e - s, run.end - run.start).coerceAtLeast(1) }) { skipped++; return@forEachIndexed }
            File(dir, "${run.id}.json").writeText(json.encodeToString(Stored.serializer(), Stored(run, points)))
            known += run.start to run.end
            added += run
            ok++
        }
        if (added.isNotEmpty()) saveIndex(_runs.value + added)
        onProgress(own.size, own.size)
        ImportResult(ok, ko, skipped)
    }

    private fun overlap(s1: Long, e1: Long, s2: Long, e2: Long) = (minOf(e1, e2) - maxOf(s1, s2)).coerceAtLeast(0)

    /** Rassemble les fichiers d'activité ([files]) et la table activities.csv de Strava ([csv]), archives comprises. */
    private fun collect(input: InputStream, name: String, prefix: String, files: MutableList<Source>, csv: MutableMap<String, Pair<String, String>>) {
        val lower = name.lowercase()
        when {
            lower.endsWith(".zip") -> {
                val zip = ZipInputStream(input)
                while (true) {
                    val e = zip.nextEntry ?: break
                    if (!e.isDirectory) collect(NonClosing(zip), e.name.substringAfterLast('/'), e.name.substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }, files, csv)
                }
            }
            lower.endsWith(".gz") -> collect(GZIPInputStream(input), name.dropLast(3), prefix, files, csv)
            lower.endsWith(".gpx") || lower.endsWith(".tcx") || lower.endsWith(".fit") -> {
                val bytes = input.readBytes()
                if (bytes.size in 64..64 * 1024 * 1024) files += Source(prefix + name, bytes)
            }
            lower == "activities.csv" -> readStravaCsv(input.readBytes().decodeToString(), csv)
        }
    }

    /** activities.csv de Strava : « Activity Name », « Activity Type » et « Filename » (activities/123.gpx.gz). */
    private fun readStravaCsv(text: String, out: MutableMap<String, Pair<String, String>>) {
        val rows = parseCsv(text)
        val head = rows.firstOrNull() ?: return
        val iName = head.indexOfFirst { it.equals("Activity Name", true) }
        val iType = head.indexOfFirst { it.equals("Activity Type", true) }
        val iFile = head.indexOfFirst { it.equals("Filename", true) }
        if (iFile < 0) return
        rows.drop(1).forEach { r ->
            val f = r.getOrNull(iFile)?.trim()?.removeSuffix(".gz")?.takeIf { it.isNotEmpty() } ?: return@forEach
            out[f] = (r.getOrNull(iName) ?: "") to (r.getOrNull(iType) ?: "")
        }
    }

    private fun parseCsv(text: String): List<List<String>> {
        val rows = ArrayList<List<String>>(); var row = ArrayList<String>(); val cell = StringBuilder(); var quoted = false; var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                quoted && c == '"' && text.getOrNull(i + 1) == '"' -> { cell.append('"'); i++ }
                c == '"' -> quoted = !quoted
                !quoted && c == ',' -> { row += cell.toString(); cell.clear() }
                !quoted && (c == '\n' || c == '\r') -> {
                    if (c == '\r' && text.getOrNull(i + 1) == '\n') i++
                    row += cell.toString(); cell.clear(); rows += row; row = ArrayList()
                }
                else -> cell.append(c)
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) { row += cell.toString(); rows += row }
        return rows
    }

    // ---- Lecture des formats ------------------------------------------------------------------------------

    /** Point brut d'un fichier : temps absolu (ms), distance cumulée si le fichier la donne. */
    private data class Raw(val ms: Long, val lat: Double?, val lon: Double?, val alt: Double?, val hr: Int?, val cad: Int?, val dist: Double?, val speed: Double?)

    /** Résumé donné par le fichier (TCX : tours ; FIT : session), prioritaire sur ce qui se recalcule du tracé. */
    private data class Summary(var name: String? = null, var type: String? = null, var app: String? = null, var timerSec: Double? = null,
                               var distance: Double? = null, var kcal: Double? = null, var avgHr: Int? = null, var maxHr: Int? = null,
                               var ascent: Double? = null, var descent: Double? = null, var cadence: Double? = null, var maxSpeed: Double? = null)

    private fun parse(f: Source): Pair<Run, List<P>>? {
        val lower = f.name.lowercase()
        val s = Summary()
        val raw = when {
            lower.endsWith(".gpx") -> gpx(f.bytes, s)
            lower.endsWith(".tcx") -> tcx(f.bytes, s)
            lower.endsWith(".fit") -> fit(f.bytes, s)
            else -> return null
        }.filter { it.ms > 0 }.sortedBy { it.ms }
        if (raw.size < 2) return null
        return build(raw, s, f.name)
    }

    private fun xml(bytes: ByteArray): XmlPullParser = Xml.newPullParser().apply {
        setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        setInput(ByteArrayInputStream(bytes), null)
    }

    private fun time(s: String?): Long? = s?.trim()?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() ?: runCatching { OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() }

    /** Nom local d'une balise (sans préfixe d'espace de noms : gpxtpx:hr → hr). */
    private val XmlPullParser.local get() = name.substringAfter(':')

    private fun gpx(bytes: ByteArray, s: Summary): List<Raw> {
        val p = xml(bytes); val out = ArrayList<Raw>()
        var lat: Double? = null; var lon: Double? = null; var alt: Double? = null; var t: Long? = null; var hr: Int? = null; var cad: Int? = null
        var inPt = false; var inTrk = false
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            when (p.eventType) {
                XmlPullParser.START_TAG -> when (p.local) {
                    "gpx" -> s.app = creator(p.getAttributeValue(null, "creator"))
                    "trk" -> inTrk = true
                    "trkpt", "rtept" -> { inPt = true; lat = p.getAttributeValue(null, "lat")?.toDoubleOrNull(); lon = p.getAttributeValue(null, "lon")?.toDoubleOrNull(); alt = null; t = null; hr = null; cad = null }
                    "ele" -> if (inPt) alt = p.nextText().trim().toDoubleOrNull()
                    "time" -> if (inPt) t = time(p.nextText())
                    "hr", "heartrate" -> if (inPt) hr = p.nextText().trim().toDoubleOrNull()?.roundToInt()
                    "cad", "cadence" -> if (inPt) cad = p.nextText().trim().toDoubleOrNull()?.roundToInt()
                    "name" -> if (inTrk && !inPt && s.name == null) s.name = p.nextText().trim()
                    "type" -> if (inTrk && !inPt && s.type == null) s.type = p.nextText().trim()
                }
                XmlPullParser.END_TAG -> when (p.local) {
                    "trkpt", "rtept" -> { inPt = false; t?.let { out += Raw(it, lat, lon, alt, hr, cad, null, null) } }
                    "trk" -> inTrk = false
                }
            }
        }
        return out
    }

    private fun tcx(bytes: ByteArray, s: Summary): List<Raw> {
        val p = xml(bytes); val out = ArrayList<Raw>()
        var t: Long? = null; var lat: Double? = null; var lon: Double? = null; var alt: Double? = null; var d: Double? = null
        var hr: Int? = null; var cad: Int? = null; var v: Double? = null
        var inPt = false; var inLap = 0; var inHr = ""; var timer = 0.0; var kcal = 0.0; var lapDist = 0.0; var maxHr = 0
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            when (p.eventType) {
                XmlPullParser.START_TAG -> when (p.local) {
                    "Activity" -> s.type = p.getAttributeValue(null, "Sport")
                    "Lap" -> inLap++
                    "Trackpoint" -> { inPt = true; t = null; lat = null; lon = null; alt = null; d = null; hr = null; cad = null; v = null }
                    "Time" -> if (inPt) t = time(p.nextText())
                    "LatitudeDegrees" -> if (inPt) lat = p.nextText().trim().toDoubleOrNull()
                    "LongitudeDegrees" -> if (inPt) lon = p.nextText().trim().toDoubleOrNull()
                    "AltitudeMeters" -> if (inPt) alt = p.nextText().trim().toDoubleOrNull()
                    "DistanceMeters" -> p.nextText().trim().toDoubleOrNull()?.let { if (inPt) d = it else if (inLap > 0) lapDist += it }
                    "HeartRateBpm" -> inHr = "pt"
                    "AverageHeartRateBpm" -> inHr = "avg"
                    "MaximumHeartRateBpm" -> inHr = "max"
                    "Value" -> p.nextText().trim().toDoubleOrNull()?.roundToInt()?.let { if (inHr == "pt" && inPt) hr = it else if (inHr == "max") maxHr = maxOf(maxHr, it) }
                    "Cadence", "RunCadence" -> if (inPt) cad = p.nextText().trim().toDoubleOrNull()?.roundToInt()
                    "Speed" -> if (inPt) v = p.nextText().trim().toDoubleOrNull()
                    "TotalTimeSeconds" -> if (!inPt) timer += p.nextText().trim().toDoubleOrNull() ?: 0.0
                    "Calories" -> if (!inPt) kcal += p.nextText().trim().toDoubleOrNull() ?: 0.0
                    "Name" -> if (s.app == null && !inPt && inLap == 0) s.app = creator(p.nextText())
                }
                XmlPullParser.END_TAG -> when (p.local) {
                    "Trackpoint" -> { inPt = false; t?.let { out += Raw(it, lat, lon, alt, hr, cad, d, v) } }
                    "Lap" -> inLap--
                    "HeartRateBpm", "AverageHeartRateBpm", "MaximumHeartRateBpm" -> inHr = ""
                }
            }
        }
        if (timer > 0) s.timerSec = timer
        if (kcal > 0) s.kcal = kcal
        if (lapDist > 0) s.distance = lapDist
        if (maxHr > 0) s.maxHr = maxHr
        return out
    }

    private fun fit(bytes: ByteArray, s: Summary): List<Raw> {
        val file = FitFile.parseIncoming(bytes)
        val out = ArrayList<Raw>()
        file.records.forEach { r ->
            when (r) {
                is FitRecord -> runCatching {
                    val a = r.toActivityPoint()
                    val loc = a.location
                    out += Raw(a.time.time, loc?.latitude?.takeIf { it != 0.0 }, loc?.longitude?.takeIf { it != 0.0 },
                        (loc?.altitude ?: a.altitude).takeIf { !it.isNaN() && it != 0.0 }, a.heartRate.takeIf { it in 30..240 },
                        a.cadence.takeIf { it > 0 }, a.distance.takeIf { it > 0 }, a.speed.toDouble().takeIf { it > 0 && !it.isNaN() })
                }
                is FitSession -> {
                    s.type = fitSport(r.sport)
                    s.timerSec = r.totalTimerTime; s.distance = r.totalDistance; s.kcal = r.totalCalories?.toDouble()
                    s.avgHr = r.avgHeartRate; s.maxHr = r.maxHeartRate; s.ascent = r.totalAscent?.toDouble(); s.descent = r.totalDescent?.toDouble()
                    s.cadence = r.avgCadence?.toDouble(); s.maxSpeed = (r.enhancedMaxSpeed ?: r.maxSpeed?.toDouble())
                }
            }
        }
        return out
    }

    /** Sport FIT (profil officiel) : 1 course, 2 vélo, 5 natation, 11 marche, 17 randonnée, 43 yoga. */
    private fun fitSport(sport: Int?): String? = when (sport) { 1 -> "RUN"; 2 -> "BIKE"; 5 -> "SWIM"; 11 -> "WALK"; 17 -> "HIKE"; 43 -> "YOGA"; else -> null }

    private fun creator(c: String?): String? {
        val l = c?.lowercase() ?: return null
        return listOf("strava" to "Strava", "garmin" to "Garmin Connect", "polar" to "Polar", "suunto" to "Suunto", "coros" to "COROS",
            "nike" to "Nike Run Club", "runkeeper" to "Runkeeper", "adidas" to "adidas Running", "runtastic" to "adidas Running",
            "komoot" to "Komoot", "wahoo" to "Wahoo", "zwift" to "Zwift", "fitbit" to "Fitbit", "samsung" to "Samsung Health",
            "apple" to "Apple Santé", "amazfit" to "Zepp", "zepp" to "Zepp", "decathlon" to "Decathlon Coach")
            .firstOrNull { l.contains(it.first) }?.second
    }

    /** Type d'activité écrit par une app (GPX « running », TCX « Running », Strava « Run », code Strava 9…) → [Bridge.Kind]. */
    private fun kindOf(type: String?): String? {
        val t = type?.trim()?.uppercase() ?: return null
        return when {
            t.isEmpty() -> null
            t == "9" || "RUN" in t || "JOG" in t || "COURSE" in t -> "RUN"
            t == "1" || "RIDE" in t || "BIK" in t || "CYCL" in t || "VÉLO" in t || "VELO" in t -> "BIKE"
            t == "10" || "WALK" in t || "MARCHE" in t -> "WALK"
            t == "4" || "HIK" in t || "RANDO" in t -> "HIKE"
            t == "2" || "SWIM" in t || "NATATION" in t -> "SWIM"
            "YOGA" in t -> "YOGA"
            "WEIGHT" in t || "STRENGTH" in t -> "STRENGTH"
            else -> "OTHER"
        }
    }

    /**
     * Séance à partir des points : distance cumulée du fichier, sinon recalculée entre points GPS ; temps de mouvement =
     * celui du fichier (chrono TCX / FIT), sinon les intervalles d'au plus 30 s ou où l'on avance (> 0,5 m/s) ; dénivelé =
     * hausses d'altitude de plus de 2 m (hystérésis contre le bruit). Tracé gardé à 4 000 points au plus.
     */
    private fun build(raw: List<Raw>, s: Summary, file: String): Pair<Run, List<P>> {
        val t0 = raw.first().ms
        var cum = 0.0; var prevCum = 0.0; var last: Raw? = null; var moving = 0.0
        val pts = raw.map { r ->
            val prev = last
            if (r.dist != null) cum = maxOf(cum, r.dist)
            else if (prev != null && r.lat != null && r.lon != null && prev.lat != null && prev.lon != null) cum += haversine(prev.lat, prev.lon, r.lat, r.lon)
            if (prev != null) {
                val dt = (r.ms - prev.ms) / 1000.0
                if (dt in 0.0..30.0 || (dt > 0 && (cum - prevCum) / dt > 0.5)) moving += dt
            }
            prevCum = cum; last = r
            P((r.ms - t0) / 1000.0, cum, r.lat, r.lon, r.alt, r.hr, r.cad, r.speed)
        }
        var up = 0.0; var down = 0.0; var ref: Double? = null
        pts.forEach { p -> p.alt?.let { a -> val r0 = ref; if (r0 == null) ref = a else if (a - r0 > 2) { up += a - r0; ref = a } else if (r0 - a > 2) { down += r0 - a; ref = a } } }
        val hrs = pts.mapNotNull { it.hr }
        val kind = kindOf(s.type) ?: "RUN"
        val run = Run(
            id = -(1 + ((t0 / 1000) and 0x3FFFFFFFFFFFL)), // négatif et stable : la même séance garde son identifiant
            name = s.name?.takeIf { it.isNotBlank() } ?: Bridge.kindLabel(kind),
            kind = kind, app = s.app ?: when { file.lowercase().endsWith(".fit") -> "FIT"; file.lowercase().endsWith(".tcx") -> "TCX"; else -> "GPX" },
            start = t0, end = raw.last().ms,
            activeSec = s.timerSec?.takeIf { it > 0 } ?: moving,
            distanceM = s.distance?.takeIf { it > 0 } ?: cum.takeIf { it > 0 },
            kcal = s.kcal?.takeIf { it > 0 },
            avgHr = s.avgHr ?: hrs.takeIf { it.isNotEmpty() }?.average()?.roundToInt(),
            maxHr = s.maxHr ?: hrs.maxOrNull(),
            ascentM = s.ascent ?: up.takeIf { it > 0 }, descentM = s.descent ?: down.takeIf { it > 0 },
            avgCadence = s.cadence ?: pts.mapNotNull { it.cad }.takeIf { it.isNotEmpty() }?.average(),
            maxSpeed = s.maxSpeed,
        )
        val step = (pts.size + 3999) / 4000
        return run to if (step <= 1) pts else pts.filterIndexed { i, _ -> i % step == 0 || i == pts.lastIndex }
    }

    // ---- Lecture pour l'écran de séance -------------------------------------------------------------------

    fun detail(id: Long): WorkoutDetail? {
        val st = runCatching { json.decodeFromString(Stored.serializer(), File(dir, "$id.json").readText()) }.getOrNull() ?: return null
        val r = st.run
        val pts = st.points.map { TrackPoint(it.t, it.d, it.lat, it.lon, it.alt, it.hr, it.v, it.cad) }
        // tours au kilomètre, comme l'auto-lap des montres
        val laps = ArrayList<Lap>()
        var from = pts.firstOrNull()
        var k = 1
        pts.forEach { p ->
            val f = from ?: return@forEach
            if (p.d - f.d >= 1000) {
                val seg = pts.filter { it.t in f.t..p.t }
                laps += Lap(k++, p.d - f.d, p.t - f.t, seg.mapNotNull { it.hr }.takeIf { it.isNotEmpty() }?.average()?.toInt(), null)
                from = p
            }
        }
        val kind = when (r.kind) { "RUN" -> ActivityKind.RUNNING; "WALK" -> ActivityKind.WALKING; "HIKE" -> ActivityKind.HIKING; "BIKE" -> ActivityKind.CYCLING
            "SWIM" -> ActivityKind.SWIMMING; "YOGA" -> ActivityKind.YOGA; "STRENGTH" -> ActivityKind.STRENGTH_TRAINING; else -> ActivityKind.ACTIVITY }
        return WorkoutDetail(
            id = r.id, name = r.name, kind = kind, start = r.start, end = r.end, deviceName = r.app, activeSec = r.activeSec,
            distanceM = r.distanceM, kcal = r.kcal, avgHr = r.avgHr, maxHr = r.maxHr, ascentM = r.ascentM, descentM = r.descentM,
            avgCadence = r.avgCadence, maxSpeedMps = r.maxSpeed, steps = null, aerobicTe = null, anaerobicTe = null, recoverySec = null,
            poolLengthM = null, swolf = null, strokes = null, predictions = emptyList(), zoneBounds = null, points = pts, laps = laps,
        )
    }

    private fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val dLat = (lat2 - lat1) * PI / 180
        val dLon = (lon2 - lon1) * PI / 180
        val a = sin(dLat / 2).let { it * it } + cos(lat1 * PI / 180) * cos(lat2 * PI / 180) * sin(dLon / 2).let { it * it }
        return 2 * r * atan2(sqrt(a), sqrt(1 - a))
    }

    private class NonClosing(private val s: InputStream) : InputStream() {
        override fun read() = s.read()
        override fun read(b: ByteArray, off: Int, len: Int) = s.read(b, off, len)
        override fun close() {}
    }

    private fun displayName(ctx: Context, uri: Uri): String? = runCatching {
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }.getOrNull()
}
