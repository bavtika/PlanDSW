package app.plandsw.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Grade
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.plandsw.R
import app.plandsw.data.ClassGrade
import app.plandsw.data.CourseGrades
import app.plandsw.data.GradesDiff
import app.plandsw.data.TermGrades
import app.plandsw.data.TestCourse
import app.plandsw.data.TestNode
import app.plandsw.data.gradeKey

/** Semester from a USOS code: "2025/26L" → summer 2025/26. */
data class TermSeason(val winter: Boolean, val years: String)

private val termCodeRegex = Regex("""(\d{4}/\d{2})([ZL])""")

/** null means an unknown code: label the semester with the title from the site. */
fun termSeason(term: TermGrades): TermSeason? = termSeason(term.code)

fun termSeason(code: String): TermSeason? {
    val m = termCodeRegex.matchEntire(code) ?: return null
    val (years, season) = m.destructured
    return TermSeason(winter = season == "Z", years = years)
}

@Composable
private fun termLabel(code: String, title: String): String {
    val season = termSeason(code) ?: return title
    return stringResource(if (season.winter) R.string.grades_term_winter else R.string.grades_term_summer, season.years)
}

/** The two lists on the grades tab. */
private enum class GradesList { TESTS, SEMESTER }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GradesScreen(
    state: GradesState,
    onOpen: () -> Unit,
    onSeen: () -> Unit,
    onLeave: () -> Unit,
    onRefresh: () -> Unit,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    bottomBar: @Composable () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    var menuOpen by remember { mutableStateOf(false) }
    val leave by rememberUpdatedState(onLeave)
    LaunchedEffect(Unit) { onOpen() }
    // Grades that arrive while refreshing on this screen count as seen right away.
    LaunchedEffect(state.unseen) { if (state.unseen.isNotEmpty()) onSeen() }
    DisposableEffect(Unit) { onDispose { leave() } }
    val errorText = state.error?.let { stringResource(it) }
    LaunchedEffect(errorText) { errorText?.let { snackbar.showSnackbar(it) } }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = bottomBar,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.grades_title), style = MaterialTheme.typography.titleMedium)
                        val updated = updatedText(state.fetchedAt, state.loading, failed = state.error != null)
                        if (state.loggedIn && updated.isNotEmpty()) {
                            Text(updated, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                actions = {
                    if (state.loggedIn) {
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Filled.MoreVert, stringResource(R.string.grades_more_options))
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.usos_logout)) },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Logout, null) },
                                    onClick = { menuOpen = false; onLogout() },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (!state.loggedIn) {
            LoginIntro(onLogin, Modifier.padding(padding))
        } else {
            var list by rememberSaveable { mutableStateOf(GradesList.TESTS) }
            Column(Modifier.fillMaxSize().padding(padding)) {
                SingleChoiceSegmentedButtonRow(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                ) {
                    GradesList.entries.forEachIndexed { i, entry ->
                        SegmentedButton(
                            selected = list == entry,
                            onClick = { list = entry },
                            shape = SegmentedButtonDefaults.itemShape(i, GradesList.entries.size),
                            colors = segmentedColors(),
                        ) {
                            Text(
                                stringResource(if (entry == GradesList.TESTS) R.string.grades_tab_tests else R.string.grades_tab_semester),
                                maxLines = 1,
                            )
                        }
                    }
                }
                PullToRefreshBox(
                    isRefreshing = state.loading,
                    onRefresh = onRefresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        if (state.loginRequired) item { SessionExpiredCard(onLogin) }
                        if (list == GradesList.SEMESTER) semesterList(state) else testsList(state)
                    }
                }
            }
        }
    }
}

