package app.superhealth.bridge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import app.superhealth.ui.localDate
import app.superhealth.ui.timeOf
import app.superhealth.ui.tr
import app.superhealth.ui.trf
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceManager
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.BatteryState

/**
 * État des montres pour le widget « Montre » et la carte du tableau de bord.
 *
 * Gadgetbridge diffuse ACTION_DEVICE_CHANGED à chaque changement (connexion, batterie…) : on relit alors la liste, sans
 * aucune scrutation. La batterie est remise à « inconnue » par Gadgetbridge à la déconnexion : la dernière valeur, l'heure
 * de la dernière information et la dernière connexion sont donc gardées ici, par appareil, sous un condensat de l'adresse
 * (jamais l'adresse elle-même).
 */
object WatchStatus {
    data class Info(
        /** Condensat de l'adresse, stable pour cette installation. */
        val key: String,
        val name: String,
        val connected: Boolean,
        val connecting: Boolean,
        /** 0..100 ; montre déconnectée : la dernière valeur connue. */
        val battery: Int?,
        /** En charge (connue seulement montre connectée). */
        val charging: Boolean,
        /** Dernière information de batterie ou de connexion (ms). */
        val updatedAt: Long,
        /** Dernière fois vue connectée (ms, 0 : jamais). */
        val lastSeen: Long,
    )

    private const val PREFS = "superhealth_watch_status"
    /** Widgets redessinés au plus toutes les 5 s. */
    private const val WIDGET_MIN_MS = 5_000L

    private lateinit var ctx: Context
    private lateinit var sp: SharedPreferences
    private val main = Handler(Looper.getMainLooper())
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<List<Info>>(emptyList())
    val state: StateFlow<List<Info>> = _state
    private val last = HashMap<String, Info>()
    private var refreshPosted = false
    private var widgetPosted = false
    private var lastWidget = 0L

    private val receiver = object : BroadcastReceiver() {
        // Après les autres récepteurs (le gestionnaire d'appareils met d'abord sa liste à jour), une relecture par vague.
        override fun onReceive(c: Context, intent: Intent) {
            if (refreshPosted) return
            refreshPosted = true
            main.post { refreshPosted = false; refresh() }
        }
    }

    fun install(ctx: Context) {
        this.ctx = ctx.applicationContext
        sp = this.ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        LocalBroadcastManager.getInstance(this.ctx).registerReceiver(receiver, IntentFilter().apply {
            addAction(GBDevice.ACTION_DEVICE_CHANGED)
            addAction(DeviceManager.ACTION_DEVICES_CHANGED)
        })
        main.post { refresh() }
    }

    /** Montre à afficher : la première connectée, sinon la dernière vue. */
    fun primary(): Info? = _state.value.firstOrNull()

    private fun installed() = ::sp.isInitialized

    private fun refresh() {
        if (!installed()) return
        val devices = runCatching { Watch.devices() }.getOrDefault(emptyList())
        val now = System.currentTimeMillis()
        val edit = sp.edit()
        var changed = devices.size != last.size
        val out = devices.map { d ->
            val key = hashOf(d.address)
            val prev = last[key]
            val connected = d.isInitialized
            val level = d.getBatteryLevel(0).takeIf { it in 0..100 }
            val st = d.getBatteryState(0)
            val charging = connected && (st == BatteryState.BATTERY_CHARGING || st == BatteryState.BATTERY_CHARGING_FULL)
            var battery = sp.getInt("$key.battery", -1).takeIf { it >= 0 }
            var updatedAt = sp.getLong("$key.at", 0L)
            var lastSeen = sp.getLong("$key.seen", 0L)
            var dirty = false
            if (connected && level != null && level != battery) { battery = level; updatedAt = now; dirty = true }
            // connexion ou déconnexion : la montre a été vue à l'instant
            if (if (prev == null) connected else prev.connected != connected) { lastSeen = now; updatedAt = now; dirty = true }
            else if (prev != null && prev.charging != charging) { updatedAt = now; dirty = true }
            if (dirty) edit.putInt("$key.battery", battery ?: -1).putLong("$key.at", updatedAt).putLong("$key.seen", lastSeen)
            val info = Info(key, d.aliasOrName ?: "", connected, d.isConnecting, battery, charging, updatedAt, lastSeen)
            if (prev == null || prev.name != info.name || prev.connected != connected || prev.battery != battery || prev.charging != charging) changed = true
            info
        }.sortedWith(compareByDescending<Info> { it.connected }.thenByDescending { it.lastSeen })
        edit.apply()
        last.clear(); out.forEach { last[it.key] = it }
        _state.value = out
        if (changed) refreshWidgets()
    }

    private fun refreshWidgets() {
        if (widgetPosted) return
        widgetPosted = true
        val wait = (lastWidget + WIDGET_MIN_MS - SystemClock.elapsedRealtime()).coerceAtLeast(500L)
        main.postDelayed({
            widgetPosted = false
            lastWidget = SystemClock.elapsedRealtime()
            io.launch { runCatching { app.superhealth.ui.widget.WatchBatteryWidgets.refresh(ctx) } }
        }, wait)
    }

    /**
     * Condensat de l'adresse (HMAC-SHA256 à clé propre à l'installation, 32 caractères hexadécimaux) : une adresse
     * Bluetooth a trop peu de valeurs possibles pour qu'un simple SHA-256 la cache.
     */
    fun hashOf(address: String): String = hashes.getOrPut(address) {
        var secret = sp.getString("salt", null)
        if (secret == null) {
            val bytes = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
            secret = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            sp.edit().putString("salt", secret).apply()
        }
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(android.util.Base64.decode(secret, android.util.Base64.NO_WRAP), "HmacSHA256"))
        mac.doFinal(address.uppercase().toByteArray()).take(16).joinToString("") { "%02x".format(it) }
    }
    private val hashes = java.util.concurrent.ConcurrentHashMap<String, String>()

    // region Textes

    /** « Connectée », « En charge », « Connexion… » ou « Déconnectée ». */
    fun stateText(w: Info): String = when {
        w.connected && w.charging -> tr("En charge")
        w.connected -> tr("Connectée")
        w.connecting -> tr("Connexion…")
        else -> tr("Déconnectée")
    }

    /** Montre déconnectée : « Vue à 14:32 », « Vue hier à 22:10 », « Vue le 3 nov. » ; null sinon. */
    fun lastSeenText(w: Info): String? {
        if (w.connected || w.lastSeen <= 0L) return null
        val day = localDate(w.lastSeen)
        val today = LocalDate.now()
        return when (day) {
            today -> trf("Vue à %1\$s", timeOf(w.lastSeen))
            today.minusDays(1) -> trf("Vue hier à %1\$s", timeOf(w.lastSeen))
            else -> trf("Vue le %1\$s", day.format(DateTimeFormatter.ofPattern("d MMM", app.superhealth.ui.Lang.locale)))
        }
    }

    // endregion
}
