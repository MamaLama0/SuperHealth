package app.superhealth.nutrition.data

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

/** Aliment de la bibliothèque, valeurs pour 100 g (ou 100 mL). */
@Serializable
@Entity(tableName = "foods", indices = [Index("barcode"), Index("name")])
data class FoodEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val brand: String = "",
    val barcode: String? = null,
    val servingName: String = "portion",
    val servingGrams: Double = 100.0,
    val kcal100: Double,
    val protein100: Double,
    val fat100: Double,
    val carbs100: Double,
    val fiber100: Double = 0.0,
    val sugars100: Double = 0.0,
    val satFat100: Double = 0.0,
    val sodiumMg100: Double = 0.0,
    /** "custom" ou "off" (Open Food Facts). */
    val source: String = "custom",
    val favorite: Boolean = false,
    val useCount: Int = 0,
    val lastUsedAt: Long = 0,
    /** Micronutriments pour 100 g : objet JSON {code: valeur} (codes de Micros). */
    val microsJson: String? = null,
    /** Recette (source "recipe") : nombre de portions ; poids total = servingGrams × recipeServings. */
    val recipeServings: Double? = null,
)

/** Ingrédient d'une recette : valeurs figées pour la quantité utilisée. */
@Serializable
@Entity(tableName = "recipe_items", indices = [Index("recipeId")])
data class RecipeItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recipeId: Long,
    val position: Int,
    val foodId: Long? = null,
    val name: String,
    val grams: Double,
    val kcal: Double,
    val protein: Double,
    val fat: Double,
    val carbs: Double,
    val fiber: Double = 0.0,
    val microsJson: String? = null,
)

/** Mesure corporelle (enum BodyMetric de MacroFactor : tour de taille, hanches…), en cm. */
@Serializable
@Entity(tableName = "body_metrics", primaryKeys = ["epochDay", "metric"])
data class BodyMetricEntity(val epochDay: Long, val metric: String, val value: Double)

/** Photo de progression (frontPhoto / sidePhoto / backPhoto), fichier dans filesDir/photos. */
@Serializable
@Entity(tableName = "progress_photos", indices = [Index("epochDay")])
data class ProgressPhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val pose: String,
    val file: String,
)

/** Entrée du journal : valeurs figées au moment de l'ajout (comme MacroFactor). */
@Serializable
@Entity(tableName = "food_entries", indices = [Index("epochDay")])
data class FoodEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    /** 0 petit-déj, 1 déjeuner, 2 dîner, 3 collation. */
    val meal: Int,
    val foodId: Long? = null,
    val name: String,
    val grams: Double? = null,
    val kcal: Double,
    val protein: Double,
    val fat: Double,
    val carbs: Double,
    val fiber: Double = 0.0,
    val createdAt: Long = System.currentTimeMillis(),
    /** Heure de l'entrée (minutes depuis minuit) pour la timeline ; -1 = déduite de createdAt (entrées v1). */
    @ColumnInfo(defaultValue = "-1") val minuteOfDay: Int = -1,
    /** Micronutriments de la portion journalisée (JSON {code: valeur}). */
    val microsJson: String? = null,
)

@Serializable
@Entity(tableName = "weigh_ins")
data class WeighInEntity(
    @PrimaryKey val epochDay: Long,
    val weightKg: Double,
    val bodyFatPct: Double? = null,
)

@Serializable
@Entity(tableName = "day_flags")
data class DayFlagEntity(
    @PrimaryKey val epochDay: Long,
    val fasted: Boolean = false,
    val partial: Boolean = false,
    val note: String = "",
)

@Dao
interface FoodDao {
    @Query("SELECT * FROM foods ORDER BY favorite DESC, useCount DESC, lastUsedAt DESC LIMIT :limit")
    fun recent(limit: Int = 50): Flow<List<FoodEntity>>

    @Query("SELECT * FROM foods WHERE name LIKE '%' || :q || '%' OR brand LIKE '%' || :q || '%' ORDER BY favorite DESC, useCount DESC LIMIT 50")
    suspend fun search(q: String): List<FoodEntity>

    @Query("SELECT * FROM foods WHERE name = :name AND source = :source LIMIT 1")
    suspend fun byNameSource(name: String, source: String): FoodEntity?

