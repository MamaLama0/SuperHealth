package app.superhealth.adventure

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import app.superhealth.SuperPrefs
import app.superhealth.bridge.Bridge
import app.superhealth.modules.Modules
import app.superhealth.training.WorkoutApp
import app.superhealth.training.domain.Analytics as TrainingAnalytics
import app.superhealth.ui.Lang
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.pow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Module Aventure : un petit RPG en pixel-art. Les boss se combattent avec ce qu'on fait vraiment — la fonte soulevée
 * (kg × répétitions des séries de travail) et les kilomètres courus (montre). Tout se recalcule depuis les données à partir
 * du jour où l'aventure a commencé : rien à tenir à jour, rien de perdu à la restauration.
 *
 * Le contenu (boss, héros, scénario, images) vient du module téléchargé (world.json, sprites.png) ; ce fichier n'est que le moteur.
 */
object Adventure {
    /** Texte bilingue du module (l'anglais retombe sur le français s'il manque). */
    @Serializable data class Txt(val fr: String, val en: String? = null) {
        val text: String get() = if (Lang.english) en ?: fr else fr
    }

    @Serializable data class Hero(val level: Int, val sprite: Int, val name: Txt)

    @Serializable data class Boss(val id: String, val name: Txt, val sprite: Int, val hp: Long, val loot: Int? = null, val lootName: Txt? = null)

    /** Scène du scénario : jouée une fois quand son déclencheur arrive (« start », « boss:<id> », « defeat:<id> », « level:<n> », « loop »). */
    @Serializable data class Scene(val at: String, val speaker: Txt? = null, val sprite: Int? = null, val lines: List<Txt>)

    @Serializable data class World(
        val title: Txt = Txt("Le Donjon"),
        val tile: Int = 16,
        val columns: Int = 12,
        /** Dégâts par kilo soulevé et par kilomètre couru. */
        val kgDamage: Double = 1.0,
        val kmDamage: Double = 1000.0,
        /** Chaque tour du donjon terminé multiplie les PV des boss. */
        val loopFactor: Double = 2.0,
        val heroes: List<Hero> = emptyList(),
        val bosses: List<Boss> = emptyList(),
        val story: List<Scene> = emptyList(),
        /** Tuiles du décor : mur, sol (variantes), porte. */
        val wall: List<Int> = listOf(40),
        val floor: List<Int> = listOf(48),
        val door: Int? = null,
    )

    /** Un coup porté : une séance ou une course. */
    data class Hit(val time: Long, val label: String, val kg: Double, val km: Double, val damage: Long) {
        val day: LocalDate get() = TrainingAnalytics.day(time)
    }

    data class Defeat(val boss: Boss, val round: Int, val time: Long)

    /** État du combat : boss courant (tour [round], 0 au premier passage), PV, boss vaincus, coups portés. */
    data class Battle(
        val world: World, val boss: Boss, val index: Int, val round: Int, val maxHp: Long, val hp: Long,
        val defeated: List<Defeat>, val total: Long, val hits: List<Hit>,
    ) {
        val fraction: Float get() = if (maxHp <= 0) 0f else hp.toFloat() / maxHp
        /** Équivalents de ce qu'il reste : en kilos soulevés ou en kilomètres courus. */
        val leftKg: Double get() = hp / world.kgDamage
        val leftKm: Double get() = hp / world.kmDamage
    }

    fun damage(w: World, kg: Double, km: Double): Long = (kg * w.kgDamage + km * w.kmDamage).toLong()

    fun hpOf(w: World, b: Boss, round: Int): Long = (b.hp * w.loopFactor.pow(round)).toLong()

    /** Combat à partir des coups dans l'ordre : chaque boss vaincu laisse place au suivant ; après le dernier, nouveau tour. */
    fun battle(w: World, hits: List<Hit>): Battle? {
        if (w.bosses.isEmpty()) return null
        var index = 0; var round = 0
        var hp = hpOf(w, w.bosses[0], 0)
        val defeated = ArrayList<Defeat>()
        for (h in hits.sortedBy { it.time }) {
            var d = h.damage
            while (d > 0) {
                if (d < hp) { hp -= d; d = 0 } else {
                    d -= hp
                    defeated += Defeat(w.bosses[index], round, h.time)
                    index++
                    if (index == w.bosses.size) { index = 0; round++ }
                    hp = hpOf(w, w.bosses[index], round)
                }
            }
        }
        val b = w.bosses[index]
        return Battle(w, b, index, round, hpOf(w, b, round), hp, defeated, hits.sumOf { it.damage }, hits.sortedByDescending { it.time })
    }

