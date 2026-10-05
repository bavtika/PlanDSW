package app.plandsw.data

import java.text.Normalizer

private val marks = Regex("\\p{Mn}+")

/** Lowercase without diacritics: "Ćw" → "cw", "Łódź" → "lodz". For searching without a Polish keyboard layout. */
fun String.fold(): String =
    Normalizer.normalize(lowercase().replace('ł', 'l'), Normalizer.Form.NFD).replace(marks, "")
