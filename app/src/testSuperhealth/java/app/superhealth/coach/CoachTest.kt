package app.superhealth.coach

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CoachTest {
    @Test fun rememberTagIsStrippedAndProposed() {
        val (text, fact) = Coach.split("Bonne semaine !\n- Dors 8 h.\n<<remember: s'entraîne le soir>>")
        assertEquals("Bonne semaine !\n- Dors 8 h.", text)
        assertEquals("s'entraîne le soir", fact)
    }

    @Test fun answerWithoutTagIsUntouched() {
        val (text, fact) = Coach.split("Rien à retenir.")
        assertEquals("Rien à retenir.", text)
        assertNull(fact)
    }
}