    @Query("SELECT * FROM foods WHERE barcode = :code LIMIT 1")
    suspend fun byBarcode(code: String): FoodEntity?

    @Query("SELECT * FROM foods WHERE id = :id")
    suspend fun byId(id: Long): FoodEntity?

    @Query("SELECT * FROM foods")
    suspend fun all(): List<FoodEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(food: FoodEntity): Long

    @Upsert
    suspend fun upsertAll(foods: List<FoodEntity>)

    @Query("UPDATE foods SET useCount = useCount + 1, lastUsedAt = :now WHERE id = :id")
    suspend fun markUsed(id: Long, now: Long = System.currentTimeMillis())

    @Query("UPDATE foods SET favorite = :fav WHERE id = :id")
    suspend fun setFavorite(id: Long, fav: Boolean)

    @Query("DELETE FROM foods WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM foods")
    suspend fun clear()
}

@Dao
interface EntryDao {
    @Query("SELECT * FROM food_entries WHERE epochDay = :day ORDER BY minuteOfDay, createdAt")
    fun forDay(day: Long): Flow<List<FoodEntryEntity>>

    @Query("SELECT * FROM food_entries ORDER BY epochDay, meal, createdAt")
    fun allFlow(): Flow<List<FoodEntryEntity>>

    @Query("SELECT * FROM food_entries ORDER BY epochDay, meal, createdAt")
    suspend fun all(): List<FoodEntryEntity>

    @Insert
    suspend fun insert(e: FoodEntryEntity): Long

    /** Dernière quantité journalisée pour un aliment de la bibliothèque. */
    @Query("SELECT grams FROM food_entries WHERE foodId = :foodId AND grams > 0 ORDER BY createdAt DESC LIMIT 1")
    suspend fun lastGrams(foodId: Long): Double?

    /** Idem pour un aliment pas encore enregistré (Open Food Facts, base suisse) : reconnu par son nom. */
    @Query("SELECT grams FROM food_entries WHERE name = :name AND grams > 0 ORDER BY createdAt DESC LIMIT 1")
    suspend fun lastGramsByName(name: String): Double?

    @Upsert
    suspend fun upsertAll(e: List<FoodEntryEntity>)

    @Query("DELETE FROM food_entries WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM food_entries")
    suspend fun clear()
}

@Dao
interface WeighInDao {
    @Query("SELECT * FROM weigh_ins ORDER BY epochDay")
    fun allFlow(): Flow<List<WeighInEntity>>

    @Query("SELECT * FROM weigh_ins ORDER BY epochDay")
    suspend fun all(): List<WeighInEntity>

    @Upsert
    suspend fun upsert(w: WeighInEntity)

    @Upsert
    suspend fun upsertAll(w: List<WeighInEntity>)

    @Query("DELETE FROM weigh_ins WHERE epochDay = :day")
    suspend fun delete(day: Long)

    @Query("DELETE FROM weigh_ins")
    suspend fun clear()
}

@Dao
interface DayFlagDao {
    @Query("SELECT * FROM day_flags")
    fun allFlow(): Flow<List<DayFlagEntity>>

    @Query("SELECT * FROM day_flags")
    suspend fun all(): List<DayFlagEntity>

    @Upsert
    suspend fun upsert(f: DayFlagEntity)

    @Upsert
    suspend fun upsertAll(f: List<DayFlagEntity>)

    @Query("DELETE FROM day_flags")
    suspend fun clear()
}

@Dao
interface RecipeDao {
    @Query("SELECT * FROM recipe_items WHERE recipeId = :recipeId ORDER BY position")
    suspend fun itemsFor(recipeId: Long): List<RecipeItemEntity>

    @Query("SELECT * FROM recipe_items")
    suspend fun all(): List<RecipeItemEntity>

    @Insert
    suspend fun insertAll(items: List<RecipeItemEntity>)

    @Upsert
    suspend fun upsertAll(items: List<RecipeItemEntity>)

    @Query("DELETE FROM recipe_items WHERE recipeId = :recipeId")
    suspend fun deleteFor(recipeId: Long)

    @Query("DELETE FROM recipe_items")
    suspend fun clear()
}

