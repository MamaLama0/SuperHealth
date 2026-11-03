package app.superhealth.update

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, manifest = Config.NONE)
class InstallLifecycleTest {
    private val ctx: Context get() = RuntimeEnvironment.getApplication()
    private val prefs get() = ctx.getSharedPreferences("update_install", Context.MODE_PRIVATE)
    @Before fun before() { prefs.edit().clear().commit(); Updater.state.value = Updater.State.Idle }
    @After fun after() { prefs.edit().clear().commit(); Updater.state.value = Updater.State.Idle }

    @Test fun repeatedTapsAndRefreshCannotRestartAPendingAndroidSession() {
        val id = ctx.packageManager.packageInstaller.createSession(PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL))
        prefs.edit().putInt("id", id).putString("version", "3.0.4").commit()
        Updater.state.value = Updater.State.Idle // processus recréé pendant l'installation
        assertTrue(Updater.restoreSession(ctx))
        repeat(5) { Updater.startInstall(ctx) }
        kotlinx.coroutines.runBlocking { Updater.refresh(ctx) }
        assertEquals(Updater.State.Installing("3.0.4"), Updater.state.value)
        assertEquals(1, ctx.packageManager.packageInstaller.mySessions.size)
    }
    @Test fun confirmationResumesTheExistingSessionAndCancellationAllowsRetry() {
        prefs.edit().putInt("id", 42).putString("version", "3.0.4").commit()
        val confirm = Intent("confirm-update")
        val callback = Intent().putExtra("update_session", 42).putExtra(Intent.EXTRA_INTENT, confirm)
        assertTrue(Updater.finishSession(ctx, callback, PackageInstaller.STATUS_PENDING_USER_ACTION))
        assertEquals(confirm.action, (Updater.state.value as Updater.State.AwaitingConfirmation).intent.action)
        assertTrue(Updater.busy())
        assertTrue(Updater.finishSession(ctx, callback, PackageInstaller.STATUS_FAILURE_ABORTED))
        assertTrue(Updater.state.value is Updater.State.Failed)
        assertFalse(Updater.busy())
        assertEquals(-1, prefs.getInt("id", -1))
    }
    @Test fun lateCallbacksFromAnOlderSessionCannotFailTheCurrentInstallation() {
        prefs.edit().putInt("id", 99).putString("version", "3.0.4").commit()
        Updater.state.value = Updater.State.Installing("3.0.4")
        assertFalse(Updater.finishSession(ctx, Intent().putExtra("update_session", 98), PackageInstaller.STATUS_FAILURE))
        assertEquals(Updater.State.Installing("3.0.4"), Updater.state.value)
        assertTrue(Updater.finishSession(ctx, Intent().putExtra("update_session", 99), PackageInstaller.STATUS_SUCCESS))
        assertEquals(Updater.State.UpToDate("3.0.4"), Updater.state.value)
    }
}
