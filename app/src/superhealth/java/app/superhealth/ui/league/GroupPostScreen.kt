package app.superhealth.ui.league

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import app.superhealth.league.*
import app.superhealth.nutrition.ui.screens.DetailScaffold
import app.superhealth.ui.tr
import kotlinx.coroutines.launch

@Composable
fun GroupPostScreen(nav: NavHostController, author: String, post: String, reply: String?) {
    val ctx = LocalContext.current
    val history by GroupHistory.state.collectAsState()
    val league by League.state.collectAsState()
    val selected = remember(history, league, author, post) {
        GroupHistory.posts(ctx).firstOrNull { (item, friend) -> item.id == post && (friend?.id ?: League.myId()) == author }
    }
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    fun refresh() { scope.launch {
        if (busy) return@launch
        busy = true; error = false
        try { Relay.sync(ctx); error = League.state.value.syncError != null }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { error = true }
        finally { busy = false }
    } }
    LaunchedEffect(author, post) { refresh() }
    DetailScaffold(tr("Publication du Groupe"), { nav.popBackStack() }) {
        if (busy) LinearProgressIndicator()
        if (selected != null) SocialPostCard(selected.first, selected.second, reply)
        else if (!busy) Text(tr(if (error) "Publication indisponible hors ligne ou pendant une erreur de synchronisation." else "Publication supprimée ou indisponible"))
        if (error || (selected == null && !busy)) TextButton(enabled = !busy, onClick = ::refresh) { Text(tr("Réessayer")) }
    }
}
