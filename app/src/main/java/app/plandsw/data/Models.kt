package app.plandsw.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Schedule times are Warsaw time, so "now" and "today" are computed for Warsaw too. */
val WARSAW: ZoneId = ZoneId.of("Europe/Warsaw")

@Serializable
enum class TargetKind { GROUP, TEACHER, ROOM, TOK }

/** Whose schedule is being viewed: a group, a teacher or a room. */
@Serializable
data class Target(
    val kind: TargetKind,
    val id: Int,
    val name: String,
    /** Study tok for a group, e-mail for a teacher, building for a room, specialization for a tok. */
    val subtitle: String = "",
) {
    val key: String get() = "${kind.name}_$id"
}

@Serializable
data class Lesson(
    val start: String, // ISO LocalDateTime, Warsaw time
    val end: String,
    val subject: String,
    /** Wyk, Cw, Lab, Sem… (the site export calls this field "Grupy"). */
    val type: String = "",
    val room: String = "",
    val teacher: String = "",
    /** Toki nauki — which groups attend the class (present in a teacher's plan). */
    val groups: String = "",
    val examForm: String = "",
    val remarks: String = "",
    val topic: String = "",
    /** Full names of the class's groups (in a tok plan) — the "my groups" filter works on them. */
    val groupNames: List<String> = emptyList(),
) {
    @Transient val startAt: LocalDateTime = LocalDateTime.parse(start)
    @Transient val endAt: LocalDateTime = LocalDateTime.parse(end)
    val date: LocalDate get() = startAt.toLocalDate()

    val isOnline: Boolean
        get() = room.isBlank() && remarks.contains(Regex("distance|zdaln|online", RegexOption.IGNORE_CASE))
}

@Serializable
data class CachedSchedule(
    val target: Target,
    val fetchedAt: Long,
    val from: String, // ISO LocalDate — semester bounds
    val to: String,
    val lessons: List<Lesson>,
)

/** A site dropdown item: the id as the server expects it (can be "8038,5338") and a label. */
@Serializable
data class Option(val id: String, val name: String)

data class TokFilters(val faculties: List<Option>, val intakes: List<Option>, val modes: List<Option>)

@Serializable
enum class ChangeKind { MOVED, CANCELLED, ADDED, ROOM }

/** One schedule change: [old] — before, [new] — after (a cancellation has no new, an added class has no old). */
@Serializable
data class ScheduleChange(
    val kind: ChangeKind,
    val old: Lesson? = null,
    val new: Lesson? = null,
)
