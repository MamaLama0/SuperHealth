package app.superhealth.hiking

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.IBinder
import androidx.core.content.ContextCompat
import app.superhealth.MainActivity
import app.superhealth.ui.tr
import nodomain.freeyourgadget.gadgetbridge.R

/** User-started foreground GPS session. Samples and pause boundaries survive process death. */
class HikeTrackingService : Service(), LocationListener {
    companion object {
        const val CHANNEL = "hiking_tracking"
        const val ID = 7132
        fun start(ctx: Context) = ContextCompat.startForegroundService(ctx, Intent(ctx,HikeTrackingService::class.java))
        fun stop(ctx: Context) = ctx.stopService(Intent(ctx,HikeTrackingService::class.java))
    }
    private lateinit var manager: LocationManager
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onCreate() {
        super.onCreate()
        manager = getSystemService(LocationManager::class.java)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL,tr("Randonnée en cours"),NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this,ID,Intent(this,MainActivity::class.java).putExtra(MainActivity.EXTRA_ROUTE,"hiking/live"),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        startForeground(ID,androidx.core.app.NotificationCompat.Builder(this,CHANNEL).setSmallIcon(R.drawable.ic_stat_update)
            .setContentTitle(tr("Randonnée en cours")).setContentText(tr("Touchez pour retrouver votre parcours et arrêter le suivi GPS."))
            .setContentIntent(open).setOngoing(true).build())
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Hiking.session.value == null) { stopSelf(); return START_NOT_STICKY }
        val fine = ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) { Hiking.gpsError.value = tr("Autorisez la localisation pour enregistrer votre randonnée."); stopSelf(); return START_NOT_STICKY }
        if (Hiking.tracking.value) return START_NOT_STICKY
        try {
            val provider = if (fine && manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) LocationManager.GPS_PROVIDER
                else if (manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) LocationManager.NETWORK_PROVIDER else null
            if (provider == null) { Hiking.gpsError.value = tr("Activez la localisation du téléphone."); stopSelf(); return START_NOT_STICKY }
            val s = Hiking.session.value!!
            Hiking.setSession(this,s.copy(resumed = System.currentTimeMillis(),segment = s.segment+1))
            Hiking.tracking.value = true
            Hiking.gpsError.value = tr("Recherche du signal GPS…")
            manager.requestLocationUpdates(provider,3000,3f,this)
        } catch (_: SecurityException) {
            Hiking.gpsError.value = tr("Autorisez la localisation pour enregistrer votre randonnée."); stopSelf()
        }
        return START_NOT_STICKY
    }
    override fun onLocationChanged(l: Location) {
        val p = HikePoint(l.latitude,l.longitude,if (l.hasAltitude()) l.altitude else null,l.time)
        if (!HikeGeometry.valid(p) || (l.hasAccuracy() && l.accuracy > 50) || System.currentTimeMillis()-l.time > 30000) return
        Hiking.location.value = p; Hiking.gpsError.value = null
        val s = Hiking.session.value ?: return
        if (s.paused) return
        if (s.points.size >= 50000) { Hiking.gpsError.value = tr("Limite d’enregistrement atteinte. Terminez cette sortie."); stopSelf(); return }
        val previous = s.points.lastOrNull()
        val point = p.copy(segment = s.segment)
        if (previous != null && previous.segment == point.segment) {
            val d = HikeGeometry.distance(previous,point)
            val seconds = ((point.time ?: 0)-(previous.time ?: 0))/1000.0
            if (d < 3 || (seconds > 0 && d/seconds > 15)) return
        }
        // Keep the clock's origin: rounding an interval at every GPS point would lose active time.
        Hiking.setSession(this,s.copy(points = s.points+point),sampled=true)
    }
    override fun onProviderDisabled(provider: String) { Hiking.gpsError.value = tr("Signal GPS indisponible. Le suivi reprendra dès son retour.") }
    override fun onProviderEnabled(provider: String) { Hiking.gpsError.value = tr("Recherche du signal GPS…") }
    @Deprecated("Legacy LocationListener callback")
    override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit
    override fun onDestroy() {
        manager.removeUpdates(this)
        val s = Hiking.session.value
        if (s != null && Hiking.tracking.value) Hiking.setSession(this,s.copy(seconds = Hiking.elapsed(s),resumed = System.currentTimeMillis()))
        Hiking.tracking.value = false
        super.onDestroy()
    }
}
