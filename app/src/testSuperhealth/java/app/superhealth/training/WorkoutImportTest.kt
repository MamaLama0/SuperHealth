package app.superhealth.training

import app.superhealth.training.domain.ExerciseDb
import app.superhealth.training.domain.MuscleGroup
import app.superhealth.training.domain.MuscleStatus
import app.superhealth.training.domain.SessionExercise
import app.superhealth.training.domain.SetLog
import app.superhealth.training.domain.SetType
import app.superhealth.training.domain.Workout
import app.superhealth.training.domain.WorkoutCsv
import app.superhealth.training.domain.WorkoutImport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.ZoneOffset

class WorkoutImportTest {
    private val db = ExerciseDb.parse(File("src/superhealth/assets/db/exercises.json").readText())
    private val index = WorkoutImport.Index(db)
    private val utc = ZoneOffset.UTC

    @Test fun commonNamesMatchTheDatabase() {
        mapOf(
            "Bench Press (Barbell)" to "Barbell bench press",
            "Squat (Barbell)" to "squat",
            "Deadlift (Barbell)" to "deadlift",
            "Pull Up" to "pull",
            "Lat Pulldown (Cable)" to "pulldown",
            "Bicep Curl (Dumbbell)" to "curl",
            "Overhead Press (Barbell)" to "overhead press",
        ).forEach { (src, expected) ->
            val m = WorkoutImport.match(src, index)
            assertNotNull("$src sans correspondance", m)
            assertTrue("$src → ${m!!.name}", m.name.contains(expected, ignoreCase = true))
        }
        assertEquals("Barbell bench press", WorkoutImport.match("Bench Press (Barbell)", index)!!.name)
        // matériel respecté : un développé haltères ne devient pas un développé barre
        assertTrue(WorkoutImport.match("Bench Press (Dumbbell)", index)!!.name.contains("dumbbell", true))
        assertNull(WorkoutImport.match("Zorglub tractions quantiques", index))
    }

    @Test fun strongExportBecomesWorkouts() {
        val csv = """
            Date,Workout Name,Duration,Exercise Name,Set Order,Weight,Reps,Distance,Seconds,Notes,Workout Notes,RPE
            2024-03-04 18:02:11,"Push, lourd",1h 5m,Bench Press (Barbell),W,40,10,0,0,,Bonne forme,
            2024-03-04 18:02:11,"Push, lourd",1h 5m,Bench Press (Barbell),1,80,8,0,0,"pause ""longue"" ok",Bonne forme,8
            2024-03-04 18:02:11,"Push, lourd",1h 5m,Bench Press (Barbell),2,80,7,0,0,,Bonne forme,9
            2024-03-04 18:02:11,"Push, lourd",1h 5m,Exercice Maison Inconnu,1,0,12,0,0,,Bonne forme,
            2024-03-04 18:02:11,"Push, lourd",1h 5m,Bench Press (Barbell),Rest Timer,0,0,0,90,,Bonne forme,
            2024-03-06 07:30:00,Jambes,45m,Squat (Barbell),1,100,5,0,0,,,
        """.trimIndent()
        val p = WorkoutImport.preview(csv, db, emptyList(), zone = utc)
        assertEquals(WorkoutImport.Source.STRONG, p.source)
        assertEquals(2, p.workouts.size)
        val push = p.workouts.first()
        assertEquals("Push, lourd", push.name)
        assertEquals(3900, push.durationSec)
        assertEquals(2, push.exercises.size)
        val bench = push.exercises.first().sets
        assertEquals(listOf(SetType.WARMUP, SetType.STANDARD, SetType.STANDARD), bench.map { it.type })
        assertEquals(listOf(40.0, 80.0, 80.0), bench.map { it.weight })
        assertEquals(2, bench[1].rir)
        assertEquals("pause \"longue\" ok", push.exercises.first().note)
        assertEquals(1, p.newExercises.size)
        assertEquals(listOf("r"), p.newExercises.single().metrics)
        assertEquals(1, p.skippedRows)
        // réimporter le même fichier n'ajoute rien
        val again = WorkoutImport.preview(csv, db, p.workouts, zone = utc)
        assertEquals(0, again.workouts.size)
        assertEquals(2, again.duplicates)
    }

    @Test fun strongPoundsAreConverted() {
        val csv = "Workout #;Date;Workout Name;Duration (sec);Exercise Name;Set Order;Weight (lbs);Reps;RPE;Distance (meters);Seconds;Notes;Workout Notes\n" +
            "1;2024-03-04 18:02:11;A;3600;Bench Press (Barbell);1;225;5;;;;;\n"
        val p = WorkoutImport.preview(csv, db, emptyList(), zone = utc)
        assertEquals(102.06, p.workouts.single().exercises.single().sets.single().weight!!, 0.01)
        assertEquals(3600, p.workouts.single().durationSec)
    }

