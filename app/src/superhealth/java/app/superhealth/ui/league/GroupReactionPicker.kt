package app.superhealth.ui.league

import android.content.res.Configuration
import android.view.ContextThemeWrapper
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.ui.Alignment
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
    // The native emoji grid owns vertical drags and flings. A draggable parent sheet
    // intercepts these gestures and can dismiss itself instead of scrolling the grid.
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Mf.colors.card,
        sheetGesturesEnabled = false, dragHandle = null,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Choisir une réaction"), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, tr("Fermer")) }
        }
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
