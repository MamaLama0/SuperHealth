package app.superhealth.engine.service.devices.cardo

import app.superhealth.engine.service.devices.cardo.CardoService.*
import app.superhealth.engine.service.devices.cardo.protocol.BitReader
import app.superhealth.engine.service.devices.cardo.protocol.EnumSetField
import app.superhealth.engine.test.TestBase

import org.junit.Assert.assertEquals
import org.junit.Test

class SubscribeMessageTest: TestBase() {

    @Test
    fun `services bitmask decodes correctly - confirmed against real capture`() {
        val payload = byteArrayOf(0xd1.toByte(), 0x3, 0x80.toByte())
        val reader = BitReader(payload)

        val servicesField = EnumSetField(
            name = "services",
            bitSize = 8,
            values = values()
        )
        val services = reader.read(servicesField)

        assertEquals(
            setOf(
                CAIP_SRVC_UPDATE,
                CAIP_SRVC_DISCONNECT,
                CAIP_SRVC_BATTERY,
                CAIP_SRVC_STATE
            ),
            services
        )
    }

}

enum class CardoService {
    CAIP_SRVC_UPDATE, NA_1, NA_2, NA_3,
    CAIP_SRVC_DISCONNECT, UNK_5,
    CAIP_SRVC_BATTERY, CAIP_SRVC_STATE
}
