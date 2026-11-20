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

class WeightWidgetTest : TestBase() {
    private fun render(data: WeightWidget.Data): String {
        val instance = WidgetInstance("test-weight", WeightWidget.id)
        val config = WidgetConfig(instance, Prefs(GBApplication.getWidgetSharedPrefs(instance.instanceId)), true, emptySet())
        val themed = ContextThemeWrapper(context, R.style.SuperHealthTheme)
        val view = WeightWidget.createView(LayoutInflater.from(themed), FrameLayout(themed), WidgetVariant.GAUGE)
        WeightWidget.bind(view, WidgetVariant.GAUGE, config, data)
        return view.findViewById<TextView>(R.id.gauge_value).text.toString()
    }

    private fun expected(kg: Double): String = WeightWidget.formatWeight(context, kg)

    @Test
    fun noMeasurementShowsEmptyValue() {
        assertEquals(context.getString(R.string.stats_empty_value), render(WeightWidget.Data(0.0, 180)))
    }

    @Test
    fun weightShownWithoutHeight() {
        val text = render(WeightWidget.Data(80.0, 0))
        assertEquals(expected(80.0), text)
        assertTrue("expected the value in '$text'", text.contains("80"))
    }

    @Test
    fun weightShownWithOneDecimal() {
        assertEquals("80.3 kg", render(WeightWidget.Data(80.26, 180)))
    }

    @Test
    fun weightShownWithHeight() {
        assertEquals(expected(80.0), render(WeightWidget.Data(80.0, 180)))
    }
}
