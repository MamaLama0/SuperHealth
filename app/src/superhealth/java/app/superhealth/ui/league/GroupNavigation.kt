package app.superhealth.ui.league

import kotlinx.coroutines.flow.MutableStateFlow

/** Explicit destinations for the two home panels, including a repeated Home tap. */
object GroupNavigation {
    val dashboardRequest = MutableStateFlow(0L)
    val groupVisible = MutableStateFlow(false)
    fun dashboard() { dashboardRequest.value += 1 }
}
