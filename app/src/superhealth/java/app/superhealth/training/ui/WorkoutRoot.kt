package app.superhealth.training.ui

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.superhealth.training.ui.screens.CompletedScreen
import app.superhealth.training.ui.screens.DashboardScreen
import app.superhealth.training.ui.screens.ExerciseInfoScreen
import app.superhealth.training.ui.screens.ExercisePickerScreen
import app.superhealth.training.ui.screens.GymEditScreen
import app.superhealth.training.ui.screens.GymsScreen
import app.superhealth.training.ui.screens.HistoryScreen
import app.superhealth.training.ui.screens.LevelsScreen
import app.superhealth.training.ui.screens.MoreScreen
import app.superhealth.training.ui.screens.ProgramDayScreen
import app.superhealth.training.ui.screens.ProgramScreen
import app.superhealth.training.ui.screens.ProgramWizardScreen
import app.superhealth.training.ui.screens.SessionScreen
import app.superhealth.training.ui.screens.WorkoutDetailScreen
import app.superhealth.ui.theme.Mf
import kotlinx.coroutines.delay
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController

/** Modes du sélecteur d'exercices. */
object Picker {
    const val SESSION = "session"; const val SWAP = "swap"; const val DAY = "day"; const val BROWSE = "browse"
    /** Remplacer un exercice d'un jour de programme ou d'une séance de bibliothèque (cible « liste|index »). */
    const val SWAP_PLAN = "swapplan"
}

/**
 * Routes du module entraînement dans la navigation de SuperHealth. L'onglet « activities » (programme, autres activités)
 * est enregistré par la coque ; les sous-écrans de La Salle sont ici.
 */
fun NavGraphBuilder.trainingGraph(vm: AppViewModel, nav: NavHostController) {
    composable("session") { SessionScreen(vm, nav) }
    composable("complete") { CompletedScreen(vm, nav, onClose = { vm.completed.value = null; nav.popBackStack() }) }
    composable("wizard") { ProgramWizardScreen(vm, nav) }
    composable("custom/{id}", listOf(navArgument("id") { type = NavType.StringType })) {
        app.superhealth.training.ui.screens.CustomWorkoutScreen(vm, it.arguments!!.getString("id")!!, nav)
    }
    composable("history") { HistoryScreen(vm, nav) }
    composable("levels") { LevelsScreen(vm, nav) }
    composable("t/insights") { DashboardScreen(vm, nav) }
    composable("t/settings") { MoreScreen(vm, nav) }
    composable("gyms") { GymsScreen(vm, nav) }
    composable("gym/{id}", listOf(navArgument("id") { type = NavType.StringType })) { GymEditScreen(vm, it.arguments!!.getString("id")!!, nav) }
    composable("program/{id}", listOf(navArgument("id") { type = NavType.StringType })) { ProgramScreen(vm, it.arguments!!.getString("id")!!, nav) }
    composable("programday/{id}/{day}", listOf(navArgument("id") { type = NavType.StringType }, navArgument("day") { type = NavType.IntType })) {
        ProgramDayScreen(vm, it.arguments!!.getString("id")!!, it.arguments!!.getInt("day"), nav)
    }
    composable("workoutedit/{id}", listOf(navArgument("id") { type = NavType.StringType })) { app.superhealth.training.ui.screens.WorkoutEditScreen(vm, it.arguments!!.getString("id")!!, nav) }
    composable("workoutdetail/{id}", listOf(navArgument("id") { type = NavType.StringType })) { WorkoutDetailScreen(vm, it.arguments!!.getString("id")!!, nav) }
    composable("exerciseedit?id={id}", listOf(navArgument("id") { type = NavType.StringType; defaultValue = "" })) {
        app.superhealth.training.ui.screens.ExerciseEditScreen(vm, it.arguments?.getString("id")?.takeIf { s -> s.isNotEmpty() }, nav)
    }
    composable("exercise/{id}", listOf(navArgument("id") { type = NavType.StringType })) { ExerciseInfoScreen(vm, it.arguments!!.getString("id")!!, nav) }
    composable("picker/{mode}?target={target}", listOf(navArgument("mode") { type = NavType.StringType },
        navArgument("target") { type = NavType.StringType; defaultValue = "" })) {
        ExercisePickerScreen(vm, it.arguments!!.getString("mode")!!, it.arguments!!.getString("target") ?: "", nav)
    }
}

/** Raccourcis du widget et de la notification (« training:… »), consommés par la coque. */
fun handleTrainingRoute(vm: AppViewModel, nav: NavHostController, r: String) {
    when (r) {
        "startnext" -> if (vm.startNext()) nav.navigate("session") else nav.navigate("activities")
        "startempty" -> { vm.startEmpty(); nav.navigate("session") }
        "workout" -> nav.navigate("activities") { popUpTo("activities"); launchSingleTop = true }
        "session" -> if (vm.state.value.session != null) nav.navigate("session") { launchSingleTop = true }
        else -> nav.navigate(r)
    }
}

/** Bandeau « séance en cours » (active_workout_preview). */
@Composable
fun ActiveSessionBar(name: String, start: Long, onOpen: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(1000); now = System.currentTimeMillis() } }
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).clip(RoundedCornerShape(16.dp)).background(Mf.colors.strong)
        .clickable(onClick = onOpen).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.PlayArrow, null, tint = Mf.colors.onStrong)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(tr("Entraînement en cours"), style = MaterialTheme.typography.labelMedium, color = Mf.colors.onStrong.copy(alpha = 0.7f))
            Text(name, style = MaterialTheme.typography.titleSmall, color = Mf.colors.onStrong)
        }
        Text(duration((now - start) / 1000), style = MaterialTheme.typography.titleMedium, color = Mf.colors.onStrong)
    }
}

