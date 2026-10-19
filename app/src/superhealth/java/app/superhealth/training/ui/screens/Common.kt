package app.superhealth.training.ui.screens

import app.superhealth.training.ui.tr
import app.superhealth.training.ui.trf
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.superhealth.ui.theme.Mf

/** Écran de détail : barre retour + titre, contenu défilant. */
@Composable
fun DetailScaffold(
    title: String, nav: NavHostController?, onBack: (() -> Unit)? = null, scroll: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {}, content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize().background(Mf.colors.background).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onBack?.invoke() ?: nav?.popBackStack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, tr("Retour")) }
            Text(tr(title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f),
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            actions()
        }
        val m = Modifier.fillMaxSize().padding(horizontal = 16.dp).navigationBarsPadding()
        Column(if (scroll) m.verticalScroll(rememberScrollState()) else m) {
            content()
            if (scroll) Spacer(Modifier.height(24.dp))
        }
    }
}
