package app.superhealth.ui
import android.app.Application
import app.superhealth.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, manifest = Config.NONE)
class FirstLaunchTest {
    @Test fun freshProfileStaysEssentialAcrossRestartAndKeepsCustomizations() {
        val ctx = RuntimeEnvironment.getApplication()
        ctx.getSharedPreferences("superhealth", 0).edit().clear().commit()
        SuperPrefs.init(ctx)
        assertEquals(4, SuperPrefs.settings.value.dashboard.size)
        assertTrue(SuperPrefs.settings.value.appTourPending)
        SuperPrefs.init(ctx)
        assertEquals(DashCard.defaults, SuperPrefs.settings.value.dashboard)
        val chosen = listOf(DashCard.CALORIES, DashCard.QUESTS)
        SuperPrefs.update { it.copy(dashboard = chosen, appTourPending = false) }
        SuperPrefs.init(ctx)
        assertEquals(chosen, SuperPrefs.settings.value.dashboard)
        assertFalse(SuperPrefs.settings.value.appTourPending)
        SuperPrefs.importSettings(SuperPrefs.exportSettings())
        assertEquals(chosen, SuperPrefs.settings.value.dashboard)
        assertFalse(SuperPrefs.settings.value.appTourPending)
    }
}
