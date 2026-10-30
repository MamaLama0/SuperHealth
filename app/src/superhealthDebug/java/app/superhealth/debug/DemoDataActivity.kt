package app.superhealth.debug

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.Vo2MaxRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Length
import androidx.health.connect.client.units.Percentage
import app.superhealth.SuperPrefs
import app.superhealth.bridge.DEMO_RECORD_PREFIX
import app.superhealth.bridge.HealthConnectHub
import app.superhealth.nutrition.NutriApp
import app.superhealth.nutrition.data.FoodEntryEntity
import app.superhealth.nutrition.data.WeighInEntity
import app.superhealth.training.domain.Deload
import app.superhealth.training.domain.Experience
import app.superhealth.training.domain.Generator
import app.superhealth.training.domain.Goal
import app.superhealth.training.domain.Periodization
import app.superhealth.training.domain.Split
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Version de test seulement : 30 jours de données réalistes pour essayer les cartes sur un téléphone sans montre.
 * Écrit dans Health Connect (pas, pouls, pouls au repos, VFC, sommeil avec phases, SpO₂, respiration, VO₂ max, courses et
 * vélo) au nom de l'app, avec un identifiant « shdemo- » que la lecture accepte ; ajoute des pesées et des repas au journal,
 * puis active Health Connect et lance la synchronisation. Relancer remplace les mêmes enregistrements (mêmes identifiants).
 * Permissions d'écriture : `adb shell pm grant app.superhealth android.permission.health.WRITE_…` (voir le manifeste debug).
 * Lancer : `adb shell am start -n app.superhealth/app.superhealth.debug.DemoDataActivity` (`--es cards "VITALS:L,…"` : disposition seule).
 */
