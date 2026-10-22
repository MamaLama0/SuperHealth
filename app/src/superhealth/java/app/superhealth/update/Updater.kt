package app.superhealth.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.superhealth.MainActivity
import app.superhealth.SuperPrefs
import app.superhealth.ui.tr
import app.superhealth.ui.trf
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import nodomain.freeyourgadget.gadgetbridge.R
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Mises à jour depuis les versions (releases) GitHub du dépôt choisi : une vérification par jour au plus, puis une
 * notification ; l'APK n'est téléchargé et installé qu'après confirmation (ou directement si l'option est désactivée,
 * Android demandant toujours de valider l'installation). Le dépôt est public : aucun compte ni jeton.
 */
object Updater {
    @Serializable data class Asset(val name: String, val browser_download_url: String, val url: String, val size: Long = 0)
    @Serializable data class GhRelease(val tag_name: String, val name: String? = null, val body: String? = null, val draft: Boolean = false,
                                       val prerelease: Boolean = false, val html_url: String? = null, val assets: List<Asset> = emptyList())

    data class Release(val version: String, val notes: String, val apk: Asset, val page: String?)

    sealed interface State {
        data object Idle : State
        data object Checking : State
        data class UpToDate(val version: String) : State
        data class Available(val release: Release) : State
        data class Downloading(val release: Release, val fraction: Float) : State
        data class Ready(val release: Release, val file: File) : State
        data class Failed(val message: String) : State
    }

    val state = MutableStateFlow<State>(State.Idle)

    private const val CHANNEL = "updates"
    private const val NOTIF = 4201
    private val json = Json { ignoreUnknownKeys = true }
    private val http by lazy { OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build() }

    fun installed(ctx: Context): String = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "0"

    /** « v1.2.3 », « 1.2 » → [1, 2, 3] ; comparaison composante par composante (1.10 après 1.9). */
    fun parts(v: String): List<Int> = v.trim().removePrefix("v").removePrefix("V").split('.', '-', '+').map { p -> p.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }

    fun newer(remote: String, local: String): Boolean {
        val a = parts(remote); val b = parts(local)
        for (i in 0 until maxOf(a.size, b.size)) {
            val d = a.getOrElse(i) { 0 } - b.getOrElse(i) { 0 }
            if (d != 0) return d > 0
        }
        return false
    }

    /** APK publié : le build « perf » (même signature que l'app installée), jamais la variante de débogage. */
    fun pickApk(assets: List<Asset>): Asset? = assets.filter { it.name.endsWith(".apk") && !it.name.contains("debug", true) }.maxByOrNull { it.size }

    private fun request(url: String, accept: String): Request = Request.Builder().url(url).header("Accept", accept)
        .header("X-GitHub-Api-Version", "2022-11-28").build()

    /** Dernière version publiée si elle est plus récente que celle installée (et pas ignorée), sinon null. */
    suspend fun check(ctx: Context): Release? = withContext(Dispatchers.IO) {
        val repo = SuperPrefs.settings.value.updateRepo.trim().trim('/')
        http.newCall(request("https://api.github.com/repos/$repo/releases/latest", "application/vnd.github+json")).execute().use { r ->
            SuperPrefs.update { it.copy(lastUpdateCheck = System.currentTimeMillis()) }
            when (r.code) {
                404 -> error(tr("aucune version publiée"))
                401, 403 -> error(trf("accès refusé par GitHub (%1\$s)", r.code))
            }
            if (!r.isSuccessful) error("GitHub ${r.code}")
            val gh = json.decodeFromString(GhRelease.serializer(), r.body.string())
            val apk = pickApk(gh.assets) ?: return@withContext null
            val v = gh.tag_name.removePrefix("v")
            if (gh.draft || !newer(v, installed(ctx))) null else Release(v, gh.body.orEmpty().trim(), apk, gh.html_url)
        }
    }

