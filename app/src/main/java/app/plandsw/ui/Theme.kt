package app.plandsw.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

/*
 * The single source of truth for the app's look. Every screen and the widget read colours from here;
 * no other file defines a colour. Brand: DSW navy as primary, DSW amber as the one accent.
 * Every text/background pair below was checked against WCAG AA (4.5:1 text, 3:1 non-text).
 */

val LightColors: ColorScheme = lightColorScheme(
    primary = Color(0xFF04427C),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD6E3FF),
    onPrimaryContainer = Color(0xFF001B3D),
    inversePrimary = Color(0xFFA9C7FF),
    secondary = Color(0xFF7A5900),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDFA0),
    onSecondaryContainer = Color(0xFF261A00),
    tertiary = Color(0xFF006A60),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFC9EDE6),
    onTertiaryContainer = Color(0xFF00423A),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFAF9F6),
    onBackground = Color(0xFF1B1C1F),
    surface = Color(0xFFFAF9F6),
    onSurface = Color(0xFF1B1C1F),
    surfaceVariant = Color(0xFFE1E2EC),
    onSurfaceVariant = Color(0xFF45474E),
    surfaceTint = Color(0xFF04427C),
    inverseSurface = Color(0xFF303033),
    inverseOnSurface = Color(0xFFF2F0F4),
    outline = Color(0xFF74777F),
    outlineVariant = Color(0xFFC4C6CF),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFAF9F6),
    surfaceDim = Color(0xFFDBDAD7),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF3F2EF),
    surfaceContainer = Color(0xFFEDECE9),
    surfaceContainerHigh = Color(0xFFE8E6E3),
    surfaceContainerHighest = Color(0xFFE2E1DE),
)

val DarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFFA9C7FF),
    onPrimary = Color(0xFF003063),
    primaryContainer = Color(0xFF0E4683),
    onPrimaryContainer = Color(0xFFD6E3FF),
    inversePrimary = Color(0xFF04427C),
    secondary = Color(0xFFFFB923),
    onSecondary = Color(0xFF412D00),
    secondaryContainer = Color(0xFF5D4200),
    onSecondaryContainer = Color(0xFFFFDFA0),
    tertiary = Color(0xFF80D5C9),
    onTertiary = Color(0xFF003731),
    tertiaryContainer = Color(0xFF0C4A43),
    onTertiaryContainer = Color(0xFFB7F0E6),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF121318),
    onBackground = Color(0xFFE3E2E6),
    surface = Color(0xFF121318),
    onSurface = Color(0xFFE3E2E6),
    surfaceVariant = Color(0xFF44474E),
    onSurfaceVariant = Color(0xFFC4C6CF),
    surfaceTint = Color(0xFFA9C7FF),
    inverseSurface = Color(0xFFE3E2E6),
    inverseOnSurface = Color(0xFF2F3035),
    outline = Color(0xFF8E9099),
    outlineVariant = Color(0xFF44474E),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF38393E),
    surfaceDim = Color(0xFF121318),
    surfaceContainerLowest = Color(0xFF0D0E12),
    surfaceContainerLow = Color(0xFF1A1B20),
    surfaceContainer = Color(0xFF1E1F24),
    surfaceContainerHigh = Color(0xFF292A2F),
    surfaceContainerHighest = Color(0xFF34353A),
)

/** Colours of one class type: a tonal chip ([container] + [onContainer]) and a small [accent] mark. */
@Immutable
data class TypeColors(val container: Color, val onContainer: Color, val accent: Color)

@Immutable
data class LessonTypePalette(
    val lecture: TypeColors,
    val exercise: TypeColors,
    val lab: TypeColors,
    val seminar: TypeColors,
    val other: TypeColors,
)

private val LightTypes = LessonTypePalette(
    lecture = TypeColors(Color(0xFFD6E3FF), Color(0xFF0B3A6E), Color(0xFF2A5EA8)),
    exercise = TypeColors(Color(0xFFC9EDE6), Color(0xFF00423A), Color(0xFF00897B)),
    lab = TypeColors(Color(0xFFFFE2B0), Color(0xFF4A3200), Color(0xFFA06E00)),
    seminar = TypeColors(Color(0xFFEBDDFF), Color(0xFF3E1F6E), Color(0xFF7A55B5)),
    other = TypeColors(Color(0xFFE4E2E8), Color(0xFF45464F), Color(0xFF74777F)),
)

private val DarkTypes = LessonTypePalette(
    lecture = TypeColors(Color(0xFF1D3F6C), Color(0xFFD6E3FF), Color(0xFFA9C7FF)),
    exercise = TypeColors(Color(0xFF0C4A43), Color(0xFFB7F0E6), Color(0xFF80D5C9)),
    lab = TypeColors(Color(0xFF553B00), Color(0xFFFFDFA0), Color(0xFFFFB923)),
    seminar = TypeColors(Color(0xFF4A2F78), Color(0xFFEBDDFF), Color(0xFFD2BBFF)),
    other = TypeColors(Color(0xFF3A3B42), Color(0xFFE3E2E6), Color(0xFFA3A5AE)),
)

private val LocalLessonTypes = staticCompositionLocalOf { LightTypes }

/** Spacing scale (4dp rhythm). Outer padding > card padding > gaps inside a card. */
object Spacing {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 64.dp
}

/** Corner radii: one soft language for the whole app. */
object Radii {
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 28.dp
}

/** Line thickness for selection and "now" outlines. */
val Stroke = 2.dp

/** Tabular figures so that times line up in a column. */
fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = "tnum")

object PlanTheme {
    val lessonTypes: LessonTypePalette
        @Composable @ReadOnlyComposable get() = LocalLessonTypes.current
}

@Composable
fun PlanTheme(dark: Boolean, content: @Composable () -> Unit) {
    // Brand colours on every Android version: wallpaper-based dynamic colour would hide the DSW palette.
    CompositionLocalProvider(LocalLessonTypes provides if (dark) DarkTypes else LightTypes) {
        MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
    }
}

/** Theme from settings -> whether it is dark right now. */
@Composable
fun isDarkTheme(mode: String): Boolean = when (mode) {
    "light" -> false
    "dark" -> true
    else -> isSystemInDarkTheme()
}
