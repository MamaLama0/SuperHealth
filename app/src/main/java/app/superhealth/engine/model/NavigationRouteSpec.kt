package app.superhealth.engine.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import app.superhealth.engine.util.maps.TrackPoint

@Parcelize
data class NavigationRouteSpec(
    var route: List<TrackPoint> = emptyList(),
    var revision: Int = 0,
) : Parcelable