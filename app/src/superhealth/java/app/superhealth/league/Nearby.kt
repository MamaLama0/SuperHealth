package app.superhealth.league

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Amis à proximité, sur le même Wi-Fi et sans serveur : deux téléphones qui ont l'écran Amis ouvert et l'option cochée s'échangent leur carte signée.
 * Rien d'autre ne transite : une ligne de texte dans chaque sens, limitée en taille et en temps, puis la connexion se ferme. La carte reçue passe par la
 * même vérification de signature qu'un QR code ; un inconnu reste une proposition à confirmer, un ami connu est simplement mis à jour.
 * Le port n'existe que le temps de l'écran : [stop] le ferme et retire le téléphone du réseau.
 */
class Nearby(private val ctx: Context, private val myCard: String) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val nsd = ctx.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var server: ServerSocket? = null
    private var registered: NsdManager.RegistrationListener? = null
    private var discovering: NsdManager.DiscoveryListener? = null
    private var accepted: Job? = null
    private val tried = HashSet<String>()
    private var ownName = ""

    /** Cartes vérifiées de personnes pas encore amies, sur ce réseau. */
    val found = MutableStateFlow<List<League.Card>>(emptyList())

    private fun readLine(i: InputStream): String {
        val out = StringBuilder()
        while (out.length < MAX) { val c = i.read(); if (c < 0 || c == '\n'.code) break; out.append(c.toChar()) }
        return out.toString()
    }

    private fun handle(raw: String) {
        val c = League.parse(raw) ?: return
        if (League.isFriend(c.k)) League.accept(ctx, c)
        else if (found.value.none { it.k == c.k }) found.value = found.value + c
    }

    fun start() {
        val srv = ServerSocket(0).also { server = it }
        accepted = scope.launch {
            while (isActive) {
                val s = runCatching { srv.accept() }.getOrNull() ?: break
                launch {
                    runCatching {
                        s.use { x ->
                            x.soTimeout = TIMEOUT
                            handle(readLine(x.getInputStream()))
                            x.getOutputStream().apply { write((myCard + "\n").toByteArray()); flush() }
                        }
                    }
                }
            }
        }
        val info = NsdServiceInfo().apply { serviceName = "SuperHealth"; serviceType = TYPE; port = srv.localPort }
        registered = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(i: NsdServiceInfo) { ownName = i.serviceName; discover() }
            override fun onRegistrationFailed(i: NsdServiceInfo, e: Int) {}
            override fun onServiceUnregistered(i: NsdServiceInfo) {}
            override fun onUnregistrationFailed(i: NsdServiceInfo, e: Int) {}
        }
        nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, registered)
    }

    private fun discover() {
        discovering = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(t: String) {}
            override fun onDiscoveryStopped(t: String) {}
            override fun onStartDiscoveryFailed(t: String, e: Int) {}
            override fun onStopDiscoveryFailed(t: String, e: Int) {}
            override fun onServiceLost(i: NsdServiceInfo) {}
            override fun onServiceFound(i: NsdServiceInfo) {
                if (i.serviceName == ownName || !tried.add(i.serviceName)) return
                @Suppress("DEPRECATION")
                nsd.resolveService(i, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(s: NsdServiceInfo, e: Int) { tried.remove(s.serviceName) }
                    override fun onServiceResolved(s: NsdServiceInfo) {
                        @Suppress("DEPRECATION") val host = s.host ?: return
                        scope.launch {
                            runCatching {
                                Socket().use { x ->
                                    x.connect(InetSocketAddress(host, s.port), TIMEOUT)
                                    x.soTimeout = TIMEOUT
                                    x.getOutputStream().apply { write((myCard + "\n").toByteArray()); flush() }
                                    handle(readLine(x.getInputStream()))
                                }
                            }
                        }
                    }
                })
            }
        }
        nsd.discoverServices(TYPE, NsdManager.PROTOCOL_DNS_SD, discovering)
    }

    fun stop() {
        runCatching { discovering?.let { nsd.stopServiceDiscovery(it) } }
        runCatching { registered?.let { nsd.unregisterService(it) } }
        runCatching { server?.close() }
        scope.cancel()
    }

    private companion object {
        const val TYPE = "_superhealth._tcp."
        const val MAX = 2000
        const val TIMEOUT = 5000
    }
}
