package app.plandsw.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.plandsw.R
import app.plandsw.data.GradeLine
import app.plandsw.data.GradesDiff
import app.plandsw.data.PointsGroup
import app.plandsw.data.TestCourse
import app.plandsw.data.TestNode
import app.plandsw.data.gradeKey
import app.plandsw.data.summarizeTests

/** "63.00" -> "63", "17.50" -> "17.5": the site pads points with zeros. */
internal fun trimPoints(value: String): String =
    if (value.contains('.')) value.trimEnd('0').trimEnd('.') else value

/** A failing grade: 2 in the Polish scale, or "nzal" (not passed). */
internal fun isFailingGrade(value: String): Boolean {
    val v = value.trim().lowercase()
    return v == "2" || v == "2,0" || v == "2.0" || v.startsWith("nzal") || v == "nk"
}

private fun ratio(value: String?, max: String?): Float? {
    val v = value?.replace(',', '.')?.toFloatOrNull() ?: return null
    val m = max?.replace(',', '.')?.toFloatOrNull()?.takeIf { it > 0f } ?: return null
    return (v / m).coerceIn(0f, 1f)
}

/**
 * One subject in the "Sprawdziany" list. The eye lands on the final grade (top right), then on the
 * points of each class form with a progress bar and its grade; individual tests are one tap away.
 */
@Composable
fun TestCourseCard(termCode: String, course: TestCourse, highlighted: Set<String>) {
    val summary = remember(course) { summarizeTests(course) }
    var expanded by rememberSaveable(course.id) { mutableStateOf(false) }
    val hasDetails = summary.groups.any { it.items.isNotEmpty() || it.comment.isNotBlank() }
    val isNew = highlighted.any { it.startsWith(gradeKey(termCode, course.code, GradesDiff.TEST_PREFIX)) }
    val colors = MaterialTheme.colorScheme

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radii.lg),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow),
    ) {
        Column(Modifier.padding(top = Spacing.lg, start = Spacing.lg, end = Spacing.lg, bottom = if (hasDetails) Spacing.xs else Spacing.lg)) {
            // Header: subject and its final grade.
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                    Text(course.name, style = MaterialTheme.typography.titleMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    summary.final?.let { f ->
                        Text(
                            stringResource(R.string.tests_final) +
                                if (f.current == null) ": " + stringResource(R.string.tests_hidden) else attemptsCaption(f),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                        )
                    }
                    if (isNew) NewBadge()
                }
                // A hidden final grade is said in the caption; an empty badge would read as an error.
                summary.final?.takeIf { it.current != null }?.let {
                    Spacer(Modifier.width(Spacing.md))
                    FinalGradeBadge(it)
                }
            }

            if (summary.isEmpty) {
                Text(
                    stringResource(R.string.tests_no_results),
                    Modifier.padding(top = Spacing.sm),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }

            summary.groups.forEach { group ->
                Spacer(Modifier.size(Spacing.md))
                HorizontalDivider(color = colors.outlineVariant)
                Spacer(Modifier.size(Spacing.md))
                GroupBlock(group, expanded)
            }
            summary.otherGrades.filter { it.current != null }.forEach { line ->
                Spacer(Modifier.size(Spacing.sm))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(line.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    GradeChip(line)
                }
            }

            if (hasDetails) {
                TextButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(stringResource(if (expanded) R.string.tests_collapse else R.string.tests_expand))
                    Spacer(Modifier.width(Spacing.xs))
                    Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, Modifier.size(IconSize.md))
                }
            }
        }
    }
}

/** ": 2 → 3,5" after "Final grade" when there were retakes, otherwise nothing extra. */
private fun attemptsCaption(line: GradeLine): String =
    if (line.attempts.size > 1) ": " + line.attempts.joinToString(" → ") else ""

