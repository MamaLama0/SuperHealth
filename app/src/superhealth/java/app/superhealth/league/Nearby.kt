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
 * Amis à proximité, sur le même Wi-Fi et sans serveur : deux téléphones dont la découverte est active s'échangent leur carte signée.
 * Rien d'autre ne transite : une ligne de texte dans chaque sens, limitée en taille et en temps, puis la connexion se ferme. La carte reçue passe par la
 * même vérification de signature qu'un QR code ; un inconnu reste une proposition à confirmer, un ami connu est simplement mis à jour.
 * Le port n'existe que le temps de l'écran : [stop] le ferme et retire le téléphone du réseau.
 */
class Nearby(private val ctx: Context, private val myCard: String, private val detailedCard: String? = null, private val knownOnly: Boolean = false) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val nsd = ctx.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var server: ServerSocket? = null
    private var registered: NsdManager.RegistrationListener? = null
    private var discovering: NsdManager.DiscoveryListener? = null
    private var accepted: Job? = null
    private val tried = HashSet<String>()
    private var ownName = ""
    @Volatile private var stopped = false

    /** Cartes vérifiées de personnes pas encore amies, sur ce réseau. */
    val found = MutableStateFlow<List<League.Card>>(emptyList())

    private fun readLine(i: InputStream): String {
        val out = java.io.ByteArrayOutputStream()
        while (out.size() <= MAX) {
            val c = i.read()
            if (c < 0 || c == '\n'.code) return out.toByteArray().decodeToString()
            out.write(c)
        }
        error("carte trop volumineuse")
    }

    private fun nonce() = java.security.SecureRandom().generateSeed(32).joinToString("") { "%02x".format(it) }
    private fun write(socket: Socket, vararg lines: String) {
        socket.getOutputStream().apply { write((lines.joinToString("\n") + "\n").toByteArray()); flush() }
    }

    private fun handle(raw: String) {
        val c = League.parse(raw) ?: return
        if (c.hello) return
        if (League.isFriend(c.k)) {
            // L'annonce de découverte est volontairement sans relais ni statistiques : attendre les détails authentifiés.
            if (!c.invite) League.accept(ctx, c)
        } else if (!knownOnly && found.value.none { it.k == c.k }) found.value = found.value + c
    }

    @Synchronized fun start() {
        if (stopped) return
        val srv = ServerSocket(0).also { server = it }
        accepted = scope.launch {
            while (isActive) {
                val s = runCatching { srv.accept() }.getOrNull() ?: break
                launch {
                    runCatching {
                        s.use { x ->
                            x.soTimeout = TIMEOUT
                            val raw = readLine(x.getInputStream())
                            val peer = League.parse(raw) ?: return@use
                            val known = League.isFriend(peer.k)
                            if (knownOnly && !known) return@use
                            val clientNonce = readLine(x.getInputStream())
                            val nonce = nonce()
                            write(x, if (known) League.helloCard() else myCard, nonce, League.proof(clientNonce))
                            if (!League.verifyProof(peer.k, nonce, readLine(x.getInputStream()))) return@use
                            handle(raw)
                            if (known) {
                                write(x, detailedCard ?: myCard)
                                val details = readLine(x.getInputStream())
                                if (League.parse(details)?.k == peer.k) handle(details)
                            }
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
        if (stopped) return
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
                                    val nonce = nonce()
                                    write(x, myCard, nonce)
                                    val reply = readLine(x.getInputStream())
                                    val peer = League.parse(reply) ?: return@use
                                    val serverNonce = readLine(x.getInputStream())
                                    if (!League.verifyProof(peer.k, nonce, readLine(x.getInputStream()))) return@use
                                    write(x, League.proof(serverNonce))
                                    handle(reply)
                                    if (League.isFriend(peer.k)) {
                                        val details = readLine(x.getInputStream())
                                        if (League.parse(details)?.k == peer.k) handle(details)
                                        write(x, detailedCard ?: myCard)
                                    }
                                }
                            }
                        }
                    }
                })
            }
        }
        nsd.discoverServices(TYPE, NsdManager.PROTOCOL_DNS_SD, discovering)
    }

    @Synchronized fun stop() {
        stopped = true
        runCatching { discovering?.let { nsd.stopServiceDiscovery(it) } }
        runCatching { registered?.let { nsd.unregisterService(it) } }
        runCatching { server?.close() }
        scope.cancel()
    }

    companion object {
        suspend fun create(ctx: Context, knownOnly: Boolean = false): Nearby {
            val s = League.compute(ctx)
            val name = app.superhealth.ui.userName() ?: "?"
            val remote = Relay.myEndpoint(ctx)
            val compact = if (knownOnly) League.helloCard() else League.card(name, League.Score(s.week, 0, emptyMap(), endDay = s.endDay), invite = true)
            val detail = League.card(name, s, remote, detailed = true, relayKey = if (remote != null) Relay.readKey(ctx) else null)
            return Nearby(ctx, compact, detail, knownOnly)
        }
        const val TYPE = "_superhealth._tcp."
        const val MAX = League.MAX_CARD
        const val TIMEOUT = 5000
    }
}
