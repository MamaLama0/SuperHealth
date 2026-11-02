package app.superhealth.coach

import android.app.Application
import app.superhealth.SuperPrefs
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, manifest = Config.NONE)
class CoachDisabledTest {
    private val context get() = RuntimeEnvironment.getApplication()
    @Before fun setup() {
        SuperPrefs.init(context)
        SuperPrefs.update { it.copy(coachEnabled = false) }
        Coach.init(context)
    }
    @Test fun disabledCoachRejectsAllEntryPointsWithoutChangingChatOrBrief() = runBlocking {
        val old = listOf(Coach.Msg("user", "question", 1), Coach.Msg("assistant", "answer", 2))
        Coach.chat.value = old
        val brief = Coach.Brief(1, "old", emptyList())
        Coach.brief.value = brief
        val prompts = Coach.Prompts(system = "test", brief = "test")
        val action = CoachTools.Action("open", "test", "test", "home")
        val calls: List<suspend () -> Any?> = listOf(
            { Coach.ask(context, "new question") }, { Coach.answer(context) }, { Coach.regenerate(context) },
            { Coach.makeBrief(context) }, { Coach.once(context, prompts, "test") }, { Coach.apply(old.last(), action) },
        )
        assertFalse(Coach.ready())
        for (call in calls) {
            val error = runCatching { call() }.exceptionOrNull()
            assertTrue("Should reject with feature-disabled error before reading data or calling a provider: $error", error is Coach.CoachException)
            assertEquals(app.superhealth.ui.tr("Le coach est désactivé."), error?.message)
            assertEquals(old, Coach.chat.value)
            assertEquals(brief, Coach.brief.value)
        }
    }
}
