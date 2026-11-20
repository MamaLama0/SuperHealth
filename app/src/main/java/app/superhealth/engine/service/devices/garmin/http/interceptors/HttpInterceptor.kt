package app.superhealth.engine.service.devices.garmin.http.interceptors

import android.net.Uri
import app.superhealth.engine.service.devices.garmin.http.GarminHttpRequest
import app.superhealth.engine.service.devices.garmin.http.GarminHttpResponse

interface HttpInterceptor {
    fun supports(request: GarminHttpRequest): Boolean
    fun handle(request: GarminHttpRequest): GarminHttpResponse?
}
