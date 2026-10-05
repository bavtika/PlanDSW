package app.plandsw.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.MeetingRoom
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.plandsw.R
import app.plandsw.data.Lesson
import app.plandsw.data.ScheduleChange
import app.plandsw.data.Target
import app.plandsw.data.TargetKind
import app.plandsw.data.WARSAW
import app.plandsw.data.summarizeSubjects
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

fun kindIcon(kind: TargetKind) = when (kind) {
    TargetKind.GROUP -> Icons.Outlined.Groups
    TargetKind.TEACHER -> Icons.Outlined.Person
    TargetKind.ROOM -> Icons.Outlined.MeetingRoom
    TargetKind.TOK -> Icons.Outlined.School
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    state: ScheduleState,
    favorites: List<Target>,
    /** Back to your own schedule; null when it is already shown. */
    onHome: (() -> Unit)?,
    onToggleFavorite: (Target) -> Unit,
    onRefresh: () -> Unit,
    onSearch: () -> Unit,
    onSetup: () -> Unit,
    onGroups: (Set<String>) -> Unit,
    onDismissGroupPrompt: () -> Unit,
    tab: MainTab,
    onTab: (MainTab) -> Unit,
    newGrades: Int,
    changes: List<ScheduleChange>,
    onChangesSeen: () -> Unit,
    update: UpdateState,
    onInstallUpdate: () -> Unit,
    onHideUpdate: () -> Unit,
) {
    val target = state.target
    if (target == null) {
        WelcomeScreen(onSetup)
        return
    }
    val context = LocalContext.current
    var selectedLesson by remember { mutableStateOf<Lesson?>(null) }
    var groupsOpen by remember { mutableStateOf(false) }
    var changesOpen by remember { mutableStateOf(false) }
    val isFavorite = favorites.any { it.key == target.key }

    val subjectsTab = tab == MainTab.SUBJECTS
    // Reset the open subject when the schedule changes.
    var openSubjectName by rememberSaveable(target.key) { mutableStateOf<String?>(null) }
    val now by rememberNow()
    val subjects = remember(state.lessonsByDate, now) {
        summarizeSubjects(state.lessonsByDate.values.flatten(), now)
    }
    val openSubject = openSubjectName?.let { name -> subjects.firstOrNull { it.name == name } }
        ?.takeIf { subjectsTab }

    BackHandler(enabled = subjectsTab) {
        if (openSubject != null) openSubjectName = null else onTab(MainTab.SCHEDULE)
    }
    BackHandler(enabled = !subjectsTab && onHome != null) { onHome?.invoke() }

    Scaffold(
        topBar = {
            if (openSubject != null) {
                TopAppBar(
                    title = {
                        Text(openSubject.name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                    },
                    navigationIcon = {
                        IconButton(onClick = { openSubjectName = null }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                        }
                    },
                )
            } else TopAppBar(
                title = {
                    Column {
                        Text(target.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                        Text(
                            listOfNotNull(
                                groupSummary(state),
                                updatedText(state.fetchedAt, state.loading, failed = state.error != null)
                                    .takeIf { it.isNotEmpty() },
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = {
                    if (onHome != null) {
                        IconButton(onClick = onHome) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                        }
                    }
                },
                actions = {
                    if (target.kind == TargetKind.TEACHER && target.subtitle.contains('@')) {
                        IconButton(onClick = {
                            runCatching { context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${target.subtitle}"))) }
                        }) { Icon(Icons.Outlined.Email, stringResource(R.string.action_email)) }
                    }
                    if (target.kind == TargetKind.TOK) {
                        if (state.groups.size > 1) {
                            IconButton(onClick = { groupsOpen = true }) {
                                Icon(Icons.Outlined.FilterList, stringResource(R.string.groups_title))
                            }
                        }
                    } else {
                        IconButton(onClick = { onToggleFavorite(target) }) {
                            if (isFavorite) Icon(Icons.Filled.Star, stringResource(R.string.favorite_remove), tint = MaterialTheme.colorScheme.secondary)
                            else Icon(Icons.Outlined.StarOutline, stringResource(R.string.favorite_add))
                        }
                    }
                    IconButton(onClick = onSearch) { Icon(Icons.Outlined.Search, stringResource(R.string.action_search)) }
                },
            )
        },
        bottomBar = {
            MainTabsBar(tab, newGrades) {
                // Tapping "Subjects" again returns to the subject list.
                if (it == MainTab.SUBJECTS && subjectsTab) openSubjectName = null
                onTab(it)
            }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.loading,
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (state.fetchedAt == null) {
                // First load: show only the progress indicator / error.
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        if (state.loading) stringResource(R.string.schedule_loading) else state.error?.let { stringResource(it) }.orEmpty(),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!state.loading && state.error != null) {
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = onRefresh) { Text(stringResource(R.string.action_retry)) }
                    }
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    if (update.available != null && !update.bannerHidden) UpdateBanner(update, onInstallUpdate, onHideUpdate)
                    if (changes.isNotEmpty()) ChangesBanner(changes.size) { changesOpen = true }
                    if (newGrades > 0) NewGradesBanner(newGrades, onClick = { onTab(MainTab.GRADES) })
                    key(target.key) {
                        when {
                            !subjectsTab -> DayPager(state, target.kind, now, onLessonClick = { selectedLesson = it })
                            openSubject != null -> SubjectDetail(openSubject, target.kind, now, onLessonClick = { selectedLesson = it })
                            else -> SubjectList(subjects, onOpen = { openSubjectName = it.name })
                        }
                    }
                }
            }
        }
    }

    selectedLesson?.let { LessonSheet(it, onDismiss = { selectedLesson = null }) }

    if (changesOpen) {
        ChangesSheet(changes, onDismiss = { changesOpen = false; onChangesSeen() })
    }

    if (groupsOpen || state.askGroups) {
        GroupPickerSheet(
            groups = state.groups,
            selected = state.selectedGroups,
            firstTime = state.askGroups,
            onDone = {
                onGroups(it)
                groupsOpen = false
                if (state.askGroups) onDismissGroupPrompt()
            },
            onDismiss = {
                groupsOpen = false
                if (state.askGroups) onDismissGroupPrompt()
            },
        )
    }
}

/** "WykS, Ćw1S" - which tok groups are currently shown (if not all are selected). */
private fun groupSummary(state: ScheduleState): String? {
    if (state.groups.size < 2 || state.selectedGroups.size == state.groups.size) return null
    return state.groups
        .filter { it.name in state.selectedGroups && groupCategory(it.short) != GroupCategory.LECTURE }
        .joinToString(", ") { groupCode(it.short) }
        .ifEmpty { null }
}

/** "Updated 5 min ago" - subtitle for schedule and grades; [failed] - refresh failed, cache is shown. */
@Composable
fun updatedText(fetchedAt: Long?, loading: Boolean, failed: Boolean): String {
    if (loading) {
        return stringResource(if (fetchedAt == null) R.string.status_loading else R.string.status_refreshing)
    }
    if (fetchedAt == null) return ""
    val minutes = (System.currentTimeMillis() - fetchedAt) / 60_000
    val updated = when {
        minutes < 1 -> stringResource(R.string.updated_just_now)
        minutes < 60 -> stringResource(R.string.updated_minutes, minutes)
        minutes < 24 * 60 -> stringResource(R.string.updated_hours, minutes / 60)
        else -> stringResource(
            R.string.updated_date,
            Instant.ofEpochMilli(fetchedAt).atZone(WARSAW).format(DateTimeFormatter.ofPattern("d MMM, H:mm", appLocale())),
        )
    }
    // Cache exists but refresh failed: don't alarm the user, just note it quietly in the subtitle.
    return if (failed) updated + " · " + stringResource(R.string.update_failed_suffix) else updated
}

/** Current Warsaw time, updated every 30 seconds. */
@Composable
private fun rememberNow() = produceState(LocalDateTime.now(WARSAW)) {
    while (true) {
        delay(30_000)
        value = LocalDateTime.now(WARSAW)
    }
}

@Composable
private fun DayPager(state: ScheduleState, viewer: TargetKind, now: LocalDateTime, onLessonClick: (Lesson) -> Unit) {
    val today = LocalDate.now(WARSAW)
    val dates = state.lessonsByDate.keys
    val first = listOfNotNull(state.from, today, dates.minOrNull()).min()
        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val last = listOfNotNull(state.to, today, dates.maxOrNull()).max()
        .with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
    val pageCount = ChronoUnit.DAYS.between(first, last).toInt() + 1

    var savedPage by rememberSaveable { mutableStateOf(ChronoUnit.DAYS.between(first, today).toInt()) }
    val pager = rememberPagerState(initialPage = savedPage.coerceIn(0, pageCount - 1)) { pageCount }
    val scope = rememberCoroutineScope()
    LaunchedEffect(pager.currentPage) { savedPage = pager.currentPage }

    val shownDate = first.plusDays(pager.targetPage.toLong())
    fun goTo(date: LocalDate) {
        val page = ChronoUnit.DAYS.between(first, date).toInt().coerceIn(0, pageCount - 1)
        scope.launch { pager.animateScrollToPage(page) }
    }

    Column(Modifier.fillMaxSize()) {
        WeekStrip(
            selected = shownDate,
            today = today,
            hasLessons = { state.lessonsByDate.containsKey(it) },
            onSelect = ::goTo,
        )
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), beyondViewportPageCount = 1) { page ->
            val date = first.plusDays(page.toLong())
            DayPage(
                date = date,
                lessons = state.lessonsByDate[date].orEmpty(),
                viewer = viewer,
                now = now,
                outsideSemester = date.isBefore(state.from) || date.isAfter(state.to),
                onLessonClick = onLessonClick,
            )
        }
    }
}

@Composable
private fun WeekStrip(
    selected: LocalDate,
    today: LocalDate,
    hasLessons: (LocalDate) -> Boolean,
    onSelect: (LocalDate) -> Unit,
) {
    val locale = appLocale()
    val monday = selected.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val monthFmt = remember(locale) { DateTimeFormatter.ofPattern("LLLL yyyy", locale) }
    Column(Modifier.padding(horizontal = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onSelect(selected.minusWeeks(1)) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.week_prev))
            }
            Text(
                selected.format(monthFmt).replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
            )
            if (selected != today) {
                TextButton(onClick = { onSelect(today) }) {
                    Icon(Icons.Outlined.EventAvailable, null, Modifier.size(18.dp))
                    Spacer(Modifier.size(4.dp))
                    Text(stringResource(R.string.today))
                }
            }
            IconButton(onClick = { onSelect(selected.plusWeeks(1)) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.week_next))
            }
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            for (i in 0..6) {
                val day = monday.plusDays(i.toLong())
                val isSelected = day == selected
                val isToday = day == today
                val colors = MaterialTheme.colorScheme
                Column(
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 2.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isSelected) colors.primary else colors.surface)
                        .then(
                            if (isToday && !isSelected) Modifier.border(1.5.dp, colors.primary, RoundedCornerShape(16.dp))
                            else Modifier
                        )
                        .clickable { onSelect(day) }
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val fg = if (isSelected) colors.onPrimary else colors.onSurface
                    Text(
                        day.dayOfWeek.getDisplayName(TextStyle.SHORT_STANDALONE, locale).replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) fg else colors.onSurfaceVariant,
                    )
                    Text(
                        day.dayOfMonth.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = fg,
                    )
                    Box(
                        Modifier
                            .padding(top = 3.dp)
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    !hasLessons(day) -> Color.Transparent
                                    isSelected -> colors.onPrimary
                                    else -> colors.secondary
                                }
                            )
                    )
                }
            }
        }
        HorizontalDivider()
    }
}

