/*  Copyright (C) 2026 oddballza

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
package app.superhealth.engine.widgets.impl

import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.TextView
import app.superhealth.engine.GBApplication
import app.superhealth.engine.R
import app.superhealth.engine.test.TestBase
import app.superhealth.engine.util.Prefs
import app.superhealth.engine.widgets.WidgetConfig
import app.superhealth.engine.widgets.WidgetInstance
import app.superhealth.engine.widgets.WidgetVariant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Spo2WidgetTest : TestBase() {
    private fun render(data: Spo2Widget.Data): String {
        val instance = WidgetInstance("test-spo2", Spo2Widget.id)
        val config = WidgetConfig(instance, Prefs(GBApplication.getWidgetSharedPrefs(instance.instanceId)), true, emptySet())
        val themed = ContextThemeWrapper(context, R.style.SuperHealthTheme)
        val view = Spo2Widget.createView(LayoutInflater.from(themed), FrameLayout(themed), WidgetVariant.GAUGE)
        Spo2Widget.bind(view, WidgetVariant.GAUGE, config, data)
        return view.findViewById<TextView>(R.id.gauge_value).text.toString()
    }

    @Test
    fun noMeasurementShowsEmptyValue() {
        assertEquals(context.getString(R.string.stats_empty_value), render(Spo2Widget.Data(0)))
    }

    @Test
    fun measurementIsShownAsPercentage() {
        val text = render(Spo2Widget.Data(97))
        assertEquals(context.getString(R.string.battery_percentage_str, "97"), text)
        assertTrue("expected the value in '$text'", text.contains("97"))
    }
}
