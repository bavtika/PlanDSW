package app.plandsw.ui

import android.content.Intent
import android.content.res.Resources
import android.provider.CalendarContract
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.plandsw.R
import app.plandsw.data.Lesson
import app.plandsw.data.TargetKind
import app.plandsw.data.WARSAW
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val timeFmt = DateTimeFormatter.ofPattern("H:mm")

/** Human-readable class type name from the site's abbreviation. */
fun lessonTypeLabel(type: String, res: Resources): String {
    val t = type.trim().lowercase().replace('ć', 'c')
    val id = when {
        t.isEmpty() -> return ""
        t.startsWith("wyk") -> R.string.type_lecture
        t.startsWith("cw") -> R.string.type_exercises
        t.startsWith("lab") -> R.string.type_lab
        t.startsWith("sem") -> R.string.type_seminar
        t.startsWith("lek") -> R.string.type_language
        t.startsWith("proj") -> R.string.type_project
        t.startsWith("konw") -> R.string.type_conversatory
        t.startsWith("warsz") -> R.string.type_workshop
        t.startsWith("prakt") -> R.string.type_practice
        t.startsWith("e-l") || t.startsWith("el") -> R.string.type_elearning
        else -> return type.trim()
    }
    return res.getString(id)
}

@Composable
fun lessonTypeColor(type: String): Color {
    val c = MaterialTheme.colorScheme
    val t = type.trim().lowercase()
    return when {
        t.startsWith("wyk") -> c.primary
        t.startsWith("cw") || t.startsWith("ćw") -> c.tertiary
        t.startsWith("lab") -> c.secondary
        t.startsWith("sem") -> c.error
        else -> c.outline
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LessonCard(
    lesson: Lesson,
    viewer: TargetKind,
    now: LocalDateTime,
    onClick: () -> Unit,
) {
    val res = LocalContext.current.resources
    val accent = lessonTypeColor(lesson.type)
    val running = !now.isBefore(lesson.startAt) && now.isBefore(lesson.endAt)
    val finished = !now.isBefore(lesson.endAt)
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (running) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        // Class type color bar on the left. Draw it instead of measuring height via IntrinsicSize:
        // otherwise wrapped FlowRow lines get clipped.
        val bar = accent.copy(alpha = if (finished) 0.35f else 1f)
        Row(Modifier.drawBehind { drawRect(bar, size = Size(5.dp.toPx(), size.height)) }) {
            Column(Modifier.width(69.dp).padding(start = 17.dp, top = 14.dp)) {
                Text(
                    lesson.startAt.format(timeFmt),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    lesson.endAt.format(timeFmt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(
                Modifier
                    .weight(1f)
                    .padding(top = 12.dp, end = 14.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val label = lessonTypeLabel(lesson.type, res)
                if (label.isNotEmpty()) {
                    Text(
                        label.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = accent,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    lesson.subject,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (finished) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    when {
                        lesson.isOnline -> Meta(Icons.Outlined.Videocam, stringResource(R.string.lesson_online))
                        lesson.room.isNotBlank() && viewer != TargetKind.ROOM -> Meta(Icons.Outlined.Place, lesson.room)
                    }
                    if (lesson.teacher.isNotBlank() && viewer != TargetKind.TEACHER) Meta(Icons.Outlined.Person, lesson.teacher)
                    if (lesson.groups.isNotBlank()) Meta(Icons.Outlined.Groups, lesson.groups)
                }
                if (lesson.remarks.isNotBlank() && !lesson.isOnline) {
                    Text(
                        lesson.remarks,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (running) {
                    val total = Duration.between(lesson.startAt, lesson.endAt).toMinutes().coerceAtLeast(1)
                    val passed = Duration.between(lesson.startAt, now).toMinutes()
                    Spacer(Modifier.height(2.dp))
                    LinearProgressIndicator(
                        progress = { passed.toFloat() / total },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        stringResource(R.string.lesson_running, formatDuration(total - passed, res)),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }
    }
}

@Composable
private fun Meta(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Banner for a gap between classes. */
@Composable
fun GapRow(minutes: Long) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            stringResource(R.string.lesson_break, formatDuration(minutes, LocalContext.current.resources)),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

@Composable
fun NextLessonHint(lesson: Lesson, now: LocalDateTime) {
    val minutes = Duration.between(now, lesson.startAt).toMinutes()
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            formatDuration(minutes, LocalContext.current.resources).let { inTime ->
                if (lesson.room.isBlank()) stringResource(R.string.lesson_next, inTime)
                else stringResource(R.string.lesson_next_room, inTime, lesson.room)
            },
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

fun formatDuration(minutes: Long, res: Resources): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0L -> res.getString(R.string.duration_min, m)
        m == 0L -> res.getString(R.string.duration_h, h)
        else -> res.getString(R.string.duration_h_min, h, m)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LessonSheet(lesson: Lesson, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val dateFmt = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(appLocale())
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val label = lessonTypeLabel(lesson.type, context.resources)
            if (label.isNotEmpty()) {
                Text(label.uppercase(), style = MaterialTheme.typography.labelMedium, color = lessonTypeColor(lesson.type), fontWeight = FontWeight.Bold)
            }
            Text(lesson.subject, style = MaterialTheme.typography.headlineSmall)
            DetailRow(
                Icons.Outlined.CalendarMonth,
                "${lesson.startAt.format(dateFmt).replaceFirstChar { it.uppercase() }}\n" +
                    "${lesson.startAt.format(timeFmt)} – ${lesson.endAt.format(timeFmt)}",
            )
            if (lesson.isOnline) DetailRow(Icons.Outlined.Videocam, stringResource(R.string.lesson_remote))
            else if (lesson.room.isNotBlank()) DetailRow(Icons.Outlined.Place, lesson.room)
            if (lesson.teacher.isNotBlank()) DetailRow(Icons.Outlined.Person, lesson.teacher)
            if (lesson.groups.isNotBlank()) DetailRow(Icons.Outlined.Groups, lesson.groups)
            if (lesson.examForm.isNotBlank()) DetailRow(Icons.Outlined.School, stringResource(R.string.lesson_exam_form, lesson.examForm))
            if (lesson.remarks.isNotBlank()) DetailRow(Icons.Outlined.Info, lesson.remarks)
            FilledTonalButton(
                onClick = {
                    val zone = WARSAW
                    val intent = Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
                        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, lesson.startAt.atZone(zone).toInstant().toEpochMilli())
                        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, lesson.endAt.atZone(zone).toInstant().toEpochMilli())
                        .putExtra(CalendarContract.Events.TITLE, lesson.subject)
                        .putExtra(CalendarContract.Events.EVENT_LOCATION, lesson.room)
                        .putExtra(
                            CalendarContract.Events.DESCRIPTION,
                            listOf(label, lesson.teacher, lesson.groups, lesson.remarks).filter { it.isNotBlank() }.joinToString("\n"),
                        )
                    runCatching { context.startActivity(intent) }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.CalendarMonth, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.lesson_add_calendar))
            }
        }
    }
}

@Composable
private fun DetailRow(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 1.dp))
        Spacer(Modifier.width(16.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}