@Dao
interface BodyDao {
    @Query("SELECT * FROM body_metrics ORDER BY epochDay")
    fun metricsFlow(): Flow<List<BodyMetricEntity>>

    @Query("SELECT * FROM body_metrics ORDER BY epochDay")
    suspend fun metrics(): List<BodyMetricEntity>

    @Upsert
    suspend fun upsertMetrics(m: List<BodyMetricEntity>)

    @Query("DELETE FROM body_metrics WHERE epochDay = :day AND metric = :metric")
    suspend fun deleteMetric(day: Long, metric: String)

    @Query("SELECT * FROM progress_photos ORDER BY epochDay DESC, id")
    fun photosFlow(): Flow<List<ProgressPhotoEntity>>

    @Query("SELECT * FROM progress_photos")
    suspend fun photos(): List<ProgressPhotoEntity>

    @Insert
    suspend fun insertPhoto(p: ProgressPhotoEntity): Long

    @Upsert
    suspend fun upsertPhotos(p: List<ProgressPhotoEntity>)

    @Query("DELETE FROM progress_photos WHERE id = :id")
    suspend fun deletePhoto(id: Long)

    @Query("DELETE FROM body_metrics")
    suspend fun clearMetrics()

    @Query("DELETE FROM progress_photos")
    suspend fun clearPhotos()
}

@Database(
    entities = [
        FoodEntity::class, FoodEntryEntity::class, WeighInEntity::class, DayFlagEntity::class,
        RecipeItemEntity::class, BodyMetricEntity::class, ProgressPhotoEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun foods(): FoodDao
    abstract fun entries(): EntryDao
    abstract fun weighIns(): WeighInDao
    abstract fun dayFlags(): DayFlagDao
    abstract fun recipes(): RecipeDao
    abstract fun body(): BodyDao

    companion object {
        /** v2 : heure de l'entrée, initialisée depuis createdAt (heure locale) pour les entrées existantes. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE food_entries ADD COLUMN minuteOfDay INTEGER NOT NULL DEFAULT -1")
                db.execSQL(
                    "UPDATE food_entries SET minuteOfDay = CAST(strftime('%H', createdAt / 1000, 'unixepoch', 'localtime') AS INTEGER) * 60 + " +
                        "CAST(strftime('%M', createdAt / 1000, 'unixepoch', 'localtime') AS INTEGER)"
                )
            }
        }

        /** v3 : micronutriments (base suisse, Open Food Facts). */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE foods ADD COLUMN microsJson TEXT")
                db.execSQL("ALTER TABLE food_entries ADD COLUMN microsJson TEXT")
            }
        }

        /** v4 : recettes, mesures corporelles, photos de progression. */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE foods ADD COLUMN recipeServings REAL")
                SQL_V4.forEach(db::execSQL)
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "nutrilocal.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build()
    }
}
/** Tables v4, SQL identique au schéma exporté par Room (schemas/…/4.json). */
private val SQL_V4 = listOf(
    "CREATE TABLE IF NOT EXISTS `recipe_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `recipeId` INTEGER NOT NULL, `position` INTEGER NOT NULL, " +
        "`foodId` INTEGER, `name` TEXT NOT NULL, `grams` REAL NOT NULL, `kcal` REAL NOT NULL, `protein` REAL NOT NULL, `fat` REAL NOT NULL, " +
        "`carbs` REAL NOT NULL, `fiber` REAL NOT NULL, `microsJson` TEXT)",
    "CREATE INDEX IF NOT EXISTS `index_recipe_items_recipeId` ON `recipe_items` (`recipeId`)",
    "CREATE TABLE IF NOT EXISTS `body_metrics` (`epochDay` INTEGER NOT NULL, `metric` TEXT NOT NULL, `value` REAL NOT NULL, PRIMARY KEY(`epochDay`, `metric`))",
    "CREATE TABLE IF NOT EXISTS `progress_photos` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `epochDay` INTEGER NOT NULL, `pose` TEXT NOT NULL, `file` TEXT NOT NULL)",
    "CREATE INDEX IF NOT EXISTS `index_progress_photos_epochDay` ON `progress_photos` (`epochDay`)",
)
