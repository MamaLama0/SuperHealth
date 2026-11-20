/*  Copyright (C) 2026 José Rebelo, Dany Mestas

    This file is part of SuperHealth.

    SuperHealth is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published
    by the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    SuperHealth is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>. */
package app.superhealth.engine.externalservices

import android.content.Context
import app.superhealth.engine.externalservices.endurain.EndurainService
import app.superhealth.engine.externalservices.wanderer.WandererService

/**
 * Every external service the app knows about. Supporting another one means adding it here and
 * writing its package; nothing else enumerates them.
 */
object ExternalServiceRegistry {
    val services: List<ExternalService> = listOf(
        EndurainService,
        WandererService,
    )

    fun byId(id: String): ExternalService? = services.firstOrNull { it.id == id }

    /** Lets every service arrange its own background upkeep. Called once at startup. */
    @JvmStatic
    fun scheduleBackgroundWork(context: Context) {
        services.forEach { it.scheduleBackgroundWork(context) }
    }
}