    @Test fun hevyExportBecomesWorkouts() {
        val csv = """
            "title","start_time","end_time","description","exercise_title","superset_id","exercise_notes","set_index","set_type","weight_kg","reps","distance_km","duration_seconds","rpe"
            "Upper","15 Jan 2024, 10:30","15 Jan 2024, 11:40","","Bench Press (Barbell)","","","0","warmup","40","10","","",""
            "Upper","15 Jan 2024, 10:30","15 Jan 2024, 11:40","","Bench Press (Barbell)","","","1","normal","82.5","6","","","8.5"
            "Upper","15 Jan 2024, 10:30","15 Jan 2024, 11:40","","Plank","","","0","normal","","","","60",""
            "Upper","15 Jan 2024, 10:30","15 Jan 2024, 11:40","","Treadmill","","","0","normal","","","2.5","",""
        """.trimIndent()
        val p = WorkoutImport.preview(csv, db, emptyList(), zone = utc)
        assertEquals(WorkoutImport.Source.HEVY, p.source)
        val w = p.workouts.single()
        assertEquals(70 * 60L, w.durationSec)
        assertEquals(82.5, w.exercises.first().sets[1].weight)
        assertEquals(2, w.exercises.first().sets[1].rir)
        assertEquals(1, p.skippedRows) // tapis : distance seule
        assertEquals(2, w.exercises.size)
    }

    @Test fun fitNotesExportGroupsByDay() {
        val csv = """
            Date,Exercise,Category,Weight (kgs),Reps,Distance,Distance Unit,Time,Comment
            2023-01-15,Flat Barbell Bench Press,Chest,60.0,8,,,,
            2023-01-15,Flat Barbell Bench Press,Chest,60.0,8,,,,
            2023-01-15,Barbell Squat,Legs,100.0,5,,,,Bonne séance
            2023-01-17,Barbell Squat,Legs,102.5,5,,,,
        """.trimIndent()
        val p = WorkoutImport.preview(csv, db, emptyList(), zone = utc)
        assertEquals(WorkoutImport.Source.FITNOTES, p.source)
        assertEquals(2, p.workouts.size)
        assertEquals(2, p.workouts.first().exercises.size)
        assertEquals("Barbell bench press", p.matches.first { it.sourceName.startsWith("Flat") }.exercise?.name)
    }

    @Test(expected = WorkoutImport.UnknownFormat::class) fun otherCsvIsRefused() {
        WorkoutImport.preview("a,b,c\n1,2,3", db, emptyList())
    }

    @Test fun csvExportListsEverySet() {
        val bench = WorkoutImport.match("Bench Press (Barbell)", index)!!
        val w = Workout("w", "Push \"A\"", 1_700_000_000_000, 3600, listOf(SessionExercise("e", bench.id,
            listOf(SetLog("1", weight = 80.0, reps = 8, rir = 2, done = true), SetLog("2", SetType.DROP, weight = 62.5, reps = 10, done = true)))))
        val lines = WorkoutCsv.export(listOf(w), { db.exercise(it)?.name ?: "" }, utc).trim().lines()
        assertEquals(WorkoutCsv.HEADER, lines[0])
        assertEquals(3, lines.size)
        assertTrue(lines[1], lines[1].contains("\"Push \"\"A\"\"\"") && lines[1].contains("Barbell bench press") && lines[1].contains(",80,8,,2,1,"))
        assertTrue(lines[2], lines[2].contains(",drop,62.50,10,"))
    }

    @Test fun muscleRecoveryAndNeglect() {
        val bench = WorkoutImport.match("Bench Press (Barbell)", index)!!
        val now = 1_700_000_000_000L
        val sets = (1..6).map { SetLog("$it", weight = 80.0, reps = 8, done = true) }
        val w = Workout("w", "Push", now - 6 * 3_600_000L, 3600, listOf(SessionExercise("e", bench.id, sets)))
        val rec = MuscleStatus.recovery(listOf(w), db, now)
        val chest = rec.getValue(MuscleGroup.CHEST)
        assertTrue("fatigue ${chest.fatigue}", chest.fatigue in 0.85..1.0)
        assertTrue(chest.readyAt!! > now)
        assertTrue(MuscleStatus.recovery(listOf(w), db, now + 4 * 86_400_000L).isEmpty())
        val idle = MuscleStatus.daysSinceTrained(listOf(w), db, now + 3 * 86_400_000L)
        assertEquals(3L, idle[MuscleGroup.CHEST])
        assertNull(idle[MuscleGroup.CALVES])
    }
}