@Composable
private fun DayPage(
    date: LocalDate,
    lessons: List<Lesson>,
    viewer: TargetKind,
    now: LocalDateTime,
    outsideSemester: Boolean,
    onLessonClick: (Lesson) -> Unit,
) {
    val locale = appLocale()
    val title = remember(date, locale) {
        DateTimeFormatter.ofPattern("EEEE, d MMMM", locale).format(date).replaceFirstChar { it.uppercase() }
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                if (lessons.isNotEmpty()) {
                    val total = lessons.sumOf { Duration.between(it.startAt, it.endAt).toMinutes() }
                    Text(
                        pluralStringResource(R.plurals.lessons_count, lessons.size, lessons.size) +
                            " · " + formatDuration(total, LocalContext.current.resources),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        val next = lessons.firstOrNull { it.startAt.isAfter(now) }
        if (date == now.toLocalDate() && next != null && lessons.none { !now.isBefore(it.startAt) && now.isBefore(it.endAt) }) {
            item { NextLessonHint(next, now) }
        }
        if (lessons.isEmpty()) {
            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 64.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(if (outsideSemester) "📚" else "🎉", style = MaterialTheme.typography.displayMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(if (outsideSemester) R.string.outside_semester else R.string.no_classes),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        itemsIndexed(lessons, key = { i, l -> "$i${l.start}${l.subject}" }) { i, lesson ->
            val prev = lessons.getOrNull(i - 1)
            if (prev != null) {
                val gap = Duration.between(prev.endAt, lesson.startAt).toMinutes()
                if (gap >= 30) GapRow(gap)
            }
            LessonCard(lesson, viewer, now, onClick = { onLessonClick(lesson) })
        }
    }
}

@Composable
private fun WelcomeScreen(onSetup: () -> Unit) {
    Scaffold { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("📅", style = MaterialTheme.typography.displayLarge)
            Spacer(Modifier.height(16.dp))
            Text("Plan DSW", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.welcome_text),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = onSetup) {
                Icon(Icons.Outlined.School, null)
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.welcome_button))
            }
        }
    }
}
