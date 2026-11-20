package app.superhealth.engine.util

import app.superhealth.engine.GBApplication
import app.superhealth.engine.service.DeviceSupport

/**
 * @param text original text
 * @return 'text' or a new String without non-supported chars like emoticons, etc.
 */
fun sanitizeText(
    deviceSupport: DeviceSupport,
    text: String?
): String? {
    if (text.isNullOrEmpty()) return text

    val filtered = deviceSupport.customStringFilter(text)

    return if (!deviceSupport.device.deviceCoordinator.supportsUnicodeEmojis(deviceSupport.device)) {
        EmojiConverter.convertUnicodeEmojiToAscii(filtered, GBApplication.getContext())
    } else {
        filtered
    }
}