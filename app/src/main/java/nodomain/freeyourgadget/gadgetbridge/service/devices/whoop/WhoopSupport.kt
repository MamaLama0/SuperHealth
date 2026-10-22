/*  Copyright (C) 2026 SuperHealth

    This file is part of Gadgetbridge (SuperHealth fork), licensed under the GNU AGPL v3 or later.

    Connection sequences follow the public NOOP protocol notes (docs/PROTOCOL_WHOOP4.md, PROTOCOL_WHOOP5.md,
    PROTOCOL_TRANSPORT.md). Only the documented, reversible command subset is ever sent. */
package nodomain.freeyourgadget.gadgetbridge.service.devices.whoop

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import nodomain.freeyourgadget.gadgetbridge.BuildConfig
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventBatteryInfo
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventVersionInfo
import nodomain.freeyourgadget.gadgetbridge.entities.WhoopActivitySample
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.BatteryState
import nodomain.freeyourgadget.gadgetbridge.model.DeviceService
import nodomain.freeyourgadget.gadgetbridge.service.btle.AbstractBTLESingleDeviceSupport
import nodomain.freeyourgadget.gadgetbridge.service.btle.GattService
import nodomain.freeyourgadget.gadgetbridge.service.btle.TransactionBuilder
import nodomain.freeyourgadget.gadgetbridge.util.GB
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.Executors

/**
 * WHOOP 4.0 et 5.0 / MG. À la connexion : abonnements, poignée de main propre à la génération, horloge, batterie, puis
 * récupération de l'historique. Chaque bloc d'historique est d'abord écrit sur le téléphone ([WhoopRawStore]) et
 * seulement ensuite acquitté : le bracelet ne libère que ce qui est enregistré. En fin de récupération, les nuits et les
 * minutes concernées sont recalculées ([WhoopAnalysis]) et écrites dans la base ([WhoopPersist]).
 */
class WhoopSupport : AbstractBTLESingleDeviceSupport(LOG) {
    private var family = WhoopFamily.WHOOP4
    private var seq = 0
    private val reassemblers = HashMap<UUID, WhoopFrames.Reassembler>()
    private val handler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()

    private var historyRunning = false
    private val chunk = ArrayList<WhoopSecond>()
    private var touchedFrom = Long.MAX_VALUE
    private var touchedTo = Long.MIN_VALUE
    private var lastHistoryRequest = 0L
    private var realtime = false

    private val store by lazy { WhoopRawStore(context.filesDir, device.address) }

    init {
        addSupportedService(WhoopUuids.W4_SERVICE)
        addSupportedService(WhoopUuids.W5_SERVICE)
        addSupportedService(GattService.SERVICE_HEART_RATE.uuid)
        addSupportedService(GattService.SERVICE_BATTERY.uuid)
        addSupportedService(GattService.SERVICE_DEVICE_INFORMATION.uuid)
    }

    override fun useAutoConnect() = true

    override fun initializeDevice(builder: TransactionBuilder): TransactionBuilder {
        family = if (getCharacteristic(WhoopUuids.W4_CMD) != null) WhoopFamily.WHOOP4 else WhoopFamily.WHOOP5
        LOG.info("WHOOP family {}", family)
        reassemblers.clear(); historyRunning = false; chunk.clear()
        builder.setDeviceState(GBDevice.State.INITIALIZING)
        builder.requestMtu(517)
        val now = System.currentTimeMillis() / 1000
        if (family == WhoopFamily.WHOOP4) {
            builder.notify(WhoopUuids.W4_CMD_NOTIFY, true)
            builder.notify(WhoopUuids.W4_EVENTS, true)
            builder.notify(WhoopUuids.W4_DATA, true)
            builder.notify(WhoopUuids.HR_MEASUREMENT, true)
            // écriture confirmée de GET_BATTERY_LEVEL : c'est elle qui établit l'appairage chiffré
            cmd(builder, WhoopCommand.GET_BATTERY_LEVEL)
            cmd(builder, WhoopCommand.GET_HELLO_HARVARD)
            cmd(builder, WhoopCommand.REPORT_VERSION_INFO)
            // deux formes de SET_CLOCK (8 et 9 octets) : chacune n'est retenue que par une partie des micrologiciels
            cmd(builder, WhoopCommand.SET_CLOCK, WhoopFrames.clockPayload(now))
            cmd(builder, WhoopCommand.SET_CLOCK, WhoopFrames.clockPayload(now, legacy = true))
            cmd(builder, WhoopCommand.GET_CLOCK)
            // coupe le flot brut R10/R11 (~2 trames/s) inutile ici
            cmd(builder, WhoopCommand.SEND_R10_R11_REALTIME, byteArrayOf(0))
            cmd(builder, WhoopCommand.GET_DATA_RANGE)
        } else {
            builder.notify(WhoopUuids.HR_MEASUREMENT, true)
            builder.write(WhoopUuids.W5_CMD, *WhoopFrames.W5_CLIENT_HELLO)
            WhoopUuids.W5_NOTIFY.forEach { builder.notify(it, true) }
            // un WHOOP 5 sans horloge valide n'enregistre rien : l'horloge passe avant tout historique
            cmd(builder, WhoopCommand.SET_CLOCK, WhoopFrames.clockPayload(now))
            cmd(builder, WhoopCommand.GET_CLOCK, ByteArray(0))
            cmd(builder, WhoopCommand.GET_BATTERY_LEVEL)
        }
        builder.setDeviceState(GBDevice.State.INITIALIZED)
        builder.run(Runnable { handler.postDelayed({ fetchHistory() }, 1500) })
        return builder
    }

