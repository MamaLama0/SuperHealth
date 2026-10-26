package app.superhealth.nutrition.ui

import app.superhealth.platform.*

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.MonitorWeight
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.RestaurantMenu
import androidx.compose.material.icons.rounded.Search
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import app.superhealth.ui.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.superhealth.nutrition.ui.screens.AiLogScreen
import app.superhealth.nutrition.ui.screens.AiImportScreen
import app.superhealth.nutrition.ui.screens.BodyScreen
import app.superhealth.nutrition.ui.screens.HabitsScreen
import app.superhealth.nutrition.ui.screens.AiSettingsScreen
import app.superhealth.nutrition.ui.screens.CheckInScreen
import app.superhealth.nutrition.ui.screens.EnergyBalanceScreen
import app.superhealth.nutrition.ui.screens.GoalProgressScreen
import app.superhealth.nutrition.ui.screens.MacrosScreen
import app.superhealth.nutrition.ui.screens.NutrientsScreen
import app.superhealth.nutrition.ui.screens.GoalEditScreen
import app.superhealth.nutrition.ui.screens.ExpenditureScreen
import app.superhealth.nutrition.ui.screens.FoodSearchScreen
import app.superhealth.nutrition.ui.screens.MoreScreen
import app.superhealth.nutrition.ui.screens.NutritionScreen
import app.superhealth.nutrition.ui.screens.ProfileScreen
import app.superhealth.nutrition.ui.screens.ProgramEditScreen
import app.superhealth.nutrition.ui.screens.RecipeEditScreen
import app.superhealth.nutrition.ui.screens.StrategyScreen
import app.superhealth.nutrition.ui.screens.WeightScreen
import app.superhealth.ui.theme.Mf
import androidx.navigation.NavGraphBuilder

/** Modes d'ouverture de la recherche d'aliments. */
object SearchMode { const val SEARCH = 0; const val SCAN = 1; const val QUICK = 2; const val CREATE = 3; const val INGREDIENT = 4; const val RECIPES = 5 }

fun NavHostController.openSearch(mode: Int = SearchMode.SEARCH, minute: Int = -1) = navigate("search/${mode}/${minute}")

/** Routes du module nutrition dans la navigation de SuperHealth (l'onglet « log » est enregistré par la coque). */
fun NavGraphBuilder.nutritionGraph(vm: AppViewModel, nav: NavHostController) {
    composable("strategy") { StrategyScreen(vm, nav) }
    composable("n/settings") { MoreScreen(vm, nav) }
    composable(
        "search/{mode}/{minute}",
        arguments = listOf(navArgument("mode") { type = NavType.IntType }, navArgument("minute") { type = NavType.IntType }),
    ) {
        FoodSearchScreen(vm, it.arguments?.getInt("mode") ?: 0, it.arguments?.getInt("minute") ?: -1, onDone = { nav.popBackStack() },
            onNav = { r -> nav.navigate(r) })
    }
    composable("weight") { WeightScreen(vm, onBack = { nav.popBackStack() }) }
    composable("expenditure") { ExpenditureScreen(vm, onBack = { nav.popBackStack() }) }
    composable("nutrition") { NutritionScreen(vm, onBack = { nav.popBackStack() }) }
    composable("program") { ProgramEditScreen(vm, onBack = { nav.popBackStack() }) }
    composable("profile") { ProfileScreen(vm, onBack = { nav.popBackStack() }) }
    composable("energy") { EnergyBalanceScreen(vm, onBack = { nav.popBackStack() }) }
    composable("goalprogress") { GoalProgressScreen(vm, onBack = { nav.popBackStack() }) }
    composable("nutrients") { NutrientsScreen(vm, onBack = { nav.popBackStack() }) }
    composable("macros") { MacrosScreen(vm, onBack = { nav.popBackStack() }) }
    composable("goal") { GoalEditScreen(vm, onBack = { nav.popBackStack() }) }
    composable("checkin") { CheckInScreen(vm, onBack = { nav.popBackStack() }) }
    composable("habits") { HabitsScreen(vm, onBack = { nav.popBackStack() }) }
    composable("body") { BodyScreen(vm, onBack = { nav.popBackStack() }) }
    composable("aiimport/{mode}") {
        AiImportScreen(vm, recipe = it.arguments?.getString("mode") == "recipe", onBack = { nav.popBackStack() },
            onSettings = { nav.navigate("aisettings") }, onRecipe = { nav.navigate("recipe") { popUpTo("aiimport/{mode}") { inclusive = true } } })
    }
    composable("ai") {
        // Repas ajouté : retour à la recherche d'aliments (comme après un aliment ou un code-barres), pour continuer à journaliser.
        AiLogScreen(vm, onBack = { nav.popBackStack() }, onSettings = { nav.navigate("aisettings") }, onAdded = {
            if (nav.previousBackStackEntry?.destination?.route?.startsWith("search/") == true) nav.popBackStack()
            else nav.navigate("search/${SearchMode.SEARCH}/-1") { popUpTo("ai") { inclusive = true } }
        })
    }
    composable("aisettings") { AiSettingsScreen(vm, onBack = { nav.popBackStack() }) }
    composable("recipe") { RecipeEditScreen(vm, onBack = { nav.popBackStack() }, onAddIngredient = { r -> nav.navigate(r) }) }
}
