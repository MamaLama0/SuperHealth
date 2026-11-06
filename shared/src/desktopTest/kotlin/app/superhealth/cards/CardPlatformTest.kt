package app.superhealth.cards

import kotlin.test.Test
import kotlin.test.assertEquals

class CardPlatformTest {
    /** Mêmes tirages que java.util.SplittableRandom : le mode démo donne les mêmes packs qu'Android. */
    @Test fun splittableRandomMatchesJava() {
        for (seed in listOf(0L, 1L, -7L, 123456789L, Long.MIN_VALUE)) {
            val java = java.util.SplittableRandom(seed); val mine = SplittableRandom(seed)
            repeat(50) { i ->
                when (i % 3) {
                    0 -> assertEquals(java.nextLong(), mine.nextLong())
                    1 -> assertEquals(java.nextDouble(), mine.nextDouble())
                    else -> (i + 1).let { bound -> assertEquals(java.nextInt(bound * 7), mine.nextInt(bound * 7)) }
                }
            }
        }
    }

    /** Chaque variante de fond (AGSL d'Android) doit compiler avec Skia, comme sur iPhone. */
    @Test fun allCardShadersCompileWithSkia() {
        val failures = app.superhealth.ui.cards.allCardShaderSources().mapNotNull { src ->
            runCatching { org.jetbrains.skia.RuntimeEffect.makeForShader(src) }.exceptionOrNull()?.message
        }
        assertEquals(emptyList(), failures.distinct().take(3))
    }
}
