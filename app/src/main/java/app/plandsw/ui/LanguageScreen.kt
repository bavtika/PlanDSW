package app.plandsw.ui

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import app.plandsw.R
import java.util.Locale

data class AppLanguage(val tag: String, val nativeName: String)

val APP_LANGUAGES = listOf(
    AppLanguage("en", "English"),
    AppLanguage("pl", "Polski"),
    AppLanguage("ru", "Русский"),
    AppLanguage("uk", "Українська"),
)

/** Current app language; if the user never picked one, the system language if supported, otherwise en. */
fun currentLanguageTag(): String {
    val app = AppCompatDelegate.getApplicationLocales()
    val tag = if (!app.isEmpty) app[0]!!.language else Locale.getDefault().language
    return APP_LANGUAGES.firstOrNull { it.tag == tag }?.tag ?: "en"
}

/** Switches the app language; the activity recreates itself. */
fun applyLanguage(tag: String) {
    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
}

@Composable
fun LanguageScreen(onChosen: (String) -> Unit) {
    val current = remember { currentLanguageTag() }
    Scaffold { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(top = Spacing.xxl + Spacing.lg),
        ) {
            Column(Modifier.padding(horizontal = Spacing.xl)) {
                Icon(
                    Icons.Outlined.Language,
                    null,
                    Modifier.size(IconSize.hero),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(Spacing.md))
                Text(stringResource(R.string.language_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.language_subtitle), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(Spacing.lg))
            APP_LANGUAGES.forEach { lang ->
                ListItem(
                    headlineContent = { Text(lang.nativeName, style = MaterialTheme.typography.titleMedium) },
                    leadingContent = { RadioButton(selected = lang.tag == current, onClick = null) },
                    modifier = Modifier.clickable { onChosen(lang.tag) },
                )
            }
        }
    }
}
