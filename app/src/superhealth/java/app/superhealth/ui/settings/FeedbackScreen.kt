package app.superhealth.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.superhealth.league.Relay
import app.superhealth.nutrition.ui.components.*
import app.superhealth.nutrition.ui.screens.DetailScaffold
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.tr
import app.superhealth.update.Updater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.serialization.json.*

@Composable
fun FeedbackScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var kind by rememberSaveable { mutableStateOf("BUG") }
    var rating by rememberSaveable { mutableStateOf(0) }
    var message by rememberSaveable { mutableStateOf("") }
    var id by rememberSaveable { mutableStateOf(java.util.UUID.randomUUID().toString()) }
    var sending by remember { mutableStateOf(false) }
    var sent by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    DetailScaffold(tr("Donner mon avis"), { if (!sending) nav.popBackStack() }) {
        if (sent) {
            MfCard {
                Text(tr("Merci, votre retour a été envoyé."), style = MaterialTheme.typography.titleLarge)
                PillButton(tr("Terminé"), Modifier.fillMaxWidth()) { nav.popBackStack() }
            }
        } else {
            Text(tr("Votre message, la note et la version de l'app sont envoyés en privé au mainteneur. Aucun compte GitHub nécessaire."),
                style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
            MfCard {
                ChipChoice(listOf("BUG", "IDEA", "REVIEW"), kind, { tr(when (it) { "BUG" -> "Problème"; "IDEA" -> "Idée"; else -> "Avis" }) }) { if (!sending) kind = it }
                Text(tr("Votre note (facultative)"), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..5).forEach { value -> FilterChip(rating == value, { if (!sending) rating = if (rating == value) 0 else value }, label = { Text("★ $value") }) }
                }
                OutlinedTextField(message, { message = it.take(3000) }, enabled = !sending, minLines = 5,
                    label = { Text(tr("Votre retour")) }, supportingText = { Text("${message.length}/3000") }, modifier = Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (sending) LinearProgressIndicator(Modifier.fillMaxWidth())
                PillButton(tr("Envoyer en privé"), Modifier.fillMaxWidth(), enabled = !sending && message.trim().length >= 10) {
                    scope.launch {
                        sending = true; error = null
                        try {
                            withContext(Dispatchers.IO) {
                                val base = Relay.defaultBase() ?: error("unavailable")
                                val body = buildJsonObject {
                                    put("id", id); put("kind", kind); put("message", message.trim()); put("version", Updater.installed(ctx))
                                    if (rating > 0) put("rating", rating)
                                }
                                okhttp3.OkHttpClient.Builder().callTimeout(40, java.util.concurrent.TimeUnit.SECONDS).build()
                                    .newCall(okhttp3.Request.Builder().url(base + "/v2/feedback").post(body.toString().toRequestBody("application/json".toMediaType())).build())
                                    .execute().use { response ->
                                        check(response.code == 201 && Json.parseToJsonElement(response.body.string()).jsonObject["received"]?.jsonPrimitive?.booleanOrNull == true)
                                    }
                            }
                            sent = true
                        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                        catch (_: Exception) { error = tr("Le service de retours est indisponible. Votre texte est conservé sur cet écran ; réessayez plus tard.") }
                        finally { sending = false }
                    }
                }
            }
        }
    }
}
