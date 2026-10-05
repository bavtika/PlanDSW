package app.plandsw.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.plandsw.R
import app.plandsw.data.Lesson
import app.plandsw.data.SubjectSummary
import app.plandsw.data.TargetKind
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

private val timeFmt = DateTimeFormatter.ofPattern("H:mm")

/** "Mon, Oct 13, 9:00" - when the subject's next class is. */
@Composable
private fun nextText(lesson: Lesson): String {
    val locale = appLocale()
    val fmt = remember(locale) { DateTimeFormatter.ofPattern("EEE, d MMM, H:mm", locale) }
    return stringResource(R.string.subject_next, lesson.startAt.format(fmt))
}

/** "Subjects" tab: all semester subjects with progress. */
@Composable
fun SubjectList(subjects: List<SubjectSummary>, onOpen: (SubjectSummary) -> Unit) {
    if (subjects.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(icon = Icons.AutoMirrored.Outlined.MenuBook, title = stringResource(R.string.subjects_empty))
        }
        return
    }
    val res = LocalContext.current.resources
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        items(subjects, key = { it.name }) { s ->
            Card(
                onClick = { onOpen(s) },
                modifier = Modifier.fillMaxWidth().alpha(if (s.finished) 0.6f else 1f),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            s.name,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.width(Spacing.md))
                        Text(
                            "${s.done}/${s.lessons.size}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (s.types.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            s.types.forEach { TypeTag(it, lessonTypeLabel(it, res)) }
                        }
                    }
                    ProgressBar(
                        progress = { s.done.toFloat() / s.lessons.size },
                        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xxs),
                    )
                    Text(
                        s.next?.let { nextText(it) } ?: stringResource(R.string.subject_finished),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun TypeTag(type: String, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(lessonTypeColor(type)))
        Spacer(Modifier.width(Spacing.xs))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private sealed interface DetailItem {
    data class Month(val month: YearMonth) : DetailItem
    data class Entry(val lesson: Lesson) : DetailItem
}

/** All of a subject's classes for the semester, listed by month, scrolled to the nearest one. */
@Composable
fun SubjectDetail(
    subject: SubjectSummary,
    viewer: TargetKind,
    now: LocalDateTime,
    onLessonClick: (Lesson) -> Unit,
) {
    val res = LocalContext.current.resources
    val locale = appLocale()
    var typeFilter by rememberSaveable(subject.name) { mutableStateOf<String?>(null) }
    val lessons = subject.lessons.filter { typeFilter == null || it.type.trim() == typeFilter }
    val entries = remember(lessons) {
        buildList {
            var month: YearMonth? = null
            lessons.forEach { l ->
                val m = YearMonth.from(l.date)
                if (m != month) { add(DetailItem.Month(m)); month = m }
                add(DetailItem.Entry(l))
            }
        }
    }
    val next = lessons.firstOrNull { now.isBefore(it.endAt) }
    val listState = rememberLazyListState()
    // The header is the first list item, so row indices are shifted by 1.
    LaunchedEffect(subject.name, typeFilter) {
        val idx = entries.indexOfFirst { it is DetailItem.Entry && it.lesson == next }
        if (idx > 1) listState.scrollToItem(idx) // idx + 1 (header) − 1 (month heading above the row)
    }
    val monthFmt = remember(locale) { DateTimeFormatter.ofPattern("LLLL yyyy", locale) }

    LazyColumn(
        Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        item(key = "header") {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(
                    pluralStringResource(R.plurals.lessons_count, subject.lessons.size, subject.lessons.size) +
                        " · " + formatDuration(subject.totalMinutes, res) + " · " +
                        stringResource(R.string.subject_progress, subject.done, subject.lessons.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (subject.teachers.isNotEmpty() && viewer != TargetKind.TEACHER) {
                    Text(subject.teachers.joinToString(", "), style = MaterialTheme.typography.bodyMedium)
                }
                if (subject.types.size > 1) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        item {
                            FilterChip(
                                selected = typeFilter == null,
                                onClick = { typeFilter = null },
                                label = { Text(stringResource(R.string.action_all)) },
                            )
                        }
                        items(subject.types) { t ->
                            FilterChip(
                                selected = typeFilter == t,
                                onClick = { typeFilter = if (typeFilter == t) null else t },
                                label = { Text(lessonTypeLabel(t, res)) },
                            )
                        }
                    }
                }
            }
        }
        items(entries) { item ->
            when (item) {
                is DetailItem.Month -> Text(
                    item.month.format(monthFmt).replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = Spacing.sm, bottom = Spacing.xxs),
                )
                is DetailItem.Entry -> OccurrenceRow(
                    lesson = item.lesson,
                    viewer = viewer,
                    past = !now.isBefore(item.lesson.endAt),
                    isNext = item.lesson == next,
                    onClick = { onLessonClick(item.lesson) },
                )
            }
        }
    }
}

@Composable
private fun OccurrenceRow(lesson: Lesson, viewer: TargetKind, past: Boolean, isNext: Boolean, onClick: () -> Unit) {
    val res = LocalContext.current.resources
    val locale = appLocale()
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radii.md))
            .background(colors.surfaceContainerLow)
            .then(if (isNext) Modifier.border(Stroke, colors.primary, RoundedCornerShape(Radii.md)) else Modifier)
            .clickable(onClick = onClick)
            .alpha(if (past) 0.5f else 1f)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                lesson.date.dayOfMonth.toString(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                lesson.date.dayOfWeek.getDisplayName(TextStyle.SHORT_STANDALONE, locale).replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${lesson.startAt.format(timeFmt)} – ${lesson.endAt.format(timeFmt)}",
                    style = MaterialTheme.typography.titleSmall.tabular(),
                )
                val label = lessonTypeLabel(lesson.type, res)
                if (label.isNotEmpty()) {
                    Spacer(Modifier.width(Spacing.sm))
                    TypeChip(lesson.type, label)
                }
            }
            val place = when {
                lesson.isOnline -> stringResource(R.string.lesson_online)
                viewer != TargetKind.ROOM -> lesson.room
                else -> ""
            }
            val details = listOf(place, lesson.groups).filter { it.isNotBlank() }.joinToString(" · ")
            if (details.isNotEmpty()) {
                Text(details, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            if (lesson.remarks.isNotBlank() && !lesson.isOnline) {
                Text(lesson.remarks, style = MaterialTheme.typography.bodySmall, color = colors.error)
            }
        }
    }
}
