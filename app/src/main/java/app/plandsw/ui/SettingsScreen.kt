package app.plandsw.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.plandsw.BuildConfig
import app.plandsw.R

private val THEMES = listOf("system", "light", "dark")

@StringRes
private fun themeLabel(mode: String) = when (mode) {
    "light" -> R.string.theme_light
    "dark" -> R.string.theme_dark
    else -> R.string.theme_system
}

private enum class SettingsDialog { LANGUAGE, THEME, GROUPS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    theme: String,
    groups: List<GroupChoice>,
    selectedGroups: Set<String>,
    onTheme: (String) -> Unit,
    onLanguage: (String) -> Unit,
    onChangeProgram: () -> Unit,
    onGroups: (Set<String>) -> Unit,
    update: UpdateState,
    onCheckUpdates: () -> Unit,
    onInstallUpdate: () -> Unit,
    bottomBar: @Composable () -> Unit,
) {
    var dialog by rememberSaveable { mutableStateOf<SettingsDialog?>(null) }
    val language = remember { currentLanguageTag() }
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.settings_title)) })
        },
        bottomBar = bottomBar,
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            SettingsItem(
                Icons.Outlined.Language,
                stringResource(R.string.settings_language),
                APP_LANGUAGES.first { it.tag == language }.nativeName,
            ) { dialog = SettingsDialog.LANGUAGE }
            SettingsItem(Icons.Outlined.DarkMode, stringResource(R.string.settings_theme), stringResource(themeLabel(theme))) {
                dialog = SettingsDialog.THEME
            }
            HorizontalDivider()
            SettingsItem(Icons.Outlined.School, stringResource(R.string.settings_change_program), null, onChangeProgram)
            if (groups.size > 1) {
                SettingsItem(
                    Icons.Outlined.FilterList,
                    stringResource(R.string.settings_groups),
                    groups.filter { it.name in selectedGroups }.joinToString(", ") { groupCode(it.short) },
                ) { dialog = SettingsDialog.GROUPS }
            }
            HorizontalDivider()
            SettingsItem(
                Icons.Outlined.SystemUpdate,
                stringResource(R.string.update_check),
                when {
                    update.checking -> stringResource(R.string.update_checking)
                    update.progress != null -> stringResource(R.string.update_downloading)
                    update.message != null -> stringResource(update.message)
                    update.available != null -> stringResource(R.string.update_available, update.available.version)
                    else -> null
                },
            ) { if (update.available != null) onInstallUpdate() else onCheckUpdates() }
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_version, BuildConfig.VERSION_NAME)) },
                leadingContent = { Icon(Icons.Outlined.Info, null) },
            )
        }
    }
    when (dialog) {
        SettingsDialog.LANGUAGE -> ChoiceDialog(
            title = stringResource(R.string.settings_language),
            options = APP_LANGUAGES.map { it.tag to it.nativeName },
            selected = language,
            onPick = { dialog = null; if (it != language) onLanguage(it) },
            onDismiss = { dialog = null },
        )
        SettingsDialog.THEME -> ChoiceDialog(
            title = stringResource(R.string.settings_theme),
            options = THEMES.map { it to stringResource(themeLabel(it)) },
            selected = theme,
            onPick = { dialog = null; onTheme(it) },
            onDismiss = { dialog = null },
        )
        SettingsDialog.GROUPS -> GroupPickerSheet(
            groups = groups,
            selected = selectedGroups,
            firstTime = false,
            onDone = { onGroups(it); dialog = null },
            onDismiss = { dialog = null },
        )
        null -> Unit
    }
}

@Composable
private fun SettingsItem(icon: ImageVector, title: String, value: String?, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = value?.takeIf { it.isNotBlank() }?.let { { Text(it) } },
        leadingContent = { Icon(icon, null) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun ChoiceDialog(
    title: String,
    options: List<Pair<String, String>>,
    selected: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (key, label) ->
                    ListItem(
                        headlineContent = { Text(label) },
                        leadingContent = { RadioButton(selected = key == selected, onClick = null) },
                        // ListItem has its own background; inside a dialog it must take the dialog's colour.
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier
                            .clip(RoundedCornerShape(Radii.md))
                            .clickable { onPick(key) },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}