    /** Vérifie et met [state] à jour (écran des mises à jour). */
    suspend fun refresh(ctx: Context) {
        state.value = State.Checking
        state.value = runCatching { check(ctx) }.fold({ it?.let(State::Available) ?: State.UpToDate(installed(ctx)) }, { State.Failed(it.message ?: "?") })
    }

    /** Télécharge l'APK dans le cache (progression dans [state]). */
    suspend fun download(ctx: Context, rel: Release): File = withContext(Dispatchers.IO) {
        val dir = File(ctx.cacheDir, "updates").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val out = File(dir, "SuperHealth-${rel.version}.apk")
        http.newCall(Request.Builder().url(rel.apk.browser_download_url).build()).execute().use { r ->
            if (!r.isSuccessful) error(trf("téléchargement refusé (%1\$s)", r.code))
            val total = r.body.contentLength().takeIf { it > 0 } ?: rel.apk.size
            var done = 0L
            r.body.byteStream().use { input ->
                out.outputStream().use { o ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf); if (n < 0) break
                        o.write(buf, 0, n); done += n
                        if (total > 0) state.value = State.Downloading(rel, (done.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
        }
        state.value = State.Ready(rel, out)
        out
    }

    /** Installation par PackageInstaller ; Android demande la confirmation (voir [InstallReceiver]). */
    suspend fun install(ctx: Context, apk: File) = withContext(Dispatchers.IO) {
        val pi = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(ctx.packageName)
            if (android.os.Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = pi.createSession(params)
        pi.openSession(id).use { s ->
            s.openWrite("superhealth.apk", 0, apk.length()).use { o -> apk.inputStream().use { it.copyTo(o) }; s.fsync(o) }
            val intent = Intent(ctx, InstallReceiver::class.java)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (android.os.Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            s.commit(PendingIntent.getBroadcast(ctx, id, intent, flags).intentSender)
        }
    }

    fun canInstall(ctx: Context) = ctx.packageManager.canRequestPackageInstalls()

    // ---- Notification ---------------------------------------------------------------------

    fun notify(ctx: Context, rel: Release) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, tr("Mises à jour"), NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(ctx, NOTIF, Intent(ctx, MainActivity::class.java).putExtra(MainActivity.EXTRA_ROUTE, "update")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(ctx, CHANNEL).setSmallIcon(R.drawable.ic_stat_update)
            .setContentTitle(trf("SuperHealth %1\$s disponible", rel.version))
            .setContentText(rel.notes.lineSequence().firstOrNull { it.isNotBlank() }?.trimStart('#', '-', '*', ' ') ?: tr("Touchez pour installer"))
            .setContentIntent(open).setAutoCancel(true)
            .addAction(0, tr("Installer"), open)
            .build()
        runCatching { nm.notify(NOTIF, n) }
    }

    fun cancelNotification(ctx: Context) = ctx.getSystemService(NotificationManager::class.java).cancel(NOTIF)
}

/** Vérification quotidienne (réseau requis) ; sans confirmation demandée, l'APK est téléchargé d'avance. */
class UpdateWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        runCatching { app.superhealth.modules.Modules.autoUpdate(applicationContext) }
        val s = SuperPrefs.settings.value
        if (!s.updateCheck) return Result.success()
        val rel = runCatching { Updater.check(applicationContext) }.getOrNull() ?: return Result.success()
        if (rel.version == s.updateSkip) return Result.success()
        if (!s.updateConfirm) runCatching { Updater.download(applicationContext, rel) }
        Updater.notify(applicationContext, rel)
        return Result.success()
    }

    companion object {
        fun schedule(ctx: Context) {
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("superhealth_update", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<UpdateWorker>(1, TimeUnit.DAYS)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build())
        }
    }
}

/** Retour de PackageInstaller : affiche la demande de confirmation d'Android, ou l'erreur. */
class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION") val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                ctx.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            PackageInstaller.STATUS_SUCCESS -> Updater.cancelNotification(ctx)
            else -> Updater.state.value = Updater.State.Failed(intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: tr("installation annulée"))
        }
    }
}