    private fun cmd(builder: TransactionBuilder, c: Int, payload: ByteArray = byteArrayOf(0)) {
        seq = (seq + 1) and 0xFF
        builder.write(if (family == WhoopFamily.WHOOP4) WhoopUuids.W4_CMD else WhoopUuids.W5_CMD, *WhoopFrames.command(family, c, seq, payload))
    }

    private fun send(name: String, c: Int, payload: ByteArray = byteArrayOf(0)) {
        val b = createTransactionBuilder(name)
        cmd(b, c, payload)
        b.queue()
    }

    // ---- Notifications ------------------------------------------------------------------

    override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray): Boolean {
        if (super.onCharacteristicChanged(gatt, characteristic, value)) return true
        val uuid = characteristic.uuid
        if (uuid == WhoopUuids.HR_MEASUREMENT) { standardHr(value); return true }
        if (uuid == WhoopUuids.W4_CMD_NOTIFY || uuid == WhoopUuids.W4_EVENTS || uuid == WhoopUuids.W4_DATA || uuid in WhoopUuids.W5_NOTIFY) {
            reassemblers.getOrPut(uuid) { WhoopFrames.Reassembler(family) }.feed(value).forEach { f ->
                if (WhoopFrames.valid(family, f)) runCatching { frame(f) }.onFailure { LOG.warn("WHOOP frame", it) }
            }
            return true
        }
        return false
    }

    private fun frame(f: ByteArray) {
        when (WhoopFrames.type(family, f)) {
            WhoopPacket.COMMAND_RESPONSE -> WhoopFrames.response(family, f)?.let { r ->
                WhoopFrames.battery(family, r)?.let { battery(it, null) }
                WhoopFrames.firmware(r)?.let { fw -> handleGBDeviceEvent(GBDeviceEventVersionInfo().apply { fwVersion = fw; hwVersion = "4.0" }) }
                WhoopFrames.clock(family, r)?.let { LOG.info("WHOOP clock offset {} s", it - System.currentTimeMillis() / 1000) }
            }
            WhoopPacket.REALTIME_DATA -> WhoopFrames.realtime(family, f)?.let { (hr, _) -> live(hr) }
            WhoopPacket.EVENT -> WhoopFrames.event(family, f)?.let { e ->
                if (e.batteryPct != null) battery(e.batteryPct, e.charging)
                when (e.number) {
                    WhoopEvent.CHARGING_ON -> battery(null, true)
                    WhoopEvent.CHARGING_OFF -> battery(null, false)
                }
            }
            WhoopPacket.HISTORICAL_DATA -> { WhoopFrames.historical(family, f)?.let { chunk += it }; armWatchdog() }
            WhoopPacket.METADATA, WhoopPacket.PUFFIN_METADATA -> WhoopFrames.meta(family, f)?.let { meta(it) }
        }
    }

    // ---- Historique ---------------------------------------------------------------------

    /** Demande l'historique (au plus une fois par minute ; ignoré pendant une récupération). */
    private fun fetchHistory() {
        if (historyRunning || !device.isInitialized) return
        val now = System.currentTimeMillis()
        if (now - lastHistoryRequest < 60_000) return
        lastHistoryRequest = now
        historyRunning = true
        chunk.clear(); touchedFrom = Long.MAX_VALUE; touchedTo = Long.MIN_VALUE
        GB.updateTransferNotification(null, context.getString(R.string.busy_task_fetch_activity_data), true, 0, context)
        device.setBusyTask(R.string.busy_task_fetch_activity_data, context)
        device.sendDeviceUpdateIntent(context)
        val b = createTransactionBuilder("whoop history")
        if (family == WhoopFamily.WHOOP5) cmd(b, WhoopCommand.GET_DATA_RANGE)
        cmd(b, WhoopCommand.SEND_HISTORICAL_DATA, byteArrayOf(0))
        b.queue()
        armWatchdog()
    }

    private fun meta(m: WhoopFrames.Meta) {
        when (m.type) {
            WhoopMeta.HISTORY_START -> armWatchdog()
            WhoopMeta.HISTORY_END -> {
                val block = m.ackBlock ?: return
                val records = ArrayList(chunk); chunk.clear()
                // enregistrer d'abord, acquitter ensuite : un échec d'écriture laisse le bloc sur le bracelet
                worker.execute {
                    val ok = runCatching { if (records.isNotEmpty()) store.append(records) }.onFailure { LOG.error("WHOOP store", it) }.isSuccess
                    if (ok) {
                        records.forEach { touchedFrom = minOf(touchedFrom, it.unix); touchedTo = maxOf(touchedTo, it.unix) }
                        handler.post { send("whoop ack", WhoopCommand.HISTORICAL_DATA_RESULT, WhoopFrames.ackPayload(block)) }
                    }
                }
                armWatchdog()
            }
            WhoopMeta.HISTORY_COMPLETE -> finishHistory()
        }
    }

    private val watchdog = Runnable { LOG.info("WHOOP history idle"); finishHistory() }

    private fun armWatchdog() { handler.removeCallbacks(watchdog); handler.postDelayed(watchdog, 60_000) }

    private fun finishHistory() {
        handler.removeCallbacks(watchdog)
        if (!historyRunning) return
        historyRunning = false
        // les écritures en attente passent avant le recalcul (même exécuteur)
        worker.execute {
            if (touchedFrom != Long.MAX_VALUE) runCatching { recompute(touchedFrom, touchedTo) }.onFailure { LOG.error("WHOOP analysis", it) }
            runCatching { store.prune() }
            handler.post {
                device.unsetBusyTask()
                GB.updateTransferNotification(null, "", false, 100, context)
                GB.signalActivityDataFinish(device)
                device.sendDeviceUpdateIntent(context)
            }
        }
    }

    /**
     * Recalcule la fenêtre touchée, élargie à la journée de la veille (une nuit commence la veille au soir) : minutes,
     * sommeil, VFC, pouls au repos, respiration, température, stress.
     */
    private fun recompute(from: Long, to: Long) {
        val start = (from - 18 * 3600) / 60 * 60
        val end = (to / 60 + 1) * 60
        val seconds = store.read(start, end)
        if (seconds.isEmpty()) return
        val result = WhoopAnalysis.analyze(seconds)
        GBApplication.acquireDB().use { db ->
            WhoopPersist.write(db, device, start, end, result, WhoopPersist.previousOnsets(db, device, start))
        }
        LOG.info("WHOOP recomputed {} min, {} night(s)", result.minutes.size, result.sessions.size)
    }

    override fun onFetchRecordedData(dataTypes: Int) = fetchHistory()

    // ---- Direct -------------------------------------------------------------------------

    override fun onEnableRealtimeHeartRateMeasurement(enable: Boolean) {
        realtime = enable
        send("whoop realtime", WhoopCommand.TOGGLE_REALTIME_HR, byteArrayOf(if (enable) 1 else 0))
    }

    override fun onEnableRealtimeSteps(enable: Boolean) {}

    /** Service cardio standard (2A37) : drapeaux, puis pouls sur 8 ou 16 bits. */
    private fun standardHr(v: ByteArray) {
        if (v.size < 2) return
        val hr = if (v[0].toInt() and 1 != 0 && v.size >= 3) v.u16(1) else v.u8(1)
        live(hr)
    }

    private fun live(hr: Int) {
        if (hr !in 25..230) return
        val s = WhoopActivitySample().apply {
            timestamp = (System.currentTimeMillis() / 1000).toInt(); heartRate = hr; steps = 0
            rawKind = nodomain.freeyourgadget.gadgetbridge.devices.whoop.WhoopActivitySampleProvider.KIND_ACTIVITY
        }
        val intent = Intent(DeviceService.ACTION_REALTIME_SAMPLES).setPackage(BuildConfig.APPLICATION_ID)
            .putExtra(GBDevice.EXTRA_DEVICE, device).putExtra(DeviceService.EXTRA_REALTIME_SAMPLE, s)
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
    }

    private fun battery(pct: Int?, charging: Boolean?) {
        val level = pct ?: device.getBatteryLevel(0).takeIf { it in 0..100 } ?: return
        val e = GBDeviceEventBatteryInfo()
        e.level = level
        e.state = when (charging) { true -> BatteryState.BATTERY_CHARGING; false -> BatteryState.BATTERY_NORMAL; null -> device.getBatteryState(0) ?: BatteryState.BATTERY_NORMAL }
        handleGBDeviceEvent(e)
    }

    override fun dispose() {
        handler.removeCallbacksAndMessages(null)
        worker.shutdown()
        super.dispose()
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(WhoopSupport::class.java)
    }
}
