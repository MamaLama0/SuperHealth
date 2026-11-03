package app.superhealth.training.domain

import app.superhealth.platform.*

import kotlinx.serialization.json.*

/** Échange d'un programme seul : crée une copie dans la bibliothèque, sans remplacer l'historique. */
object ProgramTransfer {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    fun export(program: Program): String = json.encodeToString(Program.serializer(), program)

    fun read(reader: app.superhealth.platform.Reader, knownExercises: Set<String>): Program {
        val text = StringBuilder()
        val chunk = CharArray(8192)
        while (true) {
            val n = reader.read(chunk); if (n < 0) break
            require(text.length + n <= 4_000_000) { "programme trop volumineux" }
            text.appendRange(chunk, 0, n)
        }
        return read(text.toString(), knownExercises)
    }

    fun read(text: String, knownExercises: Set<String>): Program {
        require(text.length <= 4_000_000) { "programme trop volumineux" }
        val root = json.parseToJsonElement(text).jsonObject
        val raw = root["program"]?.jsonObject ?: root
        val source = if (raw["days"]?.jsonArray?.any { it.jsonObject.containsKey("blocks") } == true)
            fromWorkout(raw) else json.decodeFromJsonElement(Program.serializer(), raw)
        require(source.name.isNotBlank() && source.days.size in 1..31 && source.numCycles in 1..52) { "structure du programme non valable" }
        require(source.days.sumOf { it.exercises.size } <= 500) { "trop d'exercices" }
        val missing = source.days.flatMap { it.exercises }.map { it.exerciseId }.distinct().filter { it !in knownExercises }
        require(missing.isEmpty()) { "${missing.size} exercice(s) absent(s) de la base ; import annulé" }
        val p = source.copy(id = uid(), name = source.name.take(120), cycleIndex = 0, dayIndex = 0, doneDayIds = emptyList(), archived = false,
            gymId = null, createdAt = System.currentTimeMillis(), days = source.days.map { d ->
                require(d.exercises.size <= 50)
                d.copy(id = uid(), name = d.name.take(120), exercises = d.exercises.map { e ->
                    require(e.skippedCycles.all { it in 0 until source.numCycles }) { "cycles exclus non valables" }
                    require(e.cycles.size in 1..52 && e.cycles.all { it.size <= 100 }) { "séries non valables" }
                    require(e.cycles.flatten().all { it.minReps in 0..1000 && it.maxReps in it.minReps..1000 && it.rir in 0..10 && it.restSec in 0..3600 }) { "cibles non valables" }
                    e.copy(id = uid())
                })
            })
        return ProgramEdit.fixCursor(p)
    }

    /** Format blocks/periodizedTargets présent dans Workouts 1.4.2, dont les programmes additionnels. */
    private fun fromWorkout(root: JsonObject): Program {
        fun JsonObject.int(key: String, default: Int) = this[key]?.jsonPrimitive?.intOrNull ?: default
        val cycles = root.int("numCycles", 1)
        val days = root.getValue("days").jsonArray.map { day ->
            val d = day.jsonObject
            ProgramDay(uid(), d["name"]?.jsonPrimitive?.content ?: "Jour", d.getValue("blocks").jsonArray.flatMapIndexed { group, block ->
                val exercises = block.jsonObject.getValue("exercises").jsonArray
                exercises.map { element ->
                    val e = element.jsonObject
                    val targets = e.getValue("periodizedTargets").jsonObject
                    val periods = targets["values"]?.jsonArray ?: JsonArray(listOf(targets["value"] ?: targets))
                    ProgramExercise(uid(), e.getValue("exerciseId").jsonPrimitive.content, periods.map { period ->
                        period.jsonObject.getValue("sets").jsonArray.map { set ->
                            val obj = set.jsonObject
                            val log = obj.getValue("log").jsonObject
                            require(log["duration"] == null && log["distance"] == null) { "cibles de durée ou distance non prises en charge" }
                            val type = when (obj["setType"]?.jsonPrimitive?.content ?: "standard") {
                                "standard" -> SetType.STANDARD; "warmUp" -> SetType.WARMUP; "drop" -> SetType.DROP
                                "myo" -> SetType.MYO; "failure" -> SetType.FAILURE; else -> error("type de série non pris en charge")
                            }
                            TargetSet(type, log.int("minFullReps", 8), log.int("maxFullReps", 12), log.int("rir", 2), (log["restTimer"]?.jsonPrimitive?.longOrNull?.div(1_000_000)?.toInt() ?: 120))
                        }
                    }, if (exercises.size > 1) group else null, periods.mapIndexedNotNull { i, period ->
                        i.takeIf { period.jsonObject["isSkipped"]?.jsonPrimitive?.booleanOrNull == true }
                    }.toSet())
                }
            })
        }
        return Program(uid(), root["name"]?.jsonPrimitive?.content ?: "Programme importé", days = days,
            numCycles = cycles, deload = when (root["deload"]?.jsonPrimitive?.contentOrNull) { "firstCycle" -> Deload.FIRST; "lastCycle" -> Deload.LAST; else -> Deload.NONE }, periodization = if (root["isPeriodized"]?.jsonPrimitive?.booleanOrNull == true) Periodization.LINEAR else Periodization.STATIC)
    }

    private fun uid() = UUID.randomUUID().toString()
}
