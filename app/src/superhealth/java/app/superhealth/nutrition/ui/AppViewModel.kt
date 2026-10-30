package app.superhealth.nutrition.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.superhealth.nutrition.data.Analytics
import app.superhealth.nutrition.data.FoodEntryEntity
import app.superhealth.nutrition.data.RecipeItemEntity
import app.superhealth.nutrition.data.Repository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class AppViewModel(val repo: Repository) : ViewModel() {

    val analytics: StateFlow<Analytics?> = repo.analytics().stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Jour affiché dans le journal. */
    val selectedDay = MutableStateFlow(LocalDate.now().toEpochDay())

    @OptIn(ExperimentalCoroutinesApi::class)
    val dayEntries = selectedDay.flatMapLatest { repo.entriesForDay(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Calories de chaque jour de la semaine affichée : lues dès le lancement, les anneaux du Journal sont déjà pleins à son ouverture. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val weekIntake = selectedDay.map { it - LocalDate.ofEpochDay(it).dayOfWeek.value + 1 }.distinctUntilChanged()
        .flatMapLatest { m -> repo.entries.rangeFlow(m, m + 6) }
        .map { l -> l.groupBy { it.epochDay }.mapValues { (_, e) -> e.sumOf { it.kcal } } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** Aujourd'hui, avancé au retour dans l'app (sinon le jour restait celui du lancement après minuit). */
    private val today = MutableStateFlow(LocalDate.now().toEpochDay())

    @OptIn(ExperimentalCoroutinesApi::class)
    val todayEntries = today.flatMapLatest { repo.entriesForDay(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Passage à un nouveau jour : le journal ouvert sur « aujourd'hui » suit. */
    fun refreshDay() {
        val now = LocalDate.now().toEpochDay()
        val old = today.value
        if (now == old) return
        today.value = now
        if (selectedDay.value == old) selectedDay.value = now
    }

    val dayFlags = repo.dayFlags.allFlow().map { l -> l.associateBy { it.epochDay } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** Presse-papiers du journal (copier / coller d'entrées, CopyPasteService de MacroFactor). */
    val clipboard = MutableStateFlow<List<FoodEntryEntity>>(emptyList())

    /** Confirmation affichée par la recherche d'aliments après un ajout fait ailleurs (IA). */
    val logNotice = MutableStateFlow<String?>(null)

    /** Recette en cours d'édition (conservée pendant la recherche d'ingrédients). */
    val recipeDraft = MutableStateFlow<RecipeDraft?>(null)

    val goals = repo.prefs.goals
    val checkIns = repo.prefs.checkIns

    init { repo.migrateGoals() }

    val profile = repo.prefs.profile
    val program = repo.prefs.program
    val settings = repo.prefs.settings

    /** Route demandée par un widget, consommée par la coque SuperHealth. */
    val pendingRoute = MutableStateFlow<String?>(null)


    fun launch(block: suspend Repository.() -> Unit) {
        viewModelScope.launch { repo.block() }
    }

    companion object {
        fun factory(repo: Repository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = AppViewModel(repo) as T
        }
    }
}

/** Brouillon de recette : [id] = 0 pour une nouvelle recette. */
data class RecipeDraft(
    val id: Long = 0, val name: String = "", val servings: String = "1", val totalGrams: String = "",
    val items: List<RecipeItemEntity> = emptyList(), val favorite: Boolean = false,
)
