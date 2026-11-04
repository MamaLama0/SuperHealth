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
fun SocialOutboxScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    if(SocialLifecycle.pending(ctx)){TextButton({nav.navigate("group/lifecycle")}){Text(tr("Poursuivre la révocation"))};return}
    val scope = rememberCoroutineScope()
    fun pendingEntries():List<SocialOutbox.Entry> {
        val attachments=SocialOutbox.load(ctx).entries
        val history=GroupHistory.state.value
        val posts=history.events.filter{it.key==League.publicKey() && it.id in history.pending && !it.deleted}.mapNotNull{event->event.post?.let{SocialOutbox.Entry(it,event.key)}}
        return (attachments+posts).distinctBy{it.post.id}
    }
    var entries by remember { mutableStateOf(pendingEntries()) }
    val history by GroupHistory.state.collectAsState()
    LaunchedEffect(history){entries=pendingEntries()}
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    DetailScaffold(tr("Envois en attente"), { nav.popBackStack() }) {
        if (entries.isEmpty()) Text(tr("Aucun envoi en attente."))
        entries.forEach { entry ->
            Text(entry.post.title.ifBlank { entry.post.text.ifBlank { tr("Média") } })
            Text(tr(if (entry.ownerKey == League.publicKey()) "Prêt pour la prochaine synchronisation" else "Cette publication appartient à une ancienne identité. Elle reste locale."))
            TextButton(enabled = !busy, onClick = {
                if (entry.ownerKey == League.publicKey()) {
                    GroupHistory.delete(ctx, entry.post)
                    League.configure(ctx) { it.copy(posts = it.posts.filterNot { post -> post.id == entry.post.id }) }
                }
                SocialOutbox.cancel(ctx,entry.post.id)
                entries = pendingEntries()
            }) { Text(tr("Abandonner cet envoi")) }
        }
        TextButton(enabled = !busy, onClick = { scope.launch {
            busy = true; error = null
            try { Relay.sync(ctx); entries = pendingEntries() }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { error = tr("Envoi impossible. Réessayez.") }
            finally { busy = false }
        } }) { Text(tr("Réessayer les envois")) }
        error?.let { Text(it) }
    }
}
