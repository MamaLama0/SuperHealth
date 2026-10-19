package app.superhealth.nutrition

import android.app.Application
import app.superhealth.nutrition.data.AppDatabase
import app.superhealth.nutrition.data.Prefs
import app.superhealth.nutrition.data.Repository
import app.superhealth.nutrition.ui.FoodIcons
import app.superhealth.nutrition.widget.Widgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

/** Module nutrition (ex-application La Bouffe), initialisé par SuperHealthApp. */
object NutriApp {
    lateinit var repository: Repository
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @OptIn(FlowPreview::class)
    fun init(app: Application) {
        FoodIcons.init(app)
        repository = Repository(AppDatabase.build(app), Prefs(app))
        // Widgets : rafraîchis à chaque changement du journal, des pesées ou des réglages.
        scope.launch {
            merge(repository.entries.allFlow().map { }, repository.weighIns.allFlow().map { }, repository.prefs.settings.map { })
                .debounce(300).collect { Widgets.refresh(app) }
        }
    }
}
