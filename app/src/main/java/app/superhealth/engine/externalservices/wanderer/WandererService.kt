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
package app.superhealth.engine.externalservices.wanderer

import android.content.Context
import androidx.fragment.app.Fragment
import app.superhealth.engine.R
import app.superhealth.engine.externalservices.AuthStyle
import app.superhealth.engine.externalservices.ExternalService

object WandererService : ExternalService {
    override val id = "wanderer"
    override val nameRes = R.string.service_name_wanderer
    override val authStyle = AuthStyle.API_TOKEN

    override fun isLoggedIn(context: Context) =
        serverUrl() != null && WandererTokenManager(context).isLoggedIn()

    override fun showSetup(fragment: Fragment) {
        WandererSetupBottomSheet().show(fragment.parentFragmentManager, "wanderer_setup")
    }

    // The API token is issued and revoked on the server, so there is nothing to tell it about.
    override fun logout(context: Context): Boolean {
        WandererTokenManager(context).clearTokens()
        return true
    }
}
