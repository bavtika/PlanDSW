package app.plandsw.data

/**
 * A readable view of a USOS test tree. USOS lists every exam attempt as its own node
 * ("Ocena z wykładu - I termin", "... - II termin") and keeps empty ones; here attempts are merged
 * into one grade line and the final grade of the subject is pulled out to the top.
 */
data class TestCourseSummary(
    /** "Ocena końcowa z przedmiotu", merged over its attempts; null when there is none yet. */
    val final: GradeLine?,
    /** Points groups in site order: "Ćwiczenia", "Wykład", or a single top-level test. */
    val groups: List<PointsGroup>,
    /** Top-level grades that are not the final grade. */
    val otherGrades: List<GradeLine>,
) {
    val isEmpty: Boolean get() = final == null && groups.isEmpty() && otherGrades.isEmpty()
}

/** One grade over its attempts, e.g. "Ocena z ćwiczeń": 2 then 3,5. */
data class GradeLine(
    val label: String,
    /** Entered values in attempt order. */
    val attempts: List<String>,
    /** Nothing visible yet because the teacher hid at least one attempt. */
    val hidden: Boolean,
) {
    val current: String? get() = attempts.lastOrNull()
}

/** A points node with its own grade and the items it is made of. */
data class PointsGroup(
    val name: String,
    val value: String?,
    val max: String?,
    val hidden: Boolean,
    val comment: String,
    val grades: List<GradeLine>,
    /** Points items inside the group (tests, quizzes, nested groups). */
    val items: List<TestNode>,
)

private val attemptSuffix = Regex("""\s*-\s*(I{1,3}|IV|V|[1-5])\s*termin\s*$""", RegexOption.IGNORE_CASE)

/** "Ocena z wykładu - II termin" -> "Ocena z wykładu". */
fun gradeBaseName(name: String): String = name.replace(attemptSuffix, "").trim()

/** Merges attempts of the same grade; lines with nothing entered and nothing hidden are dropped. */
fun mergeGrades(nodes: List<TestNode>): List<GradeLine> =
    nodes.filter { it.isGrade }
        .groupBy { gradeBaseName(it.name) }
        .map { (label, attempts) ->
            val values = attempts.mapNotNull { it.value }
            GradeLine(label, values, hidden = values.isEmpty() && attempts.any { it.hidden })
        }
        .filter { it.attempts.isNotEmpty() || it.hidden }

fun summarizeTests(course: TestCourse): TestCourseSummary {
    val topGrades = mergeGrades(course.nodes)
    val final = topGrades.firstOrNull { it.label.contains("końcow", ignoreCase = true) }
        ?: topGrades.singleOrNull()
    val groups = course.nodes.filter { !it.isGrade }.map { n ->
        PointsGroup(
            name = n.name,
            value = n.value,
            max = n.max,
            hidden = n.hidden,
            comment = n.comment,
            grades = mergeGrades(n.children),
            items = n.children.filter { !it.isGrade },
        )
    }
    return TestCourseSummary(final, groups, topGrades.filter { it !== final })
}
