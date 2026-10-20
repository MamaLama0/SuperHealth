package app.superhealth.training

import android.app.Application
import app.rive.runtime.kotlin.core.Rive
import app.superhealth.training.data.Store
import app.superhealth.training.domain.ExerciseDb
import app.superhealth.training.ui.Assets
import app.superhealth.training.ui.SessionNotification
import app.superhealth.training.widget.Widgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Module entraînement (ex-application La Salle), initialisé par SuperHealthApp. */
@OptIn(kotlinx.coroutines.FlowPreview::class)
object WorkoutApp {
    lateinit var store: Store
        private set

    /** Base d'exercices de l'APK, chargée une fois. */
    private val baseDb = MutableStateFlow<ExerciseDb?>(null)
    /** Base complète : exercices de l'APK + exercices créés par l'utilisateur. */
    lateinit var db: StateFlow<ExerciseDb?>
        private set

    fun exerciseName(id: String) = db.value?.exercise(id)?.name ?: ""

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun init(app: Application) {
        Assets.init(app)
        Rive.init(app)
        store = Store(app)
        // Widget rafraîchi à chaque changement de séance, d'historique ou de programme.
        scope.launch {
            // L'exercice affiché (current) ne change pas le widget : pas de rafraîchissement à chaque page de la séance.
            store.state.map { Triple(it.session?.copy(current = 0), it.history.size, it.programs) }.distinctUntilChanged().debounce(300)
                .collect { Widgets.refresh(app) }
        }
        db = combine(baseDb, store.state.map { it.customExercises }.distinctUntilChanged()) { b, c -> b?.withCustom(c) }
            .stateIn(scope, SharingStarted.Eagerly, null)
        scope.launch(Dispatchers.IO) { baseDb.value = ExerciseDb.parse(Assets.text("db/exercises.json") ?: error("base absente")) }
        // notification de séance en cours, tenue à jour à chaque changement de séance
        scope.launch {
            combine(store.state.map { it.session }.distinctUntilChanged(), db) { s, _ -> s }.collect { SessionNotification.update(app, store.state.value) }
        }
    }
}
