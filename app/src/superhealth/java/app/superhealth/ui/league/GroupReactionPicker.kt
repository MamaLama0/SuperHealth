package app.superhealth.ui.league

import android.content.res.Configuration
import android.view.ContextThemeWrapper
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.emoji2.emojipicker.EmojiPickerView
import app.superhealth.league.SocialReactions
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.tr

/** Opens on the emoji grid itself; no keyboard mode or extra text-entry step is required. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GroupReactionPicker(selected: String?, onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    LaunchedEffect(Unit) { focus.clearFocus(); keyboard?.hide() }
    val dark = Mf.colors.dark
    val select by rememberUpdatedState(onSelect)
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Mf.colors.card,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Text(tr("Choisir une réaction"), Modifier.padding(horizontal = 20.dp, vertical = 8.dp), style = MaterialTheme.typography.titleMedium)
        if (selected != null) TextButton(onClick = { onSelect(selected) }, modifier = Modifier.padding(horizontal = 8.dp)) {
            Text(SocialReactions.emoji(selected) + "  " + tr("Retirer ma réaction"))
        }
        AndroidView(factory = { context ->
            val configuration = Configuration(context.resources.configuration).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    (if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO)
            }
            EmojiPickerView(ContextThemeWrapper(context.createConfigurationContext(configuration),
                androidx.appcompat.R.style.Theme_AppCompat_DayNight)).apply {
                emojiGridColumns = 8
                setOnEmojiPickedListener { item ->
                    (SocialReactions.choices.entries.firstOrNull { it.value == item.emoji }?.key
                        ?: SocialReactions.customKind(item.emoji))?.let { select(it) }
                }
            }
        }, modifier = Modifier.fillMaxWidth().height(340.dp).navigationBarsPadding())
    }
}