@Composable
private fun FinalGradeBadge(line: GradeLine) {
    val colors = MaterialTheme.colorScheme
    val value = line.current ?: return
    val failing = isFailingGrade(value)
    Surface(
        shape = RoundedCornerShape(Radii.md),
        color = if (failing) colors.errorContainer else colors.primaryContainer,
        contentColor = if (failing) colors.onErrorContainer else colors.onPrimaryContainer,
    ) {
        Box(Modifier.defaultMinSize(minWidth = 52.dp, minHeight = 52.dp).padding(horizontal = Spacing.sm), contentAlignment = Alignment.Center) {
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        }
    }
}

/** Small tonal chip with an entered grade: "Grade 3,5" or "Grade 2 → 3,5". */
@Composable
private fun GradeChip(line: GradeLine) {
    val colors = MaterialTheme.colorScheme
    val value = line.current ?: return
    val failing = isFailingGrade(value)
    Surface(
        shape = RoundedCornerShape(Radii.sm),
        color = if (failing) colors.errorContainer else colors.surfaceContainerHighest,
        contentColor = if (failing) colors.onErrorContainer else colors.onSurface,
    ) {
        Row(Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xxs), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.tests_grade) + " ", style = MaterialTheme.typography.labelMedium)
            if (line.attempts.size > 1) {
                Text(line.attempts.dropLast(1).joinToString(" → ", postfix = " → "), style = MaterialTheme.typography.labelMedium)
            }
            Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
    }
}

/** "Ćwiczenia  21 / 42", a bar, the form's grade, and (expanded) the tests it is made of. */
@Composable
private fun GroupBlock(group: PointsGroup, expanded: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(group.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            PointsText(group.value, group.max, group.hidden, emphasized = true)
        }
        ratio(group.value, group.max)?.let { ProgressBar(progress = { it }, modifier = Modifier.fillMaxWidth()) }
        // Hidden form grades add nothing next to hidden points; only entered grades get a chip.
        val grades = group.grades.filter { it.current != null }
        if (grades.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                grades.forEach { GradeChip(it) }
            }
        }
        if (expanded) {
            if (group.comment.isNotBlank()) Comment(group.comment, depth = 0)
            group.items.forEach { ItemRows(it, depth = 0) }
        }
    }
    if (!expanded && group.items.isEmpty() && group.comment.isNotBlank()) {
        Spacer(Modifier.size(Spacing.xs))
        Comment(group.comment, depth = 0)
    }
}

/** One test inside a form, with its own sub-items indented below it. */
@Composable
private fun ItemRows(node: TestNode, depth: Int) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.padding(start = Spacing.md * (depth + 1))) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                node.name,
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                fontWeight = if (node.children.isNotEmpty()) FontWeight.SemiBold else FontWeight.Normal,
            )
            if (node.isGrade) {
                Text(node.value ?: stringResource(R.string.grades_no_grade), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            } else {
                PointsText(node.value, node.max, node.hidden, emphasized = false)
            }
        }
        // The comment belongs to this row, so it sits right under it.
        if (node.comment.isNotBlank()) Comment(node.comment, depth = 0)
    }
    node.children.forEach { ItemRows(it, depth + 1) }
}

@Composable
private fun Comment(text: String, depth: Int) {
    Text(
        text,
        Modifier.padding(start = Spacing.md * depth),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** "21 / 42" with the score in front; "hidden" or "no grade" when there is nothing to show. */
@Composable
private fun PointsText(value: String?, max: String?, hidden: Boolean, emphasized: Boolean) {
    val colors = MaterialTheme.colorScheme
    when {
        hidden -> Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.VisibilityOff, null, Modifier.size(IconSize.sm), tint = colors.onSurfaceVariant)
            Spacer(Modifier.width(Spacing.xs))
            Text(stringResource(R.string.tests_hidden), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }
        value == null -> Text(stringResource(R.string.tests_not_entered), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        else -> Row(verticalAlignment = Alignment.Bottom) {
            Text(
                trimPoints(value),
                style = (if (emphasized) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium).tabular(),
                fontWeight = if (emphasized) FontWeight.Bold else FontWeight.SemiBold,
            )
            if (max != null) {
                Text(
                    " / " + trimPoints(max),
                    style = MaterialTheme.typography.bodySmall.tabular(),
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
}
