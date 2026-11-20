package app.superhealth.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class PluralsTest {
    @Test fun french() {
        assertEquals("1 plugin installé · 1 actif", plurals("1 plugin(s) installé(s) · 1 actif(s)", english = false))
        assertEquals("0 série restante", plurals("0 série(s) restante(s)", english = false))
        assertEquals("3 séries validées. Tout enregistrer ajoutera 1 série renseignée",
            plurals("3 série(s) validée(s). Tout enregistrer ajoutera 1 série(s) renseignée(s)", english = false))
        assertEquals("2 nouveaux badges débloqués.", plurals("2 nouveau(x) badge(s) débloqué(s).", english = false))
    }

    @Test fun english() {
        assertEquals("0 devices", plurals("0 device(s)", english = true))
        assertEquals("1 device", plurals("1 device(s)", english = true))
    }

    @Test fun untouchedWithoutNumber() {
        assertEquals("Protéines (g)", plurals("Protéines (g)", english = false))
        assertEquals("semaine(s) active(s) de suite", plurals("semaine(s) active(s) de suite", english = false))
    }
}
