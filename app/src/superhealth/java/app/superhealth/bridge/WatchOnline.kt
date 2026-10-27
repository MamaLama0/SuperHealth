package app.superhealth.bridge

import android.content.Context
import android.location.LocationManager
import app.superhealth.ui.tr
import nodomain.freeyourgadget.gadgetbridge.model.WeatherSpec
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.http.interceptors.OnlineFallback
import org.json.JSONObject
import org.slf4j.LoggerFactory
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.math.roundToInt

/**
 * Ce que Garmin Connect fournit en ligne à la montre, sans Garmin Connect : la météo (Open-Meteo, gratuit et sans
 * compte) et les données satellites AGPS (téléchargées chez Garmin, à l'adresse exacte que la montre demande) pour un
 * GPS qui s'accroche vite. Branché dans Gadgetbridge par [OnlineFallback] ; rien n'est envoyé d'autre que la position
 * (arrondie à 0,01°, ~1 km) pour la météo.
 */
object WatchOnline {
    private val LOG = LoggerFactory.getLogger(WatchOnline::class.java)
    private lateinit var app: Context

    fun install(ctx: Context) {
        app = ctx.applicationContext
        OnlineFallback.weather = { lat, lon -> runCatching { weather(lat, lon) }.onFailure { LOG.warn("weather", it) }.getOrNull() }
        OnlineFallback.agps = { url -> runCatching { agps(url) }.onFailure { LOG.warn("agps", it) }.getOrNull() }
    }

    private fun get(url: String, max: Int): ByteArray {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000; c.readTimeout = 8000
        c.setRequestProperty("User-Agent", "SuperHealth")
        try {
            if (c.responseCode !in 200..299) error("HTTP ${c.responseCode}")
            val out = java.io.ByteArrayOutputStream()
            c.inputStream.use { inp ->
                val buf = ByteArray(16 * 1024)
                while (true) { val n = inp.read(buf); if (n < 0) break; out.write(buf, 0, n); if (out.size() > max) error("trop gros") }
            }
            return out.toByteArray()
        } finally { c.disconnect() }
    }

    // ---- AGPS ------------------------------------------------------------------------------------

