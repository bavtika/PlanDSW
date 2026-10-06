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

    /**
     * New or changed test results. [old] == null means tests were never loaded before (first load or
     * an older cache): nothing counts as new then.
     */
    fun testChanges(old: List<TermTests>?, new: List<TermTests>): List<GradeChange> {
        if (old == null) return emptyList()
        val before = results(old).associate { it.key to it.value }
        return results(new).filter { before[it.key] != it.value }
    }

    private fun results(terms: List<TermTests>): List<GradeChange> =
        terms.flatMap { t ->
            t.courses.flatMap { c ->
                leaves(c.nodes, "").map { (path, node) -> GradeChange(t.code, c.code, c.name, path, node.value!!) }
            }
        }

    /** Nodes with a visible value, keyed by their path in the tree ("Ćwiczenia/Kolokwium nr 1"). */
    private fun leaves(nodes: List<TestNode>, prefix: String): List<Pair<String, TestNode>> =
        nodes.flatMap { n ->
            val path = TEST_PREFIX + prefix + n.name
            val self = if (n.value != null) listOf(path to n) else emptyList()
            self + leaves(n.children, prefix + n.name + "/")
        }

    /** Test results share GradeChange with grades; the prefix keeps their keys apart from class form names. */
    const val TEST_PREFIX = "test:"
}
