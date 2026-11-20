package app.superhealth.engine.model

import app.superhealth.engine.service.DeviceSupport
import app.superhealth.engine.util.language.Transliterator
import app.superhealth.engine.util.sanitizeText

interface DeviceTextAdaptable<T> {
    fun transliterated(
        deviceSupport: DeviceSupport,
        transliterator: Transliterator?
    ): T

    fun transform(text: String?,
                  deviceSupport: DeviceSupport,
                  transliterator: Transliterator?): String? {
        val sanitized = sanitizeText(deviceSupport, text)
        return transliterator?.let { sanitized?.let(it::transliterate) } ?: sanitized
    }

    fun withRtlFix(): T

}