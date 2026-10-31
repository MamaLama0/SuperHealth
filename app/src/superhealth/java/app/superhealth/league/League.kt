package app.superhealth.league

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import app.superhealth.Feature
import app.superhealth.SuperPrefs
import app.superhealth.bridge.Bridge
import app.superhealth.has
import app.superhealth.health.data.Metric
import app.superhealth.meditation.Meditation
import app.superhealth.modules.Modules
import app.superhealth.nutrition.NutriApp
import app.superhealth.stretch.Stretch
import app.superhealth.training.WorkoutApp
import java.io.File
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.IsoFields
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Plugin Classement : un score hebdomadaire comparé entre amis, sans serveur.
 *
 * Score : six parties, chacune entre 0 et 100 % de l'objectif personnel de la semaine (lundi à dimanche). Les parties sans source
 * (pas de montre, nutrition coupée…) sortent du calcul : personne n'est pénalisé pour ce qu'il ne suit pas. Total sur 1000.
 *
 * Partage : une carte signée (clé EC P-256 du Keystore, jamais exportable) passe par un QR code ou un message. L'app n'ouvre aucun port
 * et ne contacte personne. Un inconnu n'est ajouté qu'après confirmation (empreinte à comparer) ; ensuite seule sa clé peut mettre à
 * jour sa ligne, avec une carte plus récente. Limite assumée : sans serveur, rien n'empêche quelqu'un de modifier sa propre app pour
 * annoncer un faux score ; la signature protège l'identité, pas la sincérité.
 */
object League {
    enum class Pillar(val label: String) { STRENGTH("Musculation"), CARDIO("Cardio"), STEPS("Pas"), SLEEP("Sommeil"), FOOD("Nutrition"), MIND("Esprit et mobilité") }

    /** Partie : son taux de 0 à 100, null si elle ne compte pas pour cette personne. */
    data class Score(val week: String, val total: Int, val pillars: Map<Pillar, Int?>)

    @Serializable data class Card(val v: Int = 1, val k: String, val n: String, val w: String, val t: Long, val s: Int, val p: List<Int?> = emptyList(), val st: Int = 0)

    @Serializable data class Friend(val id: String, val key: String, val name: String, val week: String, val time: Long, val score: Int,
                                    val pillars: List<Int?> = emptyList(), val streak: Int = 0,
                                    /** Nom choisi par moi pour cet ami (le sien reste dans [name]) ; scores reçus par semaine ; date d'ajout. */
                                    val alias: String? = null, val history: Map<String, Int> = emptyMap(), val addedAt: Long = 0) {
        val shown get() = alias?.takeIf { it.isNotBlank() } ?: name
    }

    @Serializable data class State(val friends: List<Friend> = emptyList(), val best: Map<String, Int> = emptyMap(), val sharePillars: Boolean = true)

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private const val ALIAS = "superhealth_league"
    private const val PREFIX = "SH1"
    private const val MAX_CARD = 1500
    /** Semaine « tenue » pour la série : au moins la moitié du score. */
    private const val STREAK_MIN = 500

    fun available() = Modules.isActive(Modules.LEAGUE)
    val state = MutableStateFlow(State())
    val mine = MutableStateFlow<Score?>(null)
    /** Carte reçue par un lien ou un QR, en attente de confirmation. */
    val incoming = MutableStateFlow<Card?>(null)
    val incomingError = MutableStateFlow(false)

    private fun file(ctx: Context) = File(File(ctx.filesDir, "league").apply { mkdirs() }, "state.json")
    fun load(ctx: Context) { state.value = runCatching { json.decodeFromString(State.serializer(), file(ctx).readText()) }.getOrDefault(State()) }
    private fun save(ctx: Context, s: State) { state.value = s; runCatching { file(ctx).writeText(json.encodeToString(State.serializer(), s)) } }
    fun setSharePillars(ctx: Context, on: Boolean) = save(ctx, state.value.copy(sharePillars = on))
    fun remove(ctx: Context, id: String) = save(ctx, state.value.copy(friends = state.value.friends.filter { it.id != id }))

