package app.plandsw.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.plandsw.R
import app.plandsw.data.ChangeKind
import app.plandsw.data.Lesson
import app.plandsw.data.ScheduleChange
import java.time.format.DateTimeFormatter

@Composable
fun ChangesBanner(count: Int, onClick: () -> Unit) =
    InfoBanner(Icons.Outlined.NotificationsActive, stringResource(R.string.changes_banner, count), onClick)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangesSheet(changes: List<ScheduleChange>, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            stringResource(R.string.changes_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(horizontal = Spacing.xl),
        )
        LazyColumn(contentPadding = PaddingValues(bottom = Spacing.xl)) {
            items(changes) { change ->
                ListItem(
                    leadingContent = { Icon(changeIcon(change.kind), null) },
                    headlineContent = { Text(changeText(change)) },
                )
            }
        }
    }
}

private fun changeIcon(kind: ChangeKind) = when (kind) {
    ChangeKind.MOVED -> Icons.Outlined.EventRepeat
    ChangeKind.CANCELLED -> Icons.Outlined.EventBusy
    ChangeKind.ADDED -> Icons.Outlined.EventAvailable
    ChangeKind.ROOM -> Icons.Outlined.Place
}

@Composable
private fun changeText(change: ScheduleChange): String {
    val fmt = DateTimeFormatter.ofPattern("EEE d MMM, H:mm", appLocale())
    fun at(l: Lesson) = l.startAt.format(fmt)
    val old = change.old
    val new = change.new
    return when {
        change.kind == ChangeKind.MOVED && old != null && new != null ->
            stringResource(R.string.change_moved, new.subject, at(old), at(new))
        change.kind == ChangeKind.ROOM && old != null && new != null ->
            stringResource(R.string.change_room, new.subject, at(new), old.room.ifBlank { "—" }, new.room.ifBlank { "—" })
        change.kind == ChangeKind.CANCELLED && old != null ->
            stringResource(R.string.change_cancelled, old.subject, at(old))
        new != null -> stringResource(R.string.change_added, new.subject, at(new))
        else -> ""
    }
}
