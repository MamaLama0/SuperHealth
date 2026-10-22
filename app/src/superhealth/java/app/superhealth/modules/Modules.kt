package app.superhealth.modules

import android.content.Context
import app.superhealth.SuperPrefs
import app.superhealth.module
import app.superhealth.nutrition.data.Net
import app.superhealth.ui.tr
import app.superhealth.ui.trf
import java.io.File
import java.net.URI
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Modules téléchargeables. Le moteur de chaque module est dans l'app ; le module apporte son contenu (images pixel-art,
 * scénario, boss, consignes du coach…) depuis la branche « modules » du dépôt GitHub, ou d'une source ajoutée à la main.
 *
 * Un module ne contient jamais de code : seulement des données (JSON, PNG) lues par le moteur du même [Module.kind].
 * C'est ce qui rend acceptables les sources non vérifiées. Chaque archive est vérifiée (SHA-256 de l'index) avant d'être
 * décompressée dans files/modules/<id>/.
 */
object Modules {
    /** Index officiel : branche « modules » du dépôt, dossier dist. */
    const val OFFICIAL = "https://raw.githubusercontent.com/Ouinon0/SuperHealth/modules/dist/index.json"

    /** Moteurs présents dans cette version de l'app (un module d'un autre type demande une mise à jour). */
    const val ADVENTURE = "adventure"
    const val COACH = "coach"
    val engines = setOf(ADVENTURE, COACH)

    @Serializable
    data class Module(
        val id: String,
        val kind: String,
        val name: String,
        val summary: String = "",
        val nameEn: String? = null,
        val summaryEn: String? = null,
        val version: String,
        val size: Long = 0,
        val sha256: String,
        val url: String,
        /** Version de l'app nécessaire (moteur à jour). */
        val minApp: String = "0",
        /** Couleur d'accent de la fiche (#RRGGBB). */
        val color: String? = null,
    )

    @Serializable
    data class Index(val name: String = "", val modules: List<Module> = emptyList())

    /** Module disponible dans une source, avec l'adresse absolue de son archive. */
    data class Entry(val module: Module, val source: String, val verified: Boolean, val archive: String)

    @Serializable
    data class Installed(
        val id: String,
        val kind: String,
        val name: String,
        val version: String,
        val source: String,
        val verified: Boolean,
        val enabled: Boolean = true,
        val installedAt: Long = 0,
    )

    sealed interface Job {
        data class Downloading(val fraction: Float) : Job
        data object Installing : Job
        data object Done : Job
        data class Failed(val message: String) : Job
    }

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val http by lazy { OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build() }
    private const val MAX_ARCHIVE = 24L * 1024 * 1024
    private const val MAX_UNZIPPED = 48L * 1024 * 1024

    private lateinit var root: File
    private val _installed = MutableStateFlow<List<Installed>>(emptyList())
    val installed: StateFlow<List<Installed>> = _installed

    /** Catalogue lu (toutes sources) et erreur par source. */
    val catalog = MutableStateFlow<List<Entry>>(emptyList())
    val sourceErrors = MutableStateFlow<Map<String, String>>(emptyMap())
    val loading = MutableStateFlow(false)
    /** Téléchargement en cours ou terminé, par id de module (pour l'animation de la fiche). */
    val jobs = MutableStateFlow<Map<String, Job>>(emptyMap())

    fun init(ctx: Context) {
        root = File(ctx.filesDir, "modules").apply { mkdirs() }
        _installed.value = runCatching { json.decodeFromString(ListSerializer(Installed.serializer()), File(root, "installed.json").readText()) }
            .getOrDefault(emptyList()).filter { File(root, it.id).isDirectory }
    }

    private fun save(list: List<Installed>) {
        _installed.value = list
        File(root, "installed.json").writeText(json.encodeToString(ListSerializer(Installed.serializer()), list))
    }

    /** Module du type [kind] installé et activé (le premier s'il y en a plusieurs : un seul est activé à la fois). */
    fun active(kind: String): Installed? = _installed.value.firstOrNull { it.kind == kind && it.enabled }
    fun isActive(kind: String) = active(kind) != null

    /** Dossier du module actif du type [kind] (contenu décompressé), ou null. */
    fun dir(kind: String): File? = active(kind)?.let { File(root, it.id) }?.takeIf { it.isDirectory }

    fun file(kind: String, name: String): File? = dir(kind)?.let { File(it, name) }?.takeIf { it.isFile }

    fun sources(): List<Pair<String, Boolean>> = listOf(OFFICIAL to true) + SuperPrefs.settings.value.moduleSources.map { it to false }

