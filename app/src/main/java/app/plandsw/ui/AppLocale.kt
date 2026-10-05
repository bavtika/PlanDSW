package app.plandsw.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import java.util.Locale

/** App language (respecting the user's choice) - for dates and weekdays. */
@Composable
fun appLocale(): Locale = LocalConfiguration.current.locales[0]
