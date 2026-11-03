package app.superhealth.update

import android.app.Notification
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
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
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
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.coroutineContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import nodomain.freeyourgadget.gadgetbridge.R
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Mises à jour depuis les versions (releases) GitHub du dépôt choisi : une vérification par jour au plus, puis une
 * notification dont le bouton télécharge et installe sans ouvrir l'app ([UpdateNowWorker]). Android demande de valider
 * l'installation tant que SuperHealth n'a pas lui-même installé la version en place. Le dépôt est public : aucun compte ni jeton.
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
        data object Queued : State
        data class Verifying(val release: Release) : State
        data class Preparing(val version: String) : State
        data class Installing(val version: String) : State
        data class AwaitingConfirmation(val version: String, val intent: Intent) : State
        data class Failed(val message: String) : State
    }

    val state = MutableStateFlow<State>(State.Idle)
    private val operation = Mutex()

    fun busy(s: State = state.value) = s is State.Queued || s is State.Checking || s is State.Downloading ||
        s is State.Verifying || s is State.Preparing || s is State.Installing || s is State.AwaitingConfirmation

    /** Un seul travail, indépendant de l'écran, et aucun second commit pendant la réponse d'Android. */
    @Synchronized fun startInstall(ctx: Context) {
        restoreSession(ctx)
        if (busy()) return
        state.value = State.Queued
        try {
            WorkManager.getInstance(ctx).enqueueUniqueWork("superhealth_update_now", ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<UpdateNowWorker>().setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build())
        } catch (e: Exception) { state.value = State.Failed(e.message ?: "?") }
    }

    private fun sessionPrefs(ctx: Context) = ctx.getSharedPreferences("update_install", Context.MODE_PRIVATE)

    /** Une navigation ou un redémarrage du processus ne doit pas relancer une session déjà confiée à Android. */
    fun restoreSession(ctx: Context): Boolean {
        val prefs = sessionPrefs(ctx)
        val id = prefs.getInt("id", -1)
        if (id < 0) return false
        if (ctx.packageManager.packageInstaller.getSessionInfo(id) == null) {
            prefs.edit().clear().apply()
            if (state.value is State.Installing || state.value is State.AwaitingConfirmation) state.value = State.Idle
            return false
        }
        if (state.value !is State.AwaitingConfirmation) state.value = State.Installing(prefs.getString("version", "").orEmpty())
        return true
    }

    fun finishSession(ctx: Context, intent: Intent, status: Int): Boolean {
        val prefs = sessionPrefs(ctx)
        if (intent.getIntExtra("update_session", -1) != prefs.getInt("id", -2)) return false
        val version = prefs.getString("version", "").orEmpty()
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            @Suppress("DEPRECATION") val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) ?: return false
            state.value = State.AwaitingConfirmation(version, confirm)
        } else {
            prefs.edit().clear().apply()
            state.value = if (status == PackageInstaller.STATUS_SUCCESS) State.UpToDate(version)
                else State.Failed(if (status == PackageInstaller.STATUS_FAILURE_ABORTED) tr("Installation annulée. Vous pouvez réessayer.")
                    else intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: tr("Mise à jour impossible"))
        }
        return true
    }

    private const val CHANNEL = "updates"
    const val NOTIF = 4201
    private val json = Json { ignoreUnknownKeys = true }
    private val http by lazy { OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build() }

    fun installed(ctx: Context): String = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "0"

    /** « v1.2.3 », « 1.2 » → ["1", "2", "3"] (chiffres de chaque composante). */
    fun parts(v: String): List<String> = v.trim().removePrefix("v").removePrefix("V").split('.', '-', '+').map { p -> p.takeWhile(Char::isDigit) }

    /**
     * Versions décimales (majeure +0.1, mineure +0.01) : la composante après le premier point est une fraction,
     * donc 1.41 < 1.5 < 1.51 < 1.6. Les composantes suivantes (1.1.1) se comparent comme des entiers.
     */
    fun newer(remote: String, local: String): Boolean {
        val a = parts(remote); val b = parts(local)
        val major = (a.getOrNull(0)?.toIntOrNull() ?: 0) - (b.getOrNull(0)?.toIntOrNull() ?: 0)
        if (major != 0) return major > 0
        val fa = a.getOrElse(1) { "" }; val fb = b.getOrElse(1) { "" }
        val w = maxOf(fa.length, fb.length)
        val frac = fa.padEnd(w, '0').compareTo(fb.padEnd(w, '0'))
        if (frac != 0) return frac > 0
        for (i in 2 until maxOf(a.size, b.size)) {
            val d = (a.getOrNull(i)?.toIntOrNull() ?: 0) - (b.getOrNull(i)?.toIntOrNull() ?: 0)
            if (d != 0) return d > 0
        }
        return false
    }

    /**
     * Version affichée : sans « v » ni zéro final après le point (« v1.60 » → « 1.6 »). Les versions sont publiées avec
     * une fraction à deux chiffres pour que les anciens comparateurs (composante par composante, 1.41 et 1.5) les voient aussi.
     */
    fun display(tag: String): String {
        val v = tag.trim().removePrefix("v").removePrefix("V")
        val dot = v.indexOf('.')
        if (dot < 0 || v.indexOf('.', dot + 1) >= 0 || v.substring(dot + 1).any { !it.isDigit() }) return v
        val frac = v.substring(dot + 1).trimEnd('0').ifEmpty { "0" }
        return v.substring(0, dot) + "." + frac
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
            val v = display(gh.tag_name)
            if (gh.draft || !newer(v, installed(ctx))) null else Release(v, gh.body.orEmpty().trim(), apk, gh.html_url)
        }
    }

    /** Vérifie et met [state] à jour (écran des mises à jour). */
    suspend fun refresh(ctx: Context) {
        if (restoreSession(ctx) || busy() || !operation.tryLock()) return
        try {
            state.value = State.Checking
            state.value = check(ctx)?.let(State::Available) ?: State.UpToDate(installed(ctx))
        } catch (e: kotlinx.coroutines.CancellationException) {
            state.value = State.Idle; throw e
        } catch (e: Exception) { state.value = State.Failed(e.message ?: "?") }
        finally { operation.unlock() }
    }

    suspend fun prefetch(ctx: Context, rel: Release) {
        if (restoreSession(ctx) || busy() || !operation.tryLock()) return
        try { download(ctx, rel, prefetch = true) }
        catch (e: kotlinx.coroutines.CancellationException) { state.value = State.Idle; throw e }
        catch (e: Exception) { state.value = State.Available(rel); throw e }
        finally { operation.unlock() }
    }

    suspend fun runInstallation(ctx: Context) = operation.withLock {
        if (restoreSession(ctx)) return@withLock
        val rel = check(ctx)
        if (rel == null) { state.value = State.UpToDate(installed(ctx)); cancelNotification(ctx); return@withLock }
        var shown = -1
        val file = download(ctx, rel) { f ->
            val p = (f * 100).toInt() / 5 * 5
            if (p != shown) { shown = p; showProgress(ctx, rel.version, p) }
        }
        install(ctx, file, rel.version)
    }

    /** Télécharge l'APK dans le cache (progression dans [state] et [onProgress]). */
    private suspend fun download(ctx: Context, rel: Release, prefetch: Boolean = false, onProgress: (Float) -> Unit = {}): File = withContext(Dispatchers.IO) {
        state.value = State.Downloading(rel, 0f)
        val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
        val out = File(dir, "SuperHealth-${rel.version}.apk")
        if (out.exists() && runCatching { verify(ctx, out, rel) }.isSuccess) {
            state.value = if (prefetch) State.Ready(rel, out) else State.Preparing(rel.version)
            return@withContext out
        }
        dir.listFiles()?.forEach { it.delete() }
        http.newCall(Request.Builder().url(rel.apk.browser_download_url).build()).execute().use { r ->
            if (!r.isSuccessful) error(trf("téléchargement refusé (%1\$s)", r.code))
            val total = r.body.contentLength().takeIf { it > 0 } ?: rel.apk.size
            var done = 0L
            r.body.byteStream().use { input ->
                out.outputStream().use { o ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = input.read(buf); if (n < 0) break
                        o.write(buf, 0, n); done += n
                        if (total > 0) {
                            val f = (done.toFloat() / total).coerceIn(0f, 1f)
                            state.value = State.Downloading(rel, f)
                            onProgress(f)
                        }
                    }
                }
            }
        }
        state.value = State.Verifying(rel)
        showStage(ctx, rel.version, tr("Vérification du fichier…"))
        verify(ctx, out, rel)
        state.value = if (prefetch) State.Ready(rel, out) else State.Preparing(rel.version)
        out
    }

    /**
     * Contrôle de l'APK avant de le proposer à Android : fichier complet, même application, même clé de signature que
     * l'app installée et version plus récente. Un fichier refusé est supprimé (un message court remplace l'échec d'Android).
     */
    private fun verify(ctx: Context, apk: File, rel: Release) {
        fun fail(msg: String): Nothing { apk.delete(); error(msg) }
        if (rel.apk.size > 0 && apk.length() != rel.apk.size) fail(tr("téléchargement incomplet, réessayez"))
        val pm = ctx.packageManager
        val flags = android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES
        val info = pm.getPackageArchiveInfo(apk.path, flags) ?: fail(tr("fichier de mise à jour illisible"))
        if (info.packageName != ctx.packageName) fail(tr("ce fichier n'est pas SuperHealth"))
        val mine = pm.getPackageInfo(ctx.packageName, flags)
        fun certs(i: android.content.pm.PackageInfo) = i.signingInfo?.apkContentsSigners?.map { it.toCharsString() }?.toSet().orEmpty()
        if (certs(info).isEmpty() || certs(info) != certs(mine)) fail(tr("signature différente de l'app installée"))
        if (!newer(info.versionName ?: "0", installed(ctx))) fail(tr("cette version n'est pas plus récente"))
    }

    /** Installation par PackageInstaller ; Android demande la confirmation (voir [InstallReceiver]). */
    private suspend fun install(ctx: Context, apk: File, version: String) = withContext(Dispatchers.IO) {
        state.value = State.Preparing(version)
        showStage(ctx, version, tr("Préparation de l'installation…"))
        val pi = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(ctx.packageName)
            if (android.os.Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = pi.createSession(params)
        try { pi.openSession(id).use { s ->
            s.openWrite("superhealth.apk", 0, apk.length()).use { o -> apk.inputStream().use { it.copyTo(o) }; s.fsync(o) }
            coroutineContext.ensureActive()
            val intent = Intent(ctx, InstallReceiver::class.java).putExtra("update_session", id)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (android.os.Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            check(sessionPrefs(ctx).edit().putInt("id", id).putString("version", version).commit())
            state.value = State.Installing(version)
            showStage(ctx, version, tr("Installation en cours…"))
            s.commit(PendingIntent.getBroadcast(ctx, id, intent, flags).intentSender)
        } } catch (e: Exception) {
            runCatching { pi.abandonSession(id) }
            sessionPrefs(ctx).edit().clear().apply()
            throw e
        }
    }

    fun canInstall(ctx: Context) = ctx.packageManager.canRequestPackageInstalls()

    // ---- Notification ---------------------------------------------------------------------

    private fun manager(ctx: Context) = ctx.getSystemService(NotificationManager::class.java).also {
        it.createNotificationChannel(NotificationChannel(CHANNEL, tr("Mises à jour"), NotificationManager.IMPORTANCE_DEFAULT))
    }

    private fun openScreen(ctx: Context) = PendingIntent.getActivity(ctx, NOTIF, Intent(ctx, MainActivity::class.java).putExtra(MainActivity.EXTRA_ROUTE, "update")
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    fun notify(ctx: Context, rel: Release) {
        val nm = manager(ctx)
        val open = openScreen(ctx)
        // sans l'autorisation d'installer, le bouton ouvre l'écran qui la demande
        val now = if (canInstall(ctx)) PendingIntent.getBroadcast(ctx, NOTIF, Intent(ctx, UpdateActionReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT) else open
        val n = NotificationCompat.Builder(ctx, CHANNEL).setSmallIcon(R.drawable.ic_stat_update)
            .setContentTitle(trf("SuperHealth %1\$s disponible", rel.version))
            .setContentText(rel.notes.lineSequence().firstOrNull { it.isNotBlank() }?.trimStart('#', '-', '*', ' ') ?: tr("Touchez pour installer"))
            .setContentIntent(open).setAutoCancel(true)
            .addAction(0, tr("Mettre à jour"), now)
            .build()
        runCatching { nm.notify(NOTIF, n) }
    }

    /** Téléchargement en cours, dans la même notification. */
    fun progress(ctx: Context, version: String?, percent: Int): Notification {
        manager(ctx)
        return NotificationCompat.Builder(ctx, CHANNEL).setSmallIcon(R.drawable.ic_stat_update)
            .setContentTitle(if (version != null) "SuperHealth $version" else "SuperHealth")
            .setContentText(tr("Téléchargement…")).setProgress(100, percent, percent == 0)
            .setOngoing(true).setOnlyAlertOnce(true).setSilent(true).build()
    }

    fun showProgress(ctx: Context, version: String?, percent: Int) {
        runCatching { manager(ctx).notify(NOTIF, progress(ctx, version, percent)) }
    }

    fun showStage(ctx: Context, version: String, label: String) {
        val n = NotificationCompat.Builder(ctx, CHANNEL).setSmallIcon(R.drawable.ic_stat_update)
            .setContentTitle("SuperHealth $version").setContentText(label).setProgress(0, 0, true)
            .setContentIntent(openScreen(ctx)).setOngoing(true).setOnlyAlertOnce(true).setSilent(true).build()
        runCatching { manager(ctx).notify(NOTIF, n) }
    }

    /** Android ne laisse pas une app en arrière-plan ouvrir sa demande de confirmation : la notification la propose. */
    fun notifyConfirm(ctx: Context, confirm: Intent) {
        val pi = PendingIntent.getActivity(ctx, NOTIF, confirm, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(ctx, CHANNEL).setSmallIcon(R.drawable.ic_stat_update)
            .setContentTitle(tr("Mise à jour prête")).setContentText(tr("Touchez pour installer"))
            .setContentIntent(pi).setAutoCancel(true).addAction(0, tr("Installer"), pi).build()
        runCatching { manager(ctx).notify(NOTIF, n) }
    }

    fun notifyFailed(ctx: Context, message: String?) {
        val n = NotificationCompat.Builder(ctx, CHANNEL).setSmallIcon(R.drawable.ic_stat_update)
            .setContentTitle(tr("Mise à jour impossible")).setContentText(message)
            .setContentIntent(openScreen(ctx)).setAutoCancel(true).build()
        runCatching { manager(ctx).notify(NOTIF, n) }
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
        if (!s.updateConfirm) try { Updater.prefetch(applicationContext, rel) }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { if (!Updater.busy()) Updater.state.value = Updater.State.Available(rel) }
        if (!Updater.busy()) Updater.notify(applicationContext, rel)
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

/** Bouton « Mettre à jour » de la notification. */
class UpdateActionReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Updater.startInstall(ctx)
    }
}

/** Télécharge et installe la dernière version, avec la progression dans la notification. */
class UpdateNowWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun getForegroundInfo() = ForegroundInfo(Updater.NOTIF, Updater.progress(applicationContext, null, 0))

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        return try {
            Updater.runInstallation(ctx)
            Result.success()
        } catch (e: kotlinx.coroutines.CancellationException) {
            if (!Updater.restoreSession(ctx)) Updater.state.value = Updater.State.Idle
            throw e
        } catch (e: Exception) {
            Updater.state.value = Updater.State.Failed(e.message ?: "?")
            Updater.notifyFailed(ctx, e.message)
            Result.failure()
        }
    }
}

/** Retour de PackageInstaller : demande de confirmation d'Android ou erreur. Une fois l'app remplacée, retire la notification. */
class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            ctx.getSharedPreferences("update_install", Context.MODE_PRIVATE).edit().clear().apply()
            Updater.state.value = Updater.State.UpToDate(Updater.installed(ctx))
            Updater.cancelNotification(ctx); return
        }
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        if (!Updater.finishSession(ctx, intent, status)) return
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION") val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) ?: return
                Updater.notifyConfirm(ctx, confirm)
                runCatching { ctx.startActivity(confirm) }
            }
            PackageInstaller.STATUS_SUCCESS -> Updater.cancelNotification(ctx)
            else -> {
                val message = (Updater.state.value as? Updater.State.Failed)?.message
                if (status == PackageInstaller.STATUS_FAILURE_ABORTED) Updater.cancelNotification(ctx) else Updater.notifyFailed(ctx, message)
            }
        }
    }
}