    /** Une adresse acceptée pour une source ou une archive : HTTPS, ou HTTP vers le réseau local (tests). */
    fun acceptable(url: String): Boolean = Net.canSendSecret(url)

    /** Lit toutes les sources ; une source en erreur n'empêche pas les autres. */
    suspend fun refresh() = withContext(Dispatchers.IO) {
        loading.value = true
        val errors = HashMap<String, String>()
        val out = ArrayList<Entry>()
        for ((src, verified) in sources()) {
            runCatching {
                if (!acceptable(src)) error(tr("adresse refusée (HTTPS requis)"))
                val idx = http.newCall(Request.Builder().url(src).header("Cache-Control", "no-cache").build()).execute().use { r ->
                    if (!r.isSuccessful) error("HTTP ${r.code}")
                    json.decodeFromString(Index.serializer(), Net.readCapped(r.body.byteStream(), 512 * 1024))
                }
                idx.modules.filter { it.id.matches(Regex("[a-z0-9_-]{1,40}")) }.forEach { m ->
                    out += Entry(m, src, verified, URI(src).resolve(m.url).toString())
                }
            }.onFailure { errors[src] = it.message ?: "?" }
        }
        // un module vérifié l'emporte sur une copie du même id venue d'une source non vérifiée
        catalog.value = out.groupBy { it.module.id }.map { (_, l) -> l.firstOrNull { it.verified } ?: l.first() }
        sourceErrors.value = errors
        loading.value = false
    }

    fun newer(remote: String, local: String) = app.superhealth.update.Updater.newer(remote, local)

    /** Mise à jour disponible pour un module installé. */
    fun update(i: Installed): Entry? = catalog.value.firstOrNull { it.module.id == i.id && it.source == i.source && newer(it.module.version, i.version) }

    fun compatible(ctx: Context, m: Module) = m.kind in engines && !newer(m.minApp, app.superhealth.update.Updater.installed(ctx))

    private fun setJob(id: String, j: Job?) { jobs.value = if (j == null) jobs.value - id else jobs.value + (id to j) }

    /** Télécharge, vérifie et installe (ou met à jour) [e] ; la progression est dans [jobs]. */
    suspend fun install(ctx: Context, e: Entry): Boolean = withContext(Dispatchers.IO) {
        val m = e.module
        val started = System.currentTimeMillis()
        setJob(m.id, Job.Downloading(0f))
        runCatching {
            if (!compatible(ctx, m)) error(tr("mettez d'abord SuperHealth à jour"))
            if (!acceptable(e.archive)) error(tr("adresse refusée (HTTPS requis)"))
            val tmp = File(ctx.cacheDir, "module-${m.id}.zip")
            val digest = MessageDigest.getInstance("SHA-256")
            http.newCall(Request.Builder().url(e.archive).build()).execute().use { r ->
                if (!r.isSuccessful) error(trf("téléchargement refusé (%1\$s)", r.code))
                val total = r.body.contentLength().takeIf { it > 0 } ?: m.size
                var done = 0L
                r.body.byteStream().use { input ->
                    tmp.outputStream().use { o ->
                        val buf = ByteArray(32 * 1024)
                        while (true) {
                            val n = input.read(buf); if (n < 0) break
                            done += n
                            if (done > MAX_ARCHIVE) error(tr("archive trop volumineuse"))
                            o.write(buf, 0, n); digest.update(buf, 0, n)
                            if (total > 0) setJob(m.id, Job.Downloading((done.toFloat() / total).coerceIn(0f, 1f)))
                        }
                    }
                }
            }
            val sha = digest.digest().joinToString("") { "%02x".format(it) }
            if (!sha.equals(m.sha256.trim(), ignoreCase = true)) { tmp.delete(); error(tr("archive altérée (empreinte différente)")) }
            setJob(m.id, Job.Installing)
            val staging = File(root, ".${m.id}.new").apply { deleteRecursively(); mkdirs() }
            unzip(tmp, staging)
            tmp.delete()
            val target = File(root, m.id)
            target.deleteRecursively()
            if (!staging.renameTo(target)) error(tr("installation impossible"))
            val prev = _installed.value.firstOrNull { it.id == m.id }
            val others = _installed.value.filter { it.id != m.id }
                // un seul module actif par type : le nouveau prend la place
                .map { if (it.kind == m.kind) it.copy(enabled = false) else it }
            save(others + Installed(m.id, m.kind, m.name, m.version, e.source, e.verified, enabled = prev?.enabled ?: true,
                installedAt = prev?.installedAt ?: System.currentTimeMillis()))
            onChanged(ctx, m.kind)
            // l'animation de téléchargement a le temps de se voir, même pour une petite archive
            kotlinx.coroutines.delay((1600 - (System.currentTimeMillis() - started)).coerceAtLeast(0))
        }.fold({ setJob(m.id, Job.Done); true }, { setJob(m.id, Job.Failed(it.message ?: "?")); false })
    }