    fun weekKey(d: LocalDate = LocalDate.now()) = "%d-W%02d".format(d.get(IsoFields.WEEK_BASED_YEAR), d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR))

    // ---- Score ------------------------------------------------------------------------------------------

    /** Taux d'une partie par rapport à un objectif atteignable une fois dans la semaine. */
    private fun pct(v: Double, target: Double) = ((v / target).coerceIn(0.0, 1.0) * 100).toInt()

    suspend fun compute(ctx: Context, today: LocalDate = LocalDate.now()): Score = withContext(Dispatchers.Default) {
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val days = generateSequence(monday) { it.plusDays(1) }.takeWhile { !it.isAfter(today) }.toList()
        val zone = ZoneId.systemDefault()
        val fromMs = monday.atStartOfDay(zone).toInstant().toEpochMilli()
        val toMs = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val sh = SuperPrefs.settings.value
        val p = linkedMapOf<Pillar, Int?>()

        // Musculation : séances faites face aux séances du programme actif (3 sans programme)
        p[Pillar.STRENGTH] = if (!sh.has(Feature.TRAINING)) null else {
            val st = WorkoutApp.store.state.value
            val planned = st.programs.firstOrNull { it.id == st.settings.activeProgramId }?.days?.count { !it.isRest }?.takeIf { it > 0 } ?: 3
            pct(st.history.count { it.startTime in fromMs until toMs }.toDouble(), planned.toDouble())
        }
        // Cardio : 150 minutes par semaine, repère de l'OMS
        p[Pillar.CARDIO] = if (!sh.has(Feature.HEALTH)) null else {
            val acts = runCatching { Bridge.activities(fromMs, toMs) }.getOrDefault(emptyList()).filter { it.source != Bridge.Source.GYM }
            pct(acts.sumOf { it.minutes }.toDouble(), 150.0)
        }
        // Pas et sommeil : chaque jour compte pour un septième, plafonné à l'objectif du jour
        p[Pillar.STEPS] = if (!sh.has(Feature.HEALTH) || !Bridge.hasSource(Metric.STEPS)) null else {
            val goal = NutriApp.repository.prefs.settings.first().stepGoal.coerceAtLeast(1000).toDouble()
            (days.sumOf { d -> ((runCatching { Bridge.healthValue(ctx, Metric.STEPS, d) }.getOrNull() ?: 0.0) / goal).coerceIn(0.0, 1.0) } / 7 * 100).toInt()
        }
        // Sommeil : 7 h par nuit, repère de l'AASM pour un adulte
        p[Pillar.SLEEP] = if (!sh.has(Feature.HEALTH) || !Bridge.hasSource(Metric.SLEEP)) null else
            (days.sumOf { d -> ((runCatching { Bridge.healthValue(ctx, Metric.SLEEP, d) }.getOrNull() ?: 0.0) / 420.0).coerceIn(0.0, 1.0) } / 7 * 100).toInt()
        // Nutrition : jours journalisés à ±10 % de la cible
        p[Pillar.FOOD] = if (!sh.has(Feature.NUTRITION)) null else {
            val repo = NutriApp.repository
            val an = repo.analytics().first()
            val eaten = repo.entries.rangeFlow(monday.toEpochDay(), today.toEpochDay()).first().groupBy { it.epochDay }
            val ok = days.count { d ->
                val kcal = eaten[d.toEpochDay()].orEmpty().sumOf { it.kcal }
                val target = an.targetFor(d.toEpochDay()).macros.kcal
                kcal > 0 && target > 0 && kotlin.math.abs(kcal / target - 1) <= 0.10
            }
            pct(ok.toDouble(), 7.0)
        }
        // Esprit et mobilité : 10 minutes par jour de méditation ou d'étirements
        p[Pillar.MIND] = if (!Meditation.available() && !Stretch.available()) null else
            pct((Meditation.minutes(monday.toEpochDay()) + Stretch.minutes(monday.toEpochDay())).toDouble(), 70.0)

        val counted = p.values.filterNotNull()
        val total = if (counted.isEmpty()) 0 else counted.sumOf { it * 10 } / counted.size
        val key = weekKey(today)
        val s = Score(key, total, p)
        mine.value = s
        if ((state.value.best[key] ?: -1) != total) save(ctx, state.value.copy(best = (state.value.best + (key to total)).entries.sortedBy { it.key }.takeLast(60).associate { it.key to it.value }))
        s
    }

    /** Semaines d'affilée au-dessus de la moitié, semaine en cours comprise quand elle y est déjà. */
    fun streak(today: LocalDate = LocalDate.now()): Int {
        val best = state.value.best
        var d = today
        if ((best[weekKey(d)] ?: 0) < STREAK_MIN) d = d.minusWeeks(1)
        var n = 0
        while ((best[weekKey(d)] ?: 0) >= STREAK_MIN) { n++; d = d.minusWeeks(1) }
        return n
    }

    // ---- Identité ---------------------------------------------------------------------------------------

    private fun keyStore() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun ensureKey() {
        if (keyStore().containsAlias(ALIAS)) return
        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore").apply {
            initialize(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1")).setDigests(KeyProperties.DIGEST_SHA256).build())
        }.generateKeyPair()
    }

    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    private fun unb64(s: String) = Base64.decode(s, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    private fun idOf(pubB64: String) = MessageDigest.getInstance("SHA-256").digest(unb64(pubB64)).take(8).joinToString("") { "%02x".format(it) }

    private fun myPublic(): String { ensureKey(); return b64(keyStore().getCertificate(ALIAS).publicKey.encoded) }

    /** Empreinte à comparer de vive voix : huit caractères tirés de la clé. */
    fun fingerprint(key: String) = idOf(key).take(8).uppercase().chunked(4).joinToString(" ")
    fun myFingerprint() = fingerprint(myPublic())

    // ---- Carte ------------------------------------------------------------------------------------------

    /** Carte signée de la semaine, prête à partager. */
    fun card(name: String, s: Score): String {
        val pub = myPublic()
        val c = Card(k = pub, n = clean(name).ifBlank { "?" }, w = s.week, t = System.currentTimeMillis() / 1000, s = s.total,
            p = if (state.value.sharePillars) Pillar.entries.map { s.pillars[it] } else emptyList(), st = streak())
        val body = json.encodeToString(Card.serializer(), c).toByteArray()
        val key = keyStore().getKey(ALIAS, null) as java.security.PrivateKey
        val sig = Signature.getInstance("SHA256withECDSA").run { initSign(key); update(body); sign() }
        return "$PREFIX.${b64(body)}.${b64(sig)}"
    }

    fun link(card: String) = "superhealth://league?c=$card"

    /** Texte d'un nom reçu : sans caractères de contrôle ni d'inversion de sens, 24 signes au plus. */
    private fun clean(s: String) = s.filter { !it.isISOControl() && Character.getType(it) != Character.FORMAT.toInt() }.trim().take(24)

    /** Lit une carte (brute, ou dans un lien ou un message) et vérifie sa signature ; null si elle n'est pas valable. */
    fun parse(raw: String): Card? = runCatching {
        val m = Regex("""$PREFIX\.([A-Za-z0-9_-]+)\.([A-Za-z0-9_-]+)""").find(raw.take(MAX_CARD * 2)) ?: return null
        if (m.value.length > MAX_CARD) return null
        val body = unb64(m.groupValues[1])
        val c = json.decodeFromString(Card.serializer(), body.decodeToString())
        val pub = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(unb64(c.k)))
        val ok = Signature.getInstance("SHA256withECDSA").run { initVerify(pub); update(body); verify(unb64(m.groupValues[2])) }
        if (!ok || c.v != 1 || c.s !in 0..1000 || c.st !in 0..1000 || c.p.size > Pillar.entries.size || c.p.any { it != null && it !in 0..100 }) return null
        if (!c.w.matches(Regex("""\d{4}-W\d{2}""")) || c.t > System.currentTimeMillis() / 1000 + 86_400) return null
        c.copy(n = clean(c.n).ifBlank { "?" })
    }.getOrNull()

    /** Carte reçue : un ami connu est mis à jour directement, un inconnu attend la confirmation. */
    fun receive(ctx: Context, raw: String): Boolean {
        val c = parse(raw)
        if (c == null) { incomingError.value = true; return false }
        if (c.k == runCatching { myPublic() }.getOrNull()) return true
        if (state.value.friends.any { it.key == c.k }) accept(ctx, c) else incoming.value = c
        return true
    }

    /** Applique une carte vérifiée ; une carte plus ancienne que celle déjà reçue est ignorée (rejeu). */
    fun accept(ctx: Context, c: Card) {
        val id = idOf(c.k)
        val old = state.value.friends.firstOrNull { it.id == id }
        if (old != null && c.t <= old.time) return
        val f = Friend(id, c.k, c.n, c.w, c.t, c.s, c.p, c.st, old?.alias, ((old?.history.orEmpty()) + (c.w to c.s)).entries.sortedBy { it.key }.takeLast(12).associate { it.key to it.value },
            old?.addedAt ?: System.currentTimeMillis())
        save(ctx, state.value.copy(friends = state.value.friends.filter { it.id != id } + f))
    }

    fun rename(ctx: Context, id: String, alias: String) =
        save(ctx, state.value.copy(friends = state.value.friends.map { if (it.id == id) it.copy(alias = clean(alias).ifBlank { null }) else it }))

    fun isFriend(key: String) = state.value.friends.any { it.key == key }

    /** Ma carte du moment : score recalculé. */
    suspend fun myCard(ctx: Context): String = card(app.superhealth.ui.userName() ?: "?", compute(ctx))
}
