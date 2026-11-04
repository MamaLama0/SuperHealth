package app.superhealth.ui.league

import app.superhealth.platform.*

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.superhealth.league.*
import app.superhealth.nutrition.ui.components.*
import app.superhealth.nutrition.ui.screens.DetailScaffold
import app.superhealth.ui.ProfilePhoto
import app.superhealth.ui.go
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.tr
import app.superhealth.ui.trf

/** Le profil partagé et ses décorations ont leur propre espace, séparé des données corporelles. */
@Composable
fun SocialProfileScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val state by League.state.collectAsState()
    val result by ProgressRepository.current.collectAsState()
    val photo by ProfilePhoto.bitmap
    val name = app.superhealth.ui.userName() ?: tr("Mon profil")
    LaunchedEffect(Unit) { runCatching { League.compute(ctx) } }
    DetailScaffold(tr("Social"), { nav.popBackStack() }, contentSpacing = 12.dp) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End){NotificationBell(nav)}
        MfCard {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(80.dp).clip(CircleShape).background(Mf.colors.chip), contentAlignment = Alignment.Center) {
                    if (photo != null) Image(photo!!, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    else Text(name.take(1).uppercase(), style = MaterialTheme.typography.headlineLarge)
                }
                Text(name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
                ProfileDecor(state.profileTitle, state.profileBadges)
                Text(tr("Votre titre et vos badges sont visibles par vos amis."), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, modifier = Modifier.padding(top = 8.dp))
            }
            TextButton({ nav.go("social/badges") }, Modifier.fillMaxWidth()) { Text(tr("Personnaliser mon profil")) }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ProfileStat(result?.summary?.level?.toString() ?: "—", tr("Niveau"), Modifier.weight(1f)) { nav.go("progress") }
            ProfileStat(state.friends.size.toString(), tr("Amis"), Modifier.weight(1f)) { nav.go("friends") }
            ProfileStat(state.earnedBadges.size.toString(), tr("Badges"), Modifier.weight(1f)) { nav.go("social/badges") }
        }
        SectionLabel(tr("Avec mes amis"))
        GroupCard {
            SettingRow(tr("Cercles et défis"), icon = Icons.Rounded.Groups) { nav.go("group/circles") }
            SettingRow(tr("Fil des amis"), icon = Icons.Rounded.Forum) { nav.go("feed") }
            SettingRow(tr("Mes amis"), icon = Icons.Rounded.People) { nav.go("friends") }
            RowDivider(); SettingRow(tr("Ajouter un ami"), subtitle = tr("Code ou QR"), icon = Icons.Rounded.PersonAdd) { nav.go("friends/add") }
            RowDivider(); SettingRow(tr("Classement"), subtitle = tr("Score global"), icon = Icons.Rounded.EmojiEvents) { nav.go("league") }
        }
        SectionLabel(tr("Mon parcours"))
        GroupCard {
            SettingRow(tr("Quêtes de la semaine"), icon = Icons.Rounded.Flag) { nav.go("quests") }
            RowDivider(); SettingRow(tr("Ma progression"), icon = Icons.Rounded.TrendingUp) { nav.go("progress") }
            RowDivider(); SettingRow(tr("Titres et badges"), icon = Icons.Rounded.WorkspacePremium) { nav.go("social/badges") }
        }
        SectionLabel(tr("Sur mon écran d'accueil"))
        GroupCard {
            SettingRow(tr("Widgets iPhone"), subtitle = tr("Classement, séries, quêtes et suivis"), icon = Icons.Rounded.Widgets) { nav.go("widgets") }
        }
        SectionLabel(tr("Mon compte"))
        GroupCard {
            SettingRow(tr("Prénom et photo"), icon = Icons.Rounded.Edit) { if (!nav.popBackStack("settings", false)) nav.go("settings") }
            RowDivider(); SettingRow(tr("Partage et serveur"), icon = Icons.Rounded.PrivacyTip) { nav.go("league/settings") }
        }
    }
}

@Composable
fun ProfileBadgesScreen(nav: NavHostController) {
    DetailScaffold(tr("Titres et badges"), { nav.popBackStack() }, contentSpacing = 12.dp) { AchievementProfileEditor() }
}

@Composable
private fun ProfileStat(value: String, label: String, modifier: Modifier, onClick: () -> Unit) {
    MfCard(modifier, onClick = onClick, padding = PaddingValues(horizontal = 8.dp, vertical = 16.dp)) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelMedium, color = Mf.colors.textSecondary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}