    /** Héros du niveau [level] (le dernier atteint) ; [chosen] : apparence choisie parmi celles débloquées. */
    fun hero(w: World, level: Int, chosen: Int = -1): Hero? {
        val unlocked = w.heroes.filter { it.level <= level }
        return unlocked.firstOrNull { it.sprite == chosen } ?: unlocked.maxByOrNull { it.level } ?: w.heroes.firstOrNull()
    }

    // ---- Données ---------------------------------------------------------------------------

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    @Volatile private var world: World? = null

    fun world(): World? = world ?: runCatching {
        json.decodeFromString(World.serializer(), Modules.file(Modules.ADVENTURE, "world.json")!!.readText())
    }.getOrNull()?.also { world = it }

    fun invalidate() { world = null; cached = null }

    private var cached: Pair<Long, Battle>? = null

    /** Séances (volume des séries de travail) et courses (distance de la montre) depuis le début de l'aventure. */
    suspend fun hits(w: World): List<Hit> {
        val start = SuperPrefs.settings.value.adventureStart.takeIf { it > 0 } ?: LocalDate.now().toEpochDay()
        val fromMs = LocalDate.ofEpochDay(start).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val gym = WorkoutApp.store.state.value.history.filter { it.startTime >= fromMs }.map { wo ->
            val kg = wo.exercises.sumOf { TrainingAnalytics.volume(it.sets) }
            Hit(wo.startTime, wo.name, kg, 0.0, damage(w, kg, 0.0))
        }.filter { it.damage > 0 }
        val runs = runCatching { Bridge.activities(fromMs) }.getOrDefault(emptyList())
            .filter { it.kind == Bridge.Kind.RUN && it.source != Bridge.Source.GYM }
            .mapNotNull { a -> ((a.watch?.distanceM ?: 0.0) / 1000).takeIf { it > 0 }?.let { km -> Hit(a.start, a.title, 0.0, km, damage(w, 0.0, km)) } }
        return gym + runs
    }

    suspend fun battle(force: Boolean = false): Battle? {
        val w = world() ?: return null
        val now = System.currentTimeMillis()
        cached?.takeIf { !force && now - it.first < 60_000L }?.let { return it.second }
        return battle(w, hits(w))?.also { cached = now to it }
    }

    // ---- Mémoire : dégâts déjà montrés, scènes déjà jouées -------------------------------

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("superhealth_adventure", Context.MODE_PRIVATE)

    /** Total de dégâts au dernier passage sur l'écran (pour animer seulement les nouveaux coups). */
    fun seenDamage(ctx: Context): Long = prefs(ctx).getLong("seenDamage", -1)
    fun markDamage(ctx: Context, total: Long) = prefs(ctx).edit().putLong("seenDamage", total).apply()

    fun seenScenes(ctx: Context): Set<String> = prefs(ctx).getStringSet("seenScenes", emptySet()).orEmpty()
    fun markScene(ctx: Context, at: String) = prefs(ctx).edit().putStringSet("seenScenes", seenScenes(ctx) + at).apply()

    /** Déclencheurs atteints, dans l'ordre du récit : début, chaque boss vaincu puis rencontré, niveaux. */
    fun triggers(b: Battle, level: Int): List<String> = buildList {
        add("start")
        add("boss:${b.world.bosses.first().id}")
        b.defeated.forEach { d ->
            add(if (d.round == 0) "defeat:${d.boss.id}" else "defeat:${d.boss.id}:${d.round}")
            val next = b.world.bosses.getOrNull(b.world.bosses.indexOf(d.boss) + 1)
            if (next != null && d.round == 0) add("boss:${next.id}")
            if (next == null) add("loop")
        }
        b.world.heroes.filter { it.level in 2..level }.forEach { add("level:${it.level}") }
    }

    /** Scènes à jouer maintenant (déclencheur atteint, pas encore vue). */
    fun pending(ctx: Context, b: Battle, level: Int): List<Scene> {
        val seen = seenScenes(ctx)
        val reached = triggers(b, level)
        return reached.filter { it !in seen }.mapNotNull { t -> b.world.story.firstOrNull { it.at == t } }
    }

    /** Toutes les scènes déjà vues, dans l'ordre (journal de quête). */
    fun journal(ctx: Context, b: Battle, level: Int): List<Scene> {
        val seen = seenScenes(ctx)
        return triggers(b, level).filter { it in seen }.mapNotNull { t -> b.world.story.firstOrNull { it.at == t } }
    }
}

/** Planche de sprites du module (16 × 16, sans lissage), chargée une fois. */
object AdventureArt {
    @Volatile private var sheet: ImageBitmap? = null

    fun sheet(): ImageBitmap? = sheet ?: runCatching {
        Modules.file(Modules.ADVENTURE, "sprites.png")!!.inputStream().use { BitmapFactory.decodeStream(it) }.asImageBitmap()
    }.getOrNull()?.also { sheet = it }

    fun invalidate() { sheet = null; Adventure.invalidate() }
}
