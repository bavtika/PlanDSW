package app.plandsw.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// DSW colors: orange and dark blue from the site.
private val Orange = Color(0xFFFFAD00)
private val Navy = Color(0xFF04427C)

private val LightColors = lightColorScheme(
    primary = Navy,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3E4FF),
    onPrimaryContainer = Color(0xFF001C38),
    secondary = Color(0xFF7A5900),
    secondaryContainer = Color(0xFFFFDEA6),
    onSecondaryContainer = Color(0xFF261900),
    tertiary = Color(0xFF00696E),
    tertiaryContainer = Color(0xFF9CF0F5),
    background = Color(0xFFFEFCF6),
    surface = Color(0xFFFEFCF6),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA3C9FF),
    onPrimary = Color(0xFF00315B),
    primaryContainer = Color(0xFF004881),
    secondary = Orange,
    onSecondary = Color(0xFF402D00),
    secondaryContainer = Color(0xFF5C4300),
    tertiary = Color(0xFF80D4D9),
    tertiaryContainer = Color(0xFF004F53),
)

@Composable
fun PlanTheme(dark: Boolean, content: @Composable () -> Unit) {
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}

/** Theme from settings -> whether it is dark right now. */
@Composable
fun isDarkTheme(mode: String): Boolean = when (mode) {
    "light" -> false
    "dark" -> true
    else -> isSystemInDarkTheme()
}
