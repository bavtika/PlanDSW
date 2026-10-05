package app.plandsw.data

import java.time.LocalDate

/**
 * Compares the old and new schedule: what was moved, cancelled, added, or changed rooms.
 * Only classes from [today] onwards and only groups in `groups` are considered (null — all groups;
 * classes without groups apply to everyone).
 */
object ScheduleDiff {
    fun diff(old: List<Lesson>, new: List<Lesson>, groups: Set<String>?, today: LocalDate): List<ScheduleChange> {
        fun relevant(l: Lesson) = !l.date.isBefore(today) &&
            (groups == null || l.groupNames.isEmpty() || l.groupNames.any { it in groups })

        val oldLeft = old.filter(::relevant).sortedBy { it.start }.toMutableList()
        val newLeft = new.filter(::relevant).sortedBy { it.start }.toMutableList()
        val changes = mutableListOf<ScheduleChange>()

        // 1. Nothing changed.
        match(oldLeft, newLeft, { listOf(it.start, it.end, it.subject, it.type, it.room, groupsKey(it)) }) { _, _ -> }
        // 2. Same time and subject, different room.
        match(oldLeft, newLeft, { listOf(it.start, it.end, it.subject, it.type, groupsKey(it)) }) { o, n ->
            changes += ScheduleChange(ChangeKind.ROOM, o, n)
        }
        // 3. Same subject for the same groups at a different time — a move (classes are paired in time order).
        match(oldLeft, newLeft, { listOf(it.subject, it.type, groupsKey(it)) }) { o, n ->
            changes += ScheduleChange(ChangeKind.MOVED, o, n)
        }
        // 4. Everything left unpaired.
        oldLeft.forEach { changes += ScheduleChange(ChangeKind.CANCELLED, old = it) }
        newLeft.forEach { changes += ScheduleChange(ChangeKind.ADDED, new = it) }
        return changes.sortedBy { (it.new ?: it.old)!!.start }
    }

    private fun groupsKey(l: Lesson) = l.groupNames.sorted().joinToString("|")

    /** Removes classes with the same key from both lists pairwise, in time order. */
    private fun <K> match(
        old: MutableList<Lesson>,
        new: MutableList<Lesson>,
        key: (Lesson) -> K,
        onMatch: (Lesson, Lesson) -> Unit,
    ) {
        val pool = LinkedHashMap<K, ArrayDeque<Int>>()
        new.forEachIndexed { i, l -> pool.getOrPut(key(l)) { ArrayDeque() }.addLast(i) }
        val usedNew = mutableSetOf<Int>()
        val iterator = old.iterator()
        while (iterator.hasNext()) {
            val o = iterator.next()
            val i = pool[key(o)]?.removeFirstOrNull() ?: continue
            usedNew += i
            onMatch(o, new[i])
            iterator.remove()
        }
        val remaining = new.filterIndexed { i, _ -> i !in usedNew }
        new.clear()
        new.addAll(remaining)
    }
}