    /** Décompression sûre : pas de chemin qui sorte du dossier, pas d'archive qui gonfle sans limite. */
    private fun unzip(zip: File, into: File) {
        val base = into.canonicalPath + File.separator
        var total = 0L
        ZipInputStream(zip.inputStream().buffered()).use { z ->
            while (true) {
                val entry = z.nextEntry ?: break
                val out = File(into, entry.name).canonicalFile
                if (!out.path.startsWith(base)) error(tr("archive invalide"))
                if (entry.isDirectory) { out.mkdirs(); continue }
                out.parentFile?.mkdirs()
                out.outputStream().use { o ->
                    val buf = ByteArray(32 * 1024)
                    while (true) {
                        val n = z.read(buf); if (n < 0) break
                        total += n
                        if (total > MAX_UNZIPPED) error(tr("archive trop volumineuse"))
                        o.write(buf, 0, n)
                    }
                }
            }
        }
    }

    fun uninstall(ctx: Context, id: String) {
        val m = _installed.value.firstOrNull { it.id == id } ?: return
        File(root, id).deleteRecursively()
        save(_installed.value.filter { it.id != id })
        setJob(id, null)
        onChanged(ctx, m.kind)
    }

    fun setEnabled(ctx: Context, id: String, on: Boolean) {
        val m = _installed.value.firstOrNull { it.id == id } ?: return
        save(_installed.value.map {
            when {
                it.id == id -> it.copy(enabled = on)
                on && it.kind == m.kind -> it.copy(enabled = false)
                else -> it
            }
        })
        onChanged(ctx, m.kind)
    }

    /**
     * Effets d'un changement : cartes du tableau de bord, icône pixel-art, caches des moteurs. L'aventure commence avec les
     * 7 derniers jours : la première visite montre déjà les coups de la semaine.
     */
    private fun onChanged(ctx: Context, kind: String) {
        val on = isActive(kind)
        // module retiré ou désactivé : ses cartes quittent le tableau de bord (elles reviennent à la réactivation)
        if (!on) SuperPrefs.update { s -> s.copy(dashboard = s.dashboard.filter { it.module != kind }) }
        when (kind) {
            ADVENTURE -> {
                app.superhealth.adventure.AdventureArt.invalidate()
                if (on) SuperPrefs.update { s ->
                    s.copy(adventureStart = s.adventureStart.takeIf { it > 0 } ?: java.time.LocalDate.now().minusDays(7).toEpochDay(),
                        dashboard = withCards(s.dashboard, app.superhealth.DashCard.PROGRESS, app.superhealth.DashCard.ADVENTURE))
                }
                LauncherIcon.apply(ctx)
            }
            COACH -> {
                app.superhealth.coach.Coach.invalidate()
                if (on) SuperPrefs.update { s ->
                    s.copy(dashboard = if (app.superhealth.DashCard.COACH in s.dashboard) s.dashboard else listOf(app.superhealth.DashCard.COACH) + s.dashboard,
                        plus = if ("COACH" in s.plus) s.plus else s.plus + "COACH")
                }
            }
        }
    }

    private fun withCards(cards: List<app.superhealth.DashCard>, after: app.superhealth.DashCard, add: app.superhealth.DashCard): List<app.superhealth.DashCard> {
        if (add in cards) return cards
        val i = cards.indexOf(after)
        return cards.toMutableList().also { if (i >= 0) it.add(i + 1, add) else it.add(add) }
    }

    /** Mises à jour automatiques (vérification quotidienne) : modules installés dont une version plus récente est publiée. */
    suspend fun autoUpdate(ctx: Context) {
        if (!SuperPrefs.settings.value.modulesAuto || _installed.value.isEmpty()) return
        refresh()
        _installed.value.mapNotNull { update(it) }.filter { compatible(ctx, it.module) }.forEach { install(ctx, it) }
    }

    /**
     * Passage à la 1.2 : les succès (activés jusqu'ici dans l'app) font partie du module Aventure, installé une fois
     * automatiquement s'ils étaient activés.
     */
    suspend fun migrate(ctx: Context) {
        val s = SuperPrefs.settings.value
        if (s.modulesMigrated) return
        if (s.achievements && !isActive(ADVENTURE)) {
            refresh()
            val e = catalog.value.firstOrNull { it.verified && it.module.kind == ADVENTURE } ?: return
            if (!install(ctx, e)) return
        }
        SuperPrefs.update { it.copy(modulesMigrated = true) }
    }
}
