package app.plandsw.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Icon sizes used across the app. */
object IconSize {
    val xs = 14.dp
    val sm = 16.dp
    val md = 18.dp
    val lg = 24.dp
    val hero = 40.dp
}

/**
 * The one banner style above the schedule (schedule changes, new grades).
 * Tonal primary surface, icon + text, chevron because the whole banner opens something.
 */
@Composable
fun InfoBanner(icon: ImageVector, text: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        color = colors.primaryContainer,
        contentColor = colors.onPrimaryContainer,
        shape = RoundedCornerShape(Radii.md),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.xs),
    ) {
        Row(
            Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, Modifier.size(IconSize.lg))
            Spacer(Modifier.size(Spacing.md))
            Text(text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
        }
    }
}

/** Empty / informational state: an icon in a tonal circle, a title and an optional line of explanation. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier.fillMaxWidth().padding(horizontal = Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Box(
            Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(colors.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, Modifier.size(IconSize.hero), tint = colors.primary)
        }
        Spacer(Modifier.height(Spacing.xs))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = colors.onSurface,
            textAlign = TextAlign.Center,
        )
        if (body != null) {
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 360.dp),
            )
        }
        if (action != null) {
            Spacer(Modifier.height(Spacing.sm))
            action()
        }
    }
}

/** Determinate progress in brand colours (the default track would take the amber accent). */
@Composable
fun ProgressBar(progress: () -> Float, modifier: Modifier = Modifier) {
    LinearProgressIndicator(
        progress = progress,
        modifier = modifier,
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    )
}

/** Indeterminate loading line under a toolbar or a field. */
@Composable
fun LoadingBar(modifier: Modifier = Modifier) {
    LinearProgressIndicator(
        modifier = modifier,
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    )
}
