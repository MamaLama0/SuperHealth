package app.superhealth.meditation
import org.junit.Assert.*
import org.junit.Test

class MeditationContentTest {
    @Test fun pausesAreShortCuratedAndUseSparseWrittenPrompts() {
        val list = Meditation.simpleSessions()
        assertEquals(listOf(3, 5, 10), list.map { it.minutes })
        assertEquals(list.size, list.map { it.id }.distinct().size)
        list.forEach { s ->
            assertTrue(s.lines.size in 2..4)
            assertTrue(s.lines.all { it.at in 0 until s.minutes * 60 && it.fr.isNotBlank() && it.en.isNotBlank() })
            assertEquals(s.lines.sortedBy { it.at }, s.lines)
        }
    }
}