private fun LazyListScope.statusLine(state: GradesState, @StringRes empty: Int) {
    if (state.loginRequired) return
    item {
        Text(
            stringResource(
                when {
                    state.loading -> R.string.grades_loading
                    state.fetchedAt != null -> empty
                    else -> R.string.grades_load_failed
                }
            ),
            Modifier.fillMaxWidth().padding(top = Spacing.xxl),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun LazyListScope.termHeader(code: String, title: String) {
    item(key = "term_$code") {
        Text(
            termLabel(code, title),
            Modifier.padding(top = Spacing.sm),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

private fun LazyListScope.semesterList(state: GradesState) {
    if (state.terms.isEmpty()) return statusLine(state, R.string.grades_empty)
    state.terms.forEach { term ->
        termHeader(term.code, term.title)
        items(term.courses, key = { "g_${term.code}_${it.code}_${it.name}" }) { course ->
            CourseCard(term.code, course, state.highlighted)
        }
    }
}

private fun LazyListScope.testsList(state: GradesState) {
    if (state.tests.isEmpty()) return statusLine(state, R.string.tests_empty)
    state.tests.forEach { term ->
        termHeader(term.code, term.title)
        items(term.courses, key = { "t_${it.id}" }) { course ->
            TestCourseCard(term.code, course, state.highlighted)
        }
    }
}

/** A subject's test tree. Collapsed it shows the top level (totals and final grades), expanded every item. */
@Composable
private fun TestCourseCard(termCode: String, course: TestCourse, highlighted: Set<String>) {
    var expanded by rememberSaveable(course.id) { mutableStateOf(false) }
    val rows = remember(course, expanded) { flatten(course.nodes, "", 0, expanded) }
    val hasMore = course.nodes.any { it.children.isNotEmpty() }
    Card(
        onClick = { expanded = !expanded },
        enabled = hasMore,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radii.lg),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            disabledContentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Column(Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(course.name, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                if (hasMore) {
                    Icon(
                        if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        stringResource(if (expanded) R.string.tests_collapse else R.string.tests_expand),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            rows.forEach { row ->
                TestRow(row, isNew = gradeKey(termCode, course.code, row.key) in highlighted)
            }
        }
    }
}

private data class TestRowData(val node: TestNode, val depth: Int, val key: String)

/** Tree to rows; [all] = false keeps only the top level. Keys match GradesDiff paths, for "new" badges. */
private fun flatten(nodes: List<TestNode>, prefix: String, depth: Int, all: Boolean): List<TestRowData> =
    nodes.flatMap { n ->
        val row = TestRowData(n, depth, GradesDiff.TEST_PREFIX + prefix + n.name)
        listOf(row) + if (all) flatten(n.children, prefix + n.name + "/", depth + 1, true) else emptyList()
    }

/** "63.00" -> "63", "17.50" -> "17.5": the site pads points with zeros. */
internal fun trimPoints(value: String): String =
    if (value.contains('.')) value.trimEnd('0').trimEnd('.') else value

/** A failing grade: 2 in the Polish scale, or "nzal" (not passed). */
internal fun isFailingGrade(value: String): Boolean {
    val v = value.trim().lowercase()
    return v == "2" || v == "2,0" || v == "2.0" || v.startsWith("nzal") || v == "nk"
}

@Composable
private fun TestRow(row: TestRowData, isNew: Boolean) {
    val node = row.node
    val colors = MaterialTheme.colorScheme
    Column(Modifier.padding(start = Spacing.lg * row.depth)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                node.name,
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (node.children.isNotEmpty() || node.isGrade) FontWeight.SemiBold else FontWeight.Normal,
                color = if (row.depth == 0) colors.onSurface else colors.onSurfaceVariant,
            )
            if (isNew) {
                NewBadge()
                Spacer(Modifier.width(Spacing.sm))
            }
            val value = node.value
            when {
                node.hidden -> Text(stringResource(R.string.tests_hidden), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                value == null -> Text(stringResource(R.string.grades_no_grade), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                node.isGrade -> Text(
                    value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isFailingGrade(value)) colors.error else colors.onSurface,
                )
                else -> Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        trimPoints(value),
                        style = MaterialTheme.typography.titleSmall.tabular(),
                        fontWeight = FontWeight.Bold,
                    )
                    node.max?.let { max ->
                        Text(
                            " / " + trimPoints(max),
                            style = MaterialTheme.typography.bodySmall.tabular(),
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        if (node.comment.isNotBlank()) {
            Text(node.comment, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun LoginIntro(onLogin: () -> Unit, modifier: Modifier) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Outlined.Grade, null, Modifier.size(IconSize.hero), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(Spacing.lg))
        Text(stringResource(R.string.grades_intro_title), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Spacing.sm))
        Text(
            stringResource(R.string.grades_intro_text),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.xl))
        Button(onClick = onLogin) { Text(stringResource(R.string.usos_login_button)) }
    }
}

@Composable
private fun SessionExpiredCard(onLogin: () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Row(Modifier.padding(start = Spacing.lg, end = Spacing.sm, top = Spacing.xs, bottom = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.grades_session_expired),
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            TextButton(onClick = onLogin) { Text(stringResource(R.string.grades_sign_in_again)) }
        }
    }
}

@Composable
private fun CourseCard(termCode: String, course: CourseGrades, highlighted: Set<String>) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radii.lg),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(course.name, style = MaterialTheme.typography.titleSmall)
            course.classes.forEach { grade ->
                ClassGradeRow(grade, isNew = gradeKey(termCode, course.code, grade.type) in highlighted)
            }
        }
    }
}

@Composable
private fun ClassGradeRow(grade: ClassGrade, isNew: Boolean) {
    val res = LocalContext.current.resources
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(lessonTypeColor(grade.type)))
        Spacer(Modifier.width(Spacing.sm))
        Text(
            lessonTypeLabel(grade.type, res),
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (isNew) {
            NewBadge()
            Spacer(Modifier.width(Spacing.sm))
        }
        val current = grade.current
        if (current == null) {
            Text(stringResource(R.string.grades_no_grade), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            // Earlier attempts in grey: "2 → 3".
            if (grade.attempts.size > 1) {
                Text(
                    grade.attempts.dropLast(1).joinToString(" → ", postfix = " → "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                current,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (grade.failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun NewBadge() {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(50)) {
        Text(
            stringResource(R.string.grades_new_badge),
            Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xxs),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

/** "New grades: N" banner above the schedule, styled like [ChangesBanner]. */
@Composable
fun NewGradesBanner(count: Int, onClick: () -> Unit) =
    InfoBanner(Icons.Outlined.Grade, stringResource(R.string.grades_new_banner, count), onClick)
