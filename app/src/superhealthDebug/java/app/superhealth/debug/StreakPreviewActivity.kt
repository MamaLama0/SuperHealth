package app.superhealth.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import app.superhealth.ui.theme.SuperTheme
import app.superhealth.ui.theme.Mf
import app.superhealth.nutrition.domain.ThemeMode
import app.superhealth.nutrition.domain.AccentColor
import app.superhealth.ui.league.StreakAnimation

/** Données synthétiques seulement ; aucune lecture ou écriture du profil. */
class StreakPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (packageName != "app.superhealth.qa") { finish(); return }
        setContent {
            SuperTheme(ThemeMode.DARK, AccentColor.GREEN) {
                Column(Modifier.fillMaxSize().background(Mf.colors.background).statusBarsPadding().verticalScroll(rememberScrollState())) {
                    listOf(1, 7, 14, 30, 100).forEach { count ->
                        Row(Modifier.fillMaxWidth().height(130.dp).padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                            StreakAnimation(count, Modifier.size(110.dp))
                            Text("QA · $count", Modifier.padding(start = 20.dp))
                        }
                    }
                }
            }
        }
    }
}