    /** Fichier de l'adresse demandée (serveurs Garmin uniquement), gardé 4 h comme le demande la montre (max-age). */
    private fun agps(url: String): ByteArray? {
        val host = URL(url).host
        if (host != "api.gcs.garmin.com" && host != "api.gcs.garmin.cn") return null
        val name = MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }.take(32)
        val f = File(app.cacheDir, "agps/$name.bin")
        if (f.isFile && System.currentTimeMillis() - f.lastModified() < 4 * 3_600_000L) return f.readBytes()
        val https = url.replaceFirst(Regex("^http://"), "https://")
        return get(https, 1024 * 1024).also { f.parentFile?.mkdirs(); f.writeBytes(it) }
    }

    // ---- Météo -------------------------------------------------------------------------------------

    /** Dernière position connue du téléphone si la montre n'en envoie pas (autorisation de localisation déjà accordée). */
    @Suppress("MissingPermission")
    private fun phonePosition(): Pair<Float, Float>? = runCatching {
        val lm = app.getSystemService(LocationManager::class.java)
        lm.getProviders(true).mapNotNull { lm.getLastKnownLocation(it) }.maxByOrNull { it.time }?.let { it.latitude.toFloat() to it.longitude.toFloat() }
    }.getOrNull()

    private fun place(lat: Double, lon: Double): String? = runCatching {
        @Suppress("DEPRECATION")
        android.location.Geocoder(app).getFromLocation(lat, lon, 1)?.firstOrNull()?.let { it.locality ?: it.subAdminArea ?: it.adminArea }
    }.getOrNull()

    private fun weather(lat0: Float, lon0: Float): WeatherSpec? {
        val (la, lo) = if (lat0 != 0f || lon0 != 0f) lat0 to lon0 else phonePosition() ?: return null
        val lat = (la * 100).roundToInt() / 100.0
        val lon = (lo * 100).roundToInt() / 100.0
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&timezone=auto&timeformat=unixtime&forecast_days=8&forecast_hours=24" +
            "&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m,wind_direction_10m,pressure_msl,cloud_cover,dew_point_2m,uv_index,visibility,precipitation_probability" +
            "&hourly=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m,wind_direction_10m,precipitation_probability,uv_index,dew_point_2m,pressure_msl" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset,uv_index_max,precipitation_probability_max,wind_speed_10m_max,wind_direction_10m_dominant,relative_humidity_2m_mean"
        val j = JSONObject(String(get(url, 512 * 1024)))
        val cur = j.getJSONObject("current")
        val d = j.getJSONObject("daily")
        val h = j.getJSONObject("hourly")
        fun k(c: Double) = (c + 273.15).roundToInt()
        return WeatherSpec().apply {
            timestamp = (System.currentTimeMillis() / 1000).toInt()
            latitude = la; longitude = lo; isCurrentLocation = 1
            location = place(lat, lon) ?: ""
            currentTemp = k(cur.getDouble("temperature_2m"))
            feelsLikeTemp = k(cur.optDouble("apparent_temperature", cur.getDouble("temperature_2m")))
            currentConditionCode = owm(cur.getInt("weather_code"))
            currentCondition = condition(cur.getInt("weather_code"))
            currentHumidity = cur.optInt("relative_humidity_2m")
            windSpeed = cur.optDouble("wind_speed_10m").toFloat()
            windDirection = cur.optInt("wind_direction_10m")
            pressure = cur.optDouble("pressure_msl").toFloat()
            cloudCover = cur.optInt("cloud_cover")
            dewPoint = k(cur.optDouble("dew_point_2m"))
            uvIndex = cur.optDouble("uv_index", 0.0).toFloat()
            visibility = cur.optDouble("visibility", 0.0).toFloat()
            precipProbability = cur.optInt("precipitation_probability")
            todayMaxTemp = k(d.getJSONArray("temperature_2m_max").getDouble(0))
            todayMinTemp = k(d.getJSONArray("temperature_2m_min").getDouble(0))
            sunRise = d.getJSONArray("sunrise").getInt(0); sunSet = d.getJSONArray("sunset").getInt(0)
            // jours suivants seulement (pas aujourd'hui)
            for (i in 1 until d.getJSONArray("time").length()) forecasts.add(WeatherSpec.Daily().apply {
                maxTemp = k(d.getJSONArray("temperature_2m_max").getDouble(i)); minTemp = k(d.getJSONArray("temperature_2m_min").getDouble(i))
                conditionCode = owm(d.getJSONArray("weather_code").getInt(i))
                humidity = d.getJSONArray("relative_humidity_2m_mean").optInt(i)
                windSpeed = d.getJSONArray("wind_speed_10m_max").optDouble(i, 0.0).toFloat()
                windDirection = d.getJSONArray("wind_direction_10m_dominant").optInt(i)
                uvIndex = d.getJSONArray("uv_index_max").optDouble(i, 0.0).toFloat()
                precipProbability = d.getJSONArray("precipitation_probability_max").optInt(i)
                sunRise = d.getJSONArray("sunrise").optInt(i); sunSet = d.getJSONArray("sunset").optInt(i)
            })
            for (i in 0 until h.getJSONArray("time").length()) hourly.add(WeatherSpec.Hourly().apply {
                timestamp = h.getJSONArray("time").getInt(i)
                temp = k(h.getJSONArray("temperature_2m").getDouble(i))
                conditionCode = owm(h.getJSONArray("weather_code").getInt(i))
                humidity = h.getJSONArray("relative_humidity_2m").optInt(i)
                windSpeed = h.getJSONArray("wind_speed_10m").optDouble(i, 0.0).toFloat()
                windDirection = h.getJSONArray("wind_direction_10m").optInt(i)
                precipProbability = h.getJSONArray("precipitation_probability").optInt(i)
                uvIndex = h.getJSONArray("uv_index").optDouble(i, 0.0).toFloat()
                dewPoint = k(h.getJSONArray("dew_point_2m").optDouble(i, 0.0))
                pressure = h.getJSONArray("pressure_msl").optDouble(i, 0.0).toFloat()
            })
        }
    }

    /** Code météo WMO (Open-Meteo) → code OpenWeatherMap attendu par Gadgetbridge. */
    private fun owm(wmo: Int): Int = when (wmo) {
        0 -> 800; 1 -> 801; 2 -> 802; 3 -> 804
        45, 48 -> 741
        51 -> 300; 53 -> 301; 55 -> 302; 56, 57 -> 511
        61 -> 500; 63 -> 501; 65 -> 502; 66, 67 -> 511
        71 -> 600; 73 -> 601; 75 -> 602; 77 -> 600
        80 -> 520; 81 -> 521; 82 -> 522
        85 -> 620; 86 -> 622
        95 -> 211; 96 -> 201; 99 -> 202
        else -> 3200
    }

    private fun condition(wmo: Int): String = tr(when (wmo) {
        0 -> "Ensoleillé"; 1, 2 -> "Éclaircies"; 3 -> "Couvert"; 45, 48 -> "Brouillard"
        in 51..57 -> "Bruine"; in 61..67, in 80..82 -> "Pluie"; in 71..77, 85, 86 -> "Neige"; in 95..99 -> "Orage"
        else -> "Météo"
    })
}
