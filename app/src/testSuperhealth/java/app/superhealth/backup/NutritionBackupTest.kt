package app.superhealth.backup

import android.app.Application
import androidx.room.Room
import app.superhealth.nutrition.data.AppDatabase
import app.superhealth.nutrition.data.AppJson
import app.superhealth.nutrition.data.Backup
import app.superhealth.nutrition.data.BodyMetricEntity
import app.superhealth.nutrition.data.Prefs
import app.superhealth.nutrition.data.ProgressPhotoEntity
import app.superhealth.nutrition.data.Repository
import app.superhealth.nutrition.data.WeighInEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Sauvegarde nutrition : pesées, mesures et photos de progression reviennent à l'identique ; une ancienne sauvegarde garde les photos. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, manifest = Config.NONE)
class NutritionBackupTest {
    private val ctx = RuntimeEnvironment.getApplication()
    private fun repo() = Repository(Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build(), Prefs(ctx))

    @Test fun photosAndMeasuresSurviveARoundTrip() = runBlocking {
        val a = repo()
        a.weighIns.upsertAll(listOf(WeighInEntity(20000, 80.5, 18.0)))
        a.body.upsertMetrics(listOf(BodyMetricEntity(20000, "waist", 84.0)))
        a.body.insertPhoto(ProgressPhotoEntity(epochDay = 20000, pose = "front", file = "20000-front.jpg"))
        val json = AppJson.encodeToString(Backup.serializer(), a.backup())

        val b = repo()
        b.body.insertPhoto(ProgressPhotoEntity(epochDay = 1, pose = "side", file = "vieille.jpg"))
        b.restore(AppJson.decodeFromString(Backup.serializer(), json))
        assertEquals(listOf("20000-front.jpg"), b.body.photos().map { it.file })
        assertEquals(80.5, b.weighIns.all().single().weightKg, 0.0)
        assertEquals(84.0, b.body.metrics().single().value, 0.0)
    }

    @Test fun backupWithoutPhotosKeepsThePhonesPhotos() = runBlocking {
        val a = repo()
        val old = AppJson.encodeToString(Backup.serializer(), a.backup().copy(version = 3, photos = null))
        val b = repo()
        b.body.insertPhoto(ProgressPhotoEntity(epochDay = 1, pose = "side", file = "garde.jpg"))
        b.restore(AppJson.decodeFromString(Backup.serializer(), old))
        assertEquals(listOf("garde.jpg"), b.body.photos().map { it.file })
    }
}
