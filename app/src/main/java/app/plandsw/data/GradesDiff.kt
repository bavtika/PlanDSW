package app.plandsw.data

/** Which grades appeared or changed between two USOS loads. */
object GradesDiff {
    /** [old] == null means the first load: nothing counts as "new", otherwise the banner would show every grade at once. */
    fun changes(old: List<TermGrades>?, new: List<TermGrades>): List<GradeChange> {
        if (old == null) return emptyList()
        val before = graded(old).associate { (change, attempts) -> change.key to attempts }
        // Compare all attempts, not just the current grade: a retake ending in the same "2" is news too.
        return graded(new).filter { (change, attempts) -> before[change.key] != attempts }.map { it.first }
    }

    private fun graded(terms: List<TermGrades>): List<Pair<GradeChange, List<String>>> =
        terms.flatMap { t ->
            t.courses.flatMap { c ->
                c.classes.mapNotNull { g ->
                    g.current?.let { GradeChange(t.code, c.code, c.name, g.type, it) to g.attempts }
                }
            }
        }
}
