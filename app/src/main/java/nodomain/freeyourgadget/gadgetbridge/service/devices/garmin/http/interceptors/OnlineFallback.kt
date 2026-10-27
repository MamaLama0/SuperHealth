package nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.http.interceptors

import nodomain.freeyourgadget.gadgetbridge.model.WeatherSpec
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Sources en ligne fournies par l'app hôte (SuperHealth) quand Gadgetbridge n'a rien en local : météo et données
 * satellites (AGPS) demandées par une montre Garmin, comme le ferait Garmin Connect. Vides par défaut (Gadgetbridge
 * reste hors ligne).
 */
object OnlineFallback {
    /** Météo au point (latitude, longitude) envoyé par la montre ; (0, 0) : position inconnue. */
    @JvmField @Volatile var weather: ((Float, Float) -> WeatherSpec?)? = null

    /** Fichier AGPS de l'adresse demandée par la montre. */
    @JvmField @Volatile var agps: ((String) -> ByteArray?)? = null

    private val pool = Executors.newCachedThreadPool()

    /** Appel réseau hors du fil courant (celui du Bluetooth), 10 s au plus ; null en cas d'échec. */
    @JvmStatic
    fun <T> fetch(block: () -> T?): T? = runCatching { pool.submit(Callable { block() }).get(10, TimeUnit.SECONDS) }.getOrNull()
}