class DemoDataActivity : Activity() {
    private lateinit var log: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        log = TextView(this).apply { setPadding(48, 160, 48, 48); textSize = 16f }
        setContentView(log)
        // téléphone personnel (vraies données) : aucune écriture de démo, seul le nettoyage reste permis
        if (android.os.Build.MODEL == "Pixel 10 Pro XL" && !intent.hasExtra("cleanFrom")) {
            log.text = "Refusé : téléphone personnel, pas de données de démo ici."
            return
        }
        // `--es theme LIGHT|DARK|SYSTEM` : thème de l'app, pour vérifier les deux rendus
        intent.getStringExtra("theme")?.let { t ->
            val prefs = NutriApp.repository.prefs
            prefs.setSettings(prefs.settings.value.copy(themeMode = app.superhealth.nutrition.domain.ThemeMode.valueOf(t)))
            prefs.setSettings(prefs.settings.value.copy(textSize = app.superhealth.nutrition.domain.TextSize.valueOf(intent.getStringExtra("text") ?: "NORMAL")))
            SuperPrefs.update { it.copy(styleCardsLight = intent.getStringExtra("light") ?: "GRADIENT", styleCardsDark = intent.getStringExtra("dark") ?: "VIVID", styleWorkoutColor = "PROGRAM") }
            finish(); return
        }
        // `--es cards "VITALS:L,HEART:S"` : ne change que la disposition du tableau de bord (essai des tailles), sans réécrire les données
        intent.getStringExtra("cards")?.let { spec ->
            val pairs = spec.split(',').map { it.substringBefore(':') to it.substringAfter(':', "M") }
            SuperPrefs.update { s -> s.copy(dashboard = pairs.mapNotNull { (n, _) -> app.superhealth.DashCard.entries.firstOrNull { it.name == n } }, dashSizes = pairs.toMap()) }
            finish(); return
        }
        // `--el cleanFrom <ms> --el cleanTo <ms>` : retire ce que le générateur a écrit lors d'un passage précis (voir clean)
        if (intent.hasExtra("cleanFrom")) {
            CoroutineScope(Dispatchers.Main).launch {
                val out = runCatching { withContext(Dispatchers.IO) { clean(intent.getLongExtra("cleanFrom", 0), intent.getLongExtra("cleanTo", 0)) } }
                    .fold({ it }, { "Erreur : $it" })
                android.util.Log.i("SHCLEAN", out)
                log.text = out
            }
            return
        }
        // `--ez program true` : programme de musculation de démo (générateur de l'app), semaine 3, deux séances faites
        if (intent.getBooleanExtra("program", false)) {
            CoroutineScope(Dispatchers.Main).launch {
                log.text = runCatching { withContext(Dispatchers.Default) { demoProgram() } }.fold({ it }, { "Erreur : ${it.message}" })
            }
            return
        }
        CoroutineScope(Dispatchers.Main).launch {
            val out = runCatching { withContext(Dispatchers.IO) { generate() } }.fold({ it }, { "Erreur : ${it.message}" })
            log.text = out
        }
    }

    private suspend fun demoProgram(): String {
        val db = app.superhealth.training.WorkoutApp.db.first { it != null }!!
        val all = db.equipment.map { it.id }.toSet()
        val st = app.superhealth.training.WorkoutApp.store
        val gym = st.state.value.gyms.firstOrNull() ?: app.superhealth.training.domain.Gym(java.util.UUID.randomUUID().toString(), "Salle", 0, all)
        val p0 = Generator.generate(db, Generator.Input(goal = Goal.HYPERTROPHY, daysPerWeek = 4, split = Split.PPL_ACCESSORIES, minutes = 55,
            periodization = Periodization.LINEAR, deload = Deload.LAST, experience = Experience.INTERMEDIATE, equipment = gym.equipmentIds,
            name = "Push Pull Legs", gymId = gym.id))
        val train = p0.days.withIndex().filter { !it.value.isRest }
        val p = p0.copy(cycleIndex = 2, dayIndex = train.getOrNull(2)?.index ?: 0, doneDayIds = train.take(2).map { it.value.id })
        st.update { s -> s.copy(gyms = if (s.gyms.any { it.id == gym.id }) s.gyms else s.gyms + gym, programs = s.programs.filter { it.name != p.name } + p,
            settings = s.settings.copy(activeProgramId = p.id, activeGymId = s.settings.activeGymId ?: gym.id)) }
        return "Programme « ${p.name} » actif : ${train.size} séances par semaine, semaine 3, 2 faites."
    }

    private fun say(s: String) = runOnUiThread { log.append("$s\n") }

    private suspend fun generate(): String {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val now = ZonedDateTime.now(zone)
        val rnd = Random(42)
        val watch = Device(type = Device.TYPE_WATCH, manufacturer = "Démo", model = "Montre")
        fun meta(id: String) = Metadata.autoRecorded(watch, "$DEMO_RECORD_PREFIX$id", 1)
        fun at(d: LocalDate, minute: Int): ZonedDateTime = d.atStartOfDay(zone).plusMinutes(minute.toLong())
        val records = mutableListOf<Record>()
        // jours de sport : course lun/mer/sam, vélo jeu
        fun sport(d: LocalDate) = when (d.dayOfWeek.value) { 1, 3, 6 -> "run"; 4 -> "bike"; else -> null }

        for (k in 30 downTo 0) {
            val d = today.minusDays(k.toLong())
            val isToday = k == 0
            val trend = (30 - k) / 30.0
            // --- nuit (finie le matin de d) : coucher 23:00–00:10, lever 6:40–7:30, cycles de 90 min
            val bed = at(d.minusDays(1), 23 * 60 + rnd.nextInt(-30, 40))
            val wake = at(d, 6 * 60 + 40 + rnd.nextInt(0, 50))
            val stages = mutableListOf<SleepSessionRecord.Stage>()
            var t = bed
            var cycle = 0
            while (t.isBefore(wake)) {
                val seq = listOf(
                    SleepSessionRecord.STAGE_TYPE_LIGHT to 20 + rnd.nextInt(15),
                    SleepSessionRecord.STAGE_TYPE_DEEP to (if (cycle < 2) 35 else 12) + rnd.nextInt(10),
                    SleepSessionRecord.STAGE_TYPE_LIGHT to 15 + rnd.nextInt(15),
                    SleepSessionRecord.STAGE_TYPE_REM to (if (cycle < 2) 12 else 28) + rnd.nextInt(10),
                    SleepSessionRecord.STAGE_TYPE_AWAKE to (if (rnd.nextInt(3) == 0) 4 + rnd.nextInt(5) else 0),
                )
                for ((type, min) in seq) {
                    if (min == 0 || !t.isBefore(wake)) continue
                    val end = minOf(t.plusMinutes(min.toLong()).toEpochSecond(), wake.toEpochSecond()).let { Instant.ofEpochSecond(it) }
                    stages += SleepSessionRecord.Stage(t.toInstant(), end, type)
                    t = end.atZone(zone)
                }
                cycle++
            }
            records += SleepSessionRecord(bed.toInstant(), bed.offset, wake.toInstant(), wake.offset, metadata = meta("sleep-$d"),
                title = null, notes = null, stages = stages)
            records += OxygenSaturationRecord(wake.toInstant().minusSeconds(3600), wake.offset, Percentage(95.0 + rnd.nextDouble(3.0)), meta("spo2-$d"))
            records += RespiratoryRateRecord(wake.toInstant().minusSeconds(3600), wake.offset, 13.0 + rnd.nextDouble(2.0), meta("resp-$d"))
            // --- pouls au repos et VFC : récupération qui s'améliore doucement, creux après les grosses séances
            val hard = sport(d.minusDays(1)) == "run"
            val rhr = 57 - 3 * trend + rnd.nextDouble(-1.2, 1.2) + (if (hard) 1.5 else 0.0)
            val hrv = 56 + 8 * trend + rnd.nextDouble(-4.0, 4.0) - (if (hard) 5 else 0)
            records += RestingHeartRateRecord(wake.toInstant(), wake.offset, rhr.roundToInt().toLong(), meta("rhr-$d"))
            records += HeartRateVariabilityRmssdRecord(wake.toInstant(), wake.offset, hrv, meta("hrv-$d"))
            if (k % 7 == 0) records += Vo2MaxRecord(time = wake.toInstant(), zoneOffset = wake.offset, metadata = meta("vo2-$d"), vo2MillilitersPerMinuteKilogram = 45.5 + 2 * trend, measurementMethod = Vo2MaxRecord.MEASUREMENT_METHOD_HEART_RATE_RATIO)

            // --- séance du jour (17:30)
            val kind = sport(d)
            val start = at(d, 17 * 60 + 30)
            val sportMin = if (kind == "run") 26 + rnd.nextInt(14) else 45 + rnd.nextInt(15)
            val doSport = kind != null && (!isToday || now.isAfter(start.plusMinutes(sportMin.toLong())))
            if (doSport) {
                val end = start.plusMinutes(sportMin.toLong())
                val km = if (kind == "run") sportMin / (5.3 + rnd.nextDouble(0.5)) else sportMin * 26.0 / 60
                records += ExerciseSessionRecord(start.toInstant(), start.offset, end.toInstant(), end.offset, meta("ex-$d"),
                    if (kind == "run") ExerciseSessionRecord.EXERCISE_TYPE_RUNNING else ExerciseSessionRecord.EXERCISE_TYPE_BIKING,
                    title = if (kind == "run") "Course" else "Vélo",
                    exerciseRoute = if (kind == "run") demoRoute(start.toInstant(), end.toInstant(), km, rnd) else null)
                records += DistanceRecord(start.toInstant(), start.offset, end.toInstant(), end.offset, Length.kilometers(km), meta("dist-$d"))
                records += ActiveCaloriesBurnedRecord(start.toInstant(), start.offset, end.toInstant(), end.offset,
                    Energy.kilocalories(sportMin * (if (kind == "run") 11.0 else 8.0)), meta("ex-kcal-$d"))
            }

            // --- pas par heure (7 h → 22 h), jusqu'à maintenant pour aujourd'hui
            val dayFactor = 0.7 + rnd.nextDouble(0.6)
            for (h in 7..22) {
                val hs = at(d, h * 60)
                val he = hs.plusHours(1)
                if (isToday && he.isAfter(now)) break
                val base = when (h) { 8, 12, 13, 18 -> 900; in 9..11, in 14..16 -> 380; 17 -> if (kind == "run") 3200 else 700; 19, 20 -> 450; else -> 180 }
                val n = (base * dayFactor * (0.6 + rnd.nextDouble(0.8))).roundToInt().toLong()
                if (n > 0) records += StepsRecord(hs.toInstant(), hs.offset, he.toInstant(), he.offset, n, meta("steps-$d-$h"))
            }

            // --- pouls toutes les 5 min, par tranches d'une heure (nuit basse, journée, effort pendant la séance)
            for (h in 0..23) {
                val hs = at(d, h * 60)
                if (isToday && hs.isAfter(now)) break
                val samples = (0 until 12).mapNotNull { i ->
                    val ts = hs.plusMinutes(5L * i)
                    if (isToday && ts.isAfter(now)) return@mapNotNull null
                    val asleep = ts.isBefore(wake) || ts.hour >= 23
                    val inSport = doSport && !ts.isBefore(start) && ts.isBefore(start.plusMinutes(sportMin.toLong()))
                    val bpm = when {
                        inSport -> (if (kind == "run") 148 else 128) + 10 * sin(i / 2.0) + rnd.nextDouble(-4.0, 4.0)
                        asleep -> rhr - 4 + rnd.nextDouble(-2.0, 3.0)
                        else -> rhr + 14 + 8 * sin(ts.hour / 3.0) + rnd.nextDouble(-5.0, 9.0)
                    }
                    HeartRateRecord.Sample(ts.toInstant(), bpm.roundToInt().toLong().coerceIn(40, 190))
                }
                if (samples.isNotEmpty()) records += HeartRateRecord(hs.toInstant(), hs.offset,
                    samples.last().time.plusSeconds(60), hs.offset, samples, meta("hr-$d-$h"))
            }
        }
        say("Health Connect : ${records.size} enregistrements…")
        val client = HealthConnectClient.getOrCreate(this)
        records.chunked(500).forEach { client.insertRecords(it) }
        say("Écrits.")

        // --- pesées (tendance 83,0 → 81,2 kg) et repas du journal
        val repo = NutriApp.repository
        repo.weighIns.upsertAll((30 downTo 0).filter { it % 3 != 1 }.map { k ->
            WeighInEntity(today.minusDays(k.toLong()).toEpochDay(), (81.2 + 1.8 * k / 30 + rnd.nextDouble(-0.35, 0.35)).let { (it * 10).roundToInt() / 10.0 })
        })
        val existing = repo.entries.all().map { it.epochDay }.toSet()
        // (nom, g, kcal, prot, lip, gluc, repas 0-3, minute)
        val menu = listOf(
            listOf(Triple("Œufs brouillés", 120.0, doubleArrayOf(182.0, 13.0, 14.0, 1.0)), Triple("Pain complet", 60.0, doubleArrayOf(148.0, 6.0, 2.0, 25.0)),
                Triple("Banane", 120.0, doubleArrayOf(105.0, 1.3, 0.4, 27.0))) to (0 to 490),
            listOf(Triple("Poulet rôti", 150.0, doubleArrayOf(285.0, 39.0, 14.0, 0.0)), Triple("Riz basmati", 180.0, doubleArrayOf(230.0, 5.0, 1.0, 50.0)),
                Triple("Brocolis", 120.0, doubleArrayOf(41.0, 3.0, 1.0, 8.0)), Triple("Avocat", 70.0, doubleArrayOf(120.0, 2.0, 11.0, 6.0))) to (1 to 760),
            listOf(Triple("Yaourt grec", 150.0, doubleArrayOf(146.0, 15.0, 7.0, 6.0)), Triple("Pomme", 180.0, doubleArrayOf(95.0, 0.5, 0.3, 25.0))) to (3 to 990),
            listOf(Triple("Saumon", 140.0, doubleArrayOf(290.0, 30.0, 18.0, 0.0)), Triple("Pâtes complètes", 200.0, doubleArrayOf(300.0, 12.0, 2.0, 58.0)),
                Triple("Haricots verts", 150.0, doubleArrayOf(47.0, 3.0, 0.3, 10.0)), Triple("Huile d'olive", 10.0, doubleArrayOf(90.0, 0.0, 10.0, 0.0))) to (2 to 1190),
        )
        var meals = 0
        for (k in 30 downTo 0) {
            val d = today.minusDays(k.toLong())
            if (d.toEpochDay() in existing) continue
            for ((foods, slot) in menu) {
                val (meal, minute) = slot
                if (k == 0 && minute > now.hour * 60 + now.minute) continue
                if (k > 0 && rnd.nextInt(10) == 0 && meal == 3) continue
                val f = 0.85 + rnd.nextDouble(0.35)
                foods.forEach { (name, g, m) ->
                    repo.entries.insert(FoodEntryEntity(epochDay = d.toEpochDay(), meal = meal, name = name, grams = (g * f).roundToInt().toDouble(),
                        kcal = m[0] * f, protein = m[1] * f, fat = m[2] * f, carbs = m[3] * f, minuteOfDay = minute + rnd.nextInt(-20, 20)))
                }
                meals++
            }
        }
        say("Journal : $meals repas, pesées sur 30 jours.")
        // humeur du matin sur 3 semaines : la Forme a de quoi conclure
        (20L downTo 0L).forEach { k -> app.superhealth.wellbeing.Journal.setMood(this, if (k == 0L) 4 else 3 + rnd.nextInt(2), today.minusDays(k).toEpochDay()) }

        // tableau de bord de démo : chaque taille représentée (grand, moyen + petit, trois petits, bandeaux)
        val layout = listOf("SCORES" to "L", "SLEEP" to "M", "CALORIES" to "S", "STEPS" to "S", "HEART" to "S", "WEIGHT" to "S",
            "NEXT_WORKOUT" to "W", "HRV_STATUS" to "S", "TRAINING_WEEK" to "M", "RUNNING" to "W", "MACROS" to "M", "CHARGE" to "S",
            "STEPS" to "S", "SLEEP_STAGES" to "L", "WEIGHT" to "M", "EXPENDITURE" to "S", "HEART" to "W", "CALORIES" to "L")
        val first = layout.distinctBy { it.first }
        SuperPrefs.update { s ->
            s.copy(healthConnect = true, dashboard = first.mapNotNull { (n, _) -> app.superhealth.DashCard.entries.firstOrNull { it.name == n } },
                dashSizes = first.toMap())
        }
        val r = HealthConnectHub.sync(this)
        return "Données de démo prêtes.\n${r.summary()}\nRetournez dans SuperHealth (tirer pour actualiser)."
    }

    /** Boucle de course autour du parc (forme lobée, un point toutes les 10 s), longue d'environ [km]. */
    private fun demoRoute(from: java.time.Instant, to: java.time.Instant, km: Double, rnd: Random): androidx.health.connect.client.records.ExerciseRoute {
        val n = ((to.epochSecond - from.epochSecond) / 10).toInt().coerceAtLeast(20)
        val lat0 = 48.8462 + rnd.nextDouble(-0.004, 0.004); val lon0 = 2.3371 + rnd.nextDouble(-0.004, 0.004)
        val phase = rnd.nextDouble(0.0, 6.28)
        // rayon moyen tel que le tour fasse à peu près la distance (périmètre ≈ 2πR, un peu plus avec les lobes)
        val r = km / (2 * Math.PI * 1.08) / 111.0
        val pts = (0 until n).map { i ->
            val t = 2 * Math.PI * 0.94 * i / (n - 1)
            val rr = r * (1 + 0.22 * kotlin.math.sin(3 * t + phase) + 0.12 * kotlin.math.cos(2 * t))
            androidx.health.connect.client.records.ExerciseRoute.Location(minOf(from.plusSeconds(10L * i), to),
                lat0 + rr * kotlin.math.sin(t), lon0 + rr * kotlin.math.cos(t) / kotlin.math.cos(Math.toRadians(lat0)))
        }
        return androidx.health.connect.client.records.ExerciseRoute(pts)
    }

    /**
     * Retire les données de démo écrites sur un téléphone par erreur, sans rien d'autre : les repas de ce passage (créés dans
     * la fenêtre [from]..[to], sans aliment lié, noms du menu de démo), les pesées de démo des 31 derniers jours et leur copie
     * Health Connect, tous les enregistrements « shdemo- » de l'app. S'arrête sans rien supprimer si les comptes ne collent pas.
     * Relit ensuite Health Connect pour vider les caches.
     */
    private suspend fun clean(from: Long, to: Long): String {
        val menu = setOf("Œufs brouillés", "Pain complet", "Banane", "Poulet rôti", "Riz basmati", "Brocolis", "Avocat", "Yaourt grec", "Pomme",
            "Saumon", "Pâtes complètes", "Haricots verts", "Huile d'olive")
        val repo = NutriApp.repository
        val today = LocalDate.now()
        val all = repo.entries.all()
        val meals = all.filter { it.foodId == null && it.createdAt in from..to && it.name in menu }
        val mealDays = meals.map { it.epochDay }.toSet()
        val real = all.filter { it !in meals }.map { it.epochDay }.toSet()
        val demoDays = (0L..30L).filter { it % 3 != 1L }.map { today.minusDays(it).toEpochDay() }.toSet()
        val weighs = repo.weighIns.all().filter { it.epochDay in demoDays && it.weightKg in 80.8..83.4 }
        if (meals.size != 296 || mealDays.any { it in real } || weighs.size != 21) return "Arrêt, rien supprimé : ${meals.size} repas, ${weighs.size} pesées"

        val client = HealthConnectClient.getOrCreate(this)
        val range = androidx.health.connect.client.time.TimeRangeFilter.between(today.minusDays(40).atStartOfDay(ZoneId.systemDefault()).toInstant(),
            today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant())
        val origin = setOf(androidx.health.connect.client.records.metadata.DataOrigin(packageName))
        suspend fun <T : androidx.health.connect.client.records.Record> ids(type: kotlin.reflect.KClass<T>): List<String> {
            val out = mutableListOf<String>(); var token: String? = null
            do {
                val r = client.readRecords(androidx.health.connect.client.request.ReadRecordsRequest(type, range, origin, pageSize = 1000, pageToken = token))
                r.records.filter { it.metadata.clientRecordId?.startsWith(DEMO_RECORD_PREFIX) == true }.forEach { out += it.metadata.id }
                token = r.pageToken
            } while (token != null)
            return out
        }
        suspend fun <T : androidx.health.connect.client.records.Record> purge(type: kotlin.reflect.KClass<T>): String {
            val l = ids(type)
            l.chunked(1000).forEach { client.deleteRecords(type, it, emptyList()) }
            return "${type.simpleName} ${l.size}→${ids(type).size}"
        }
        val hc = listOf(StepsRecord::class, HeartRateRecord::class, RestingHeartRateRecord::class, HeartRateVariabilityRmssdRecord::class, SleepSessionRecord::class,
            OxygenSaturationRecord::class, RespiratoryRateRecord::class, Vo2MaxRecord::class, ExerciseSessionRecord::class, DistanceRecord::class,
            ActiveCaloriesBurnedRecord::class).map { purge(it) }
        client.deleteRecords(androidx.health.connect.client.records.WeightRecord::class, emptyList(), weighs.map { "w-${it.epochDay}" })

        meals.forEach { repo.entries.delete(it.id) }
        weighs.forEach { repo.weighIns.delete(it.epochDay) }
        // caches de la lecture Health Connect : vidés puis relus (retire aussi les totaux nutritionnels des jours redevenus vides)
        SuperPrefs.setHc(app.superhealth.HcCache())
        java.io.File(filesDir, "hc-heart").listFiles()?.forEach { it.delete() }
        val sync = HealthConnectHub.sync(this)
        return "Retiré : ${meals.size} repas, ${weighs.size} pesées\n${hc.joinToString("\n")}\nSynchro : ${sync.summary()}"
    }
}
