package app.plandsw.data

import org.junit.Assert.assertEquals
import org.junit.Test

class UsosGradesParserTest {
    private fun fixture(name: String) = javaClass.getResource("/usos/$name")!!.readText()

    private val terms by lazy { UsosGradesParser.parse(fixture("oceny.html")) }

    private fun course(name: String) = terms.flatMap { it.courses }.single { it.name == name }

    @Test
    fun termsInPageOrderWithUsosCodes() {
        assertEquals(listOf("2026/27Z", "2025/26L"), terms.map { it.code })
        assertEquals("Semestr letni 2025/26", terms[1].title)
    }

    @Test
    fun skipsStudyRecordFrame() {
        assertEquals(5, terms.sumOf { it.courses.size })
    }

    @Test
    fun courseWithoutGrades() {
        assertEquals(
            CourseGrades("S1-00-BADA2-3", "Bazy danych II", listOf(ClassGrade("Wykład"))),
            terms[0].courses.single(),
        )
    }

    @Test
    fun retakeKeepsAllAttempts() {
        assertEquals(listOf(ClassGrade("Wykład", listOf("2", "3"))), course("Analiza matematyczna II").classes)
    }

    @Test
    fun severalClassTypes() {
        assertEquals(
            listOf(ClassGrade("Wykład", listOf("4")), ClassGrade("Ćwiczenia", listOf("NZAL", "ZAL"))),
            course("Bazy danych I").classes,
        )
    }

    @Test
    fun failedCurrentGrade() {
        assertEquals(listOf(ClassGrade("Wykład", listOf("2"), failed = true)), course("Fizyka").classes)
    }

    @Test
    fun halfGradeAndElearning() {
        assertEquals(listOf(ClassGrade("E-learning", listOf("3,5"))), course("Programowanie").classes)
    }

    @Test(expected = UsosLoginRequired::class)
    fun loginPageMeansSessionExpired() {
        UsosGradesParser.parse(fixture("niezalogowany.html"))
    }

    @Test(expected = UnexpectedResponseException::class)
    fun unknownPageIsAnError() {
        UsosGradesParser.parse("<html><head><title>Coś innego</title></head><body></body></html>")
    }
}
