package app.plandsw.ui

import android.content.res.Resources
import androidx.annotation.StringRes
import app.plandsw.R

/** A section in group selection. Enum order = section order on screen. */
enum class GroupCategory(@StringRes val title: Int) {
    LECTURE(R.string.group_cat_lecture),
    EXERCISE(R.string.group_cat_exercise),
    LAB(R.string.group_cat_lab),
    LANGUAGE(R.string.group_cat_language),
    SEMINAR(R.string.group_cat_seminar),
    OTHER(R.string.group_cat_other),
}

private val languages = mapOf(
    "ANG" to R.string.langname_en, "NIEM" to R.string.langname_de, "HISZ" to R.string.langname_es,
    "ROS" to R.string.langname_ru, "FRA" to R.string.langname_fr, "FR" to R.string.langname_fr,
    "WL" to R.string.langname_it, "WŁ" to R.string.langname_it, "POL" to R.string.langname_pl,
    "UKR" to R.string.langname_uk,
)

/**
 * The group code is the last word of the name: "Infor. 1st 3sem Lab2AS" → "Lab2AS".
 * If codes collide (a tok can contain groups from different semesters: "2sem WykS" and "3sem WykS"),
 * keep the preceding word for them as well.
 */
fun shortGroupNames(names: List<String>): List<GroupChoice> {
    val words = names.map { it.trim().split(Regex("\\s+")) }
    val last = words.map { it.last() }
    return names.mapIndexed { i, name ->
        val w = words[i]
        val short = if (last.count { it == last[i] } > 1 && w.size > 1) "${w[w.size - 2]} ${w.last()}" else last[i]
        GroupChoice(name, short)
    }
}

/** Display code: "Lekt/ANG2S" → "ANG2S". */
fun groupCode(short: String) = short.removePrefix("Lekt/").removePrefix("Lek/").removePrefix("Lekty/")

/** "Cw2S" → "Cw2": the trailing S/N is full-time/part-time mode, not needed for the description. */
private fun core(code: String) = code.substringAfterLast(' ').let {
    if (it.length > 2 && (it.endsWith('S') || it.endsWith('N'))) it.dropLast(1) else it
}

fun groupCategory(short: String): GroupCategory {
    val c = core(short).lowercase()
    return when {
        c.startsWith("wyk") -> GroupCategory.LECTURE
        c.startsWith("ćw") || c.startsWith("cw") -> GroupCategory.EXERCISE
        c.startsWith("lab") -> GroupCategory.LAB
        c.startsWith("lek") || '/' in c -> GroupCategory.LANGUAGE
        c.startsWith("sem") -> GroupCategory.SEMINAR
        else -> GroupCategory.OTHER
    }
}

/** Readable description: "Lab2AS" → "lab, group 2A", "Lekt/ANG2S" → "English, group 2". */
fun groupDescription(short: String, res: Resources): String {
    val semester = Regex("""(\d+)\s*sem""", RegexOption.IGNORE_CASE).find(short)?.groupValues?.get(1)
    val c = core(short)
    val desc = when (val category = groupCategory(short)) {
        GroupCategory.LANGUAGE -> {
            val tail = c.substringAfter('/', c)
            val m = Regex("""^([A-Za-zŁłŚśŻżŹźĆćŃńÓóĘęĄą]+?)(\d*\w?)$""").find(tail)
            val code = m?.groupValues?.get(1)?.uppercase()
            val lang = code?.let { languages[it] }?.let(res::getString) ?: code ?: tail
            val n = m?.groupValues?.get(2).orEmpty()
            if (n.isEmpty()) lang else res.getString(R.string.group_desc_numbered, lang, n)
        }
        GroupCategory.OTHER -> c
        else -> {
            val base = res.getString(
                when (category) {
                    GroupCategory.LECTURE -> R.string.group_desc_lecture
                    GroupCategory.EXERCISE -> R.string.group_desc_exercise
                    GroupCategory.LAB -> R.string.group_desc_lab
                    else -> R.string.group_desc_seminar
                }
            )
            val n = Regex("""^\D+(\w*)$""").find(c)?.groupValues?.get(1).orEmpty()
            if (n.isEmpty()) base else res.getString(R.string.group_desc_numbered, base, n)
        }
    }
    return if (semester != null) res.getString(R.string.group_desc_semester, desc, semester) else desc
}
