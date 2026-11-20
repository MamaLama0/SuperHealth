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
import app.superhealth.engine.model.ActivityUser
import app.superhealth.engine.test.TestBase
import app.superhealth.engine.util.BodyCompositionCalculator
import app.superhealth.engine.util.Prefs
import app.superhealth.engine.widgets.WidgetConfig
import app.superhealth.engine.widgets.WidgetInstance
import app.superhealth.engine.widgets.WidgetVariant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import kotlin.math.roundToInt

class BodyCompositionWidgetsTest : TestBase() {
    // Synthetic profile and reading (the calculator's own test vectors), not a real person's.
    private val composition = BodyCompositionCalculator.compute(ActivityUser.GENDER_MALE, 30, 180, 80f, 500f)

    private fun render(widget: BodyCompositionWidget, data: BodyCompositionWidget.Data): String {
        val instance = WidgetInstance("test-" + widget.id, widget.id)
        val config = WidgetConfig(instance, Prefs(GBApplication.getWidgetSharedPrefs(instance.instanceId)), true, emptySet())
        val themed = ContextThemeWrapper(context, R.style.SuperHealthTheme)
        val view = widget.createView(LayoutInflater.from(themed), FrameLayout(themed), WidgetVariant.GAUGE)
        widget.bind(view, WidgetVariant.GAUGE, config, data)
        return view.findViewById<TextView>(R.id.gauge_value).text.toString()
    }

    private fun data() = BodyCompositionWidget.Data(composition, ActivityUser.GENDER_MALE)

    @Test
    fun eachWidgetShowsItsOwnValue() {
        assertNotNull(composition)
        val c = composition!!
        assertEquals(context.getString(R.string.body_composition_percent, c.bodyFatPercent), render(BodyFatWidget, data()))
        assertEquals(context.getString(R.string.body_energy_percentage, c.bodyWaterPercent.roundToInt()), render(BodyWaterWidget, data()))
        assertEquals(WeightWidget.formatWeight(context, c.muscleMassKg.toDouble()), render(MuscleMassWidget, data()))
        assertEquals(context.getString(R.string.body_composition_kcal, c.basalMetabolicRate), render(BasalMetabolicRateWidget, data()))
        // and they are four different numbers, so a mix-up between them would show
        assertEquals(4, setOf(c.bodyFatPercent, c.bodyWaterPercent, c.muscleMassKg, c.basalMetabolicRate.toFloat()).size)
    }

    @Test
    fun noEstimateShowsEmptyValue() {
        val empty = BodyCompositionWidget.Data(null, ActivityUser.GENDER_MALE)
        for (w in listOf(BodyFatWidget, BodyWaterWidget, MuscleMassWidget, BasalMetabolicRateWidget)) {
            assertEquals(w.id, context.getString(R.string.stats_empty_value), render(w, empty))
        }
    }
}
