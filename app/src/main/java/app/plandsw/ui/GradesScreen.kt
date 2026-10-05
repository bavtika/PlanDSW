package app.plandsw.ui

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Logout
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
import app.plandsw.data.TermGrades
import app.plandsw.data.gradeKey

/** Semester from a USOS code: "2025/26L" → summer 2025/26. */
data class TermSeason(val winter: Boolean, val years: String)

private val termCodeRegex = Regex("""(\d{4}/\d{2})([ZL])""")

/** null means an unknown code: label the semester with the title from the site. */
fun termSeason(term: TermGrades): TermSeason? {
    val m = termCodeRegex.matchEntire(term.code) ?: return null
    val (years, season) = m.destructured
    return TermSeason(winter = season == "Z", years = years)
}

@Composable
private fun termLabel(term: TermGrades): String {
    val season = termSeason(term) ?: return term.title
    return stringResource(if (season.winter) R.string.grades_term_winter else R.string.grades_term_summer, season.years)
}

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
            PullToRefreshBox(
                isRefreshing = state.loading,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize().padding(padding),
            ) {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (state.loginRequired) item { SessionExpiredCard(onLogin) }
                    if (state.terms.isEmpty() && !state.loginRequired) item {
                        Text(
                            stringResource(
                                when {
                                    state.loading -> R.string.grades_loading
                                    state.fetchedAt != null -> R.string.grades_empty
                                    else -> R.string.grades_load_failed
                                }
                            ),
                            Modifier.fillMaxWidth().padding(top = 32.dp),
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    state.terms.forEach { term ->
                        item {
                            Text(
                                termLabel(term),
                                Modifier.padding(top = 8.dp),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        items(term.courses) { course -> CourseCard(term.code, course, state.highlighted) }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoginIntro(onLogin: () -> Unit, modifier: Modifier) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Outlined.Grade, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.grades_intro_title), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.grades_intro_text),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onLogin) { Text(stringResource(R.string.usos_login_button)) }
    }
}

@Composable
private fun SessionExpiredCard(onLogin: () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
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
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
        Spacer(Modifier.width(8.dp))
        Text(
            lessonTypeLabel(grade.type, res),
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (isNew) {
            NewBadge()
            Spacer(Modifier.width(8.dp))
        }
        val current = grade.current
        if (current == null) {
            Text(stringResource(R.string.grades_no_grade), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
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
            Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

/** "New grades: N" banner above the schedule, styled like [ChangesBanner]. */
@Composable
fun NewGradesBanner(count: Int, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        color = colors.secondaryContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Grade, null, tint = colors.onSecondaryContainer)
            Spacer(Modifier.width(10.dp))
            Text(
                stringResource(R.string.grades_new_banner, count),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge,
                color = colors.onSecondaryContainer,
            )
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = colors.onSecondaryContainer)
        }
    }
}
