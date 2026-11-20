package app.superhealth.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.superhealth.DashCard
import app.superhealth.SuperPrefs
import app.superhealth.nutrition.domain.ThemeMode
import app.superhealth.nutrition.ui.components.ChipChoice
import app.superhealth.nutrition.ui.components.MfCard
import app.superhealth.nutrition.ui.components.PillButton
import app.superhealth.nutrition.ui.screens.DetailScaffold
import app.superhealth.ui.SuperViewModel
import app.superhealth.ui.dashboard.CompactBubble
import app.superhealth.ui.dashboard.CompactData
import app.superhealth.ui.dashboard.CompactMedium
import app.superhealth.ui.dashboard.CompactStrip
import app.superhealth.ui.dashboard.hue
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.theme.StyleLab
import app.superhealth.ui.theme.SuperTheme
import app.superhealth.ui.tr
import kotlinx.coroutines.launch

/**
 * Cartes du tableau de bord en style classique : la variante de couleur, réglée à part en clair et en sombre (aperçu sous chaque
 * choix). Le style neutre n'a pas de variante : cet écran n'y est pas proposé.
 */
@Composable
fun StyleLabScreen(vm: SuperViewModel, nav: NavHostController) {
    val sh by SuperPrefs.settings.collectAsState()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    // widgets de l'écran d'accueil : redessinés avec la nouvelle variante
    fun widgets() = scope.launch {
        runCatching { app.superhealth.nutrition.widget.Widgets.refresh(ctx) }
        runCatching { app.superhealth.training.widget.Widgets.refresh(ctx) }
    }
    val accent = vm.nutrition.settings.collectAsState().value.accent
    DetailScaffold(tr("Cartes du tableau de bord"), onBack = { nav.popBackStack() }) {
        Text(tr("Du plus sobre au plus coloré. Les widgets de l'écran d'accueil suivent le choix."), style = MaterialTheme.typography.bodyMedium,
            color = Mf.colors.textSecondary, modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp))

        app.superhealth.nutrition.ui.components.GroupHeader(tr("Thème clair"))
        MfCard {
            ChipChoice(StyleLab.classicCards, StyleLab.classic(sh.styleCardsLight, false), { tr(it.label) }) { c ->
                SuperPrefs.update { it.copy(styleCardsLight = c.name) }; widgets()
            }
            Spacer(Modifier.height(12.dp))
            SuperTheme(ThemeMode.LIGHT, accent, neutral = false) { Preview() }
        }

        app.superhealth.nutrition.ui.components.GroupHeader(tr("Thème sombre"))
        MfCard {
            ChipChoice(StyleLab.classicCards, StyleLab.classic(sh.styleCardsDark, true), { tr(it.label) }) { c ->
                SuperPrefs.update { it.copy(styleCardsDark = c.name) }; widgets()
            }
            Spacer(Modifier.height(12.dp))
            SuperTheme(ThemeMode.DARK, accent, neutral = false) { Preview() }
        }
        Spacer(Modifier.height(12.dp))
        val origin = app.superhealth.SuperSettings()
        PillButton(tr("Rétablir les cartes d'origine"), Modifier.fillMaxWidth(), strong = false) {
            SuperPrefs.update { it.copy(styleCardsLight = origin.styleCardsLight, styleCardsDark = origin.styleCardsDark) }
            widgets()
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** Séance de musculation : couleur de l'entraînement, image de l'exercice, présentation des séries. */
@Composable
fun WorkoutStyleScreen(nav: NavHostController) {
    val sh by SuperPrefs.settings.collectAsState()
    fun <E : Enum<E>> pick(entries: List<E>, name: String, default: E) = entries.firstOrNull { it.name == name } ?: default
    DetailScaffold(tr("Séance de musculation"), onBack = { nav.popBackStack() }) {
        Text(tr("Visible pendant une séance (onglet Activités)."), style = MaterialTheme.typography.bodyMedium, color = Mf.colors.textSecondary,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp))
        Spacer(Modifier.height(8.dp))
        MfCard {
            Label(tr("Couleur de l'entraînement"))
            ChipChoice(StyleLab.WorkoutColor.entries, pick(StyleLab.WorkoutColor.entries, sh.styleWorkoutColor, StyleLab.WorkoutColor.BLUE), { tr(it.label) }) { c ->
                SuperPrefs.update { it.copy(styleWorkoutColor = c.name) }
            }
            Spacer(Modifier.height(18.dp))
            Label(tr("Image de l'exercice"))
            ChipChoice(StyleLab.Hero.entries, pick(StyleLab.Hero.entries, sh.styleHero, StyleLab.Hero.LARGE), { tr(it.label) }) { c ->
                SuperPrefs.update { it.copy(styleHero = c.name) }
            }
            Spacer(Modifier.height(18.dp))
            Label(tr("Séries"))
            ChipChoice(StyleLab.Sets.entries, pick(StyleLab.Sets.entries, sh.styleSets, StyleLab.Sets.CARDS), { tr(it.label) }) { c ->
                SuperPrefs.update { it.copy(styleSets = c.name) }
            }
        }
        Spacer(Modifier.height(12.dp))
        val origin = app.superhealth.SuperSettings()
        PillButton(tr("Rétablir les réglages d'origine"), Modifier.fillMaxWidth(), strong = false) {
            SuperPrefs.update { it.copy(styleWorkoutColor = origin.styleWorkoutColor, styleHero = origin.styleHero, styleSets = origin.styleSets) }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = Mf.colors.textSecondary)
    Spacer(Modifier.height(8.dp))
}

/** Petit tableau de bord d'exemple (valeurs fictives) sur le fond du thème. */
@Composable
private fun Preview() {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Mf.colors.background).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            app.superhealth.ui.theme.CardTone(DashCard.STEPS.hue()) { CompactMedium(DashCard.STEPS, CompactData("8 214", null, tr("Pas"), 0.82f,
                listOf(6100.0, 9200.0, 7400.0, 10300.0, 4200.0, 8800.0, 8214.0), goal = 8000.0) {}, Modifier.weight(2f)) }
            app.superhealth.ui.theme.CardTone(DashCard.SLEEP.hue()) { CompactBubble(DashCard.SLEEP, CompactData("7 h 32", null, tr("Sommeil"), 0.94f) {}, Modifier.weight(1f)) }
        }
        app.superhealth.ui.theme.CardTone(DashCard.HEART.hue()) { CompactStrip(DashCard.HEART, CompactData("58", tr("bpm"), tr("Cœur"), null,
            listOf(61.0, 60.0, 62.0, 59.0, 58.0, 59.0, 58.0), bars = false) {}, Modifier) }
    }
}
