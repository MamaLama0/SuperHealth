package app.superhealth.engine.service.devices.cardo.messages

import app.superhealth.engine.service.devices.cardo.protocol.BitReader
import app.superhealth.engine.service.devices.cardo.protocol.BoolField
import app.superhealth.engine.service.devices.cardo.protocol.IntField

data class BatteryStatus(val isCharging: Boolean, val chargePercent: Int)

private val chargeStatusField = BoolField("charging")
private val batteryChargeField = IntField("batteryCharge", 7)

fun decodeBatteryStatus(payload: ByteArray): BatteryStatus {
    val reader = BitReader(payload)
    val isCharging = reader.read(chargeStatusField)
    val chargePercent = reader.read(batteryChargeField)
    return BatteryStatus(isCharging, chargePercent)
}

