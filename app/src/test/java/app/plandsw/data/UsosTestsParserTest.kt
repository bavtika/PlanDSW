package app.plandsw.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UsosTestsParserTest {
    private fun fixture(name: String) = javaClass.getResource("/usos/$name")!!.readText()

    private val index by lazy { UsosTestsParser.parseIndex(fixture("sprawdziany.html")) }
    private val tree by lazy { UsosTestsParser.parseCourse(fixture("sprawdzian.html")) }

    private fun find(nodes: List<TestNode>, name: String): TestNode? =
        nodes.firstNotNullOfOrNull { if (it.name == name) it else find(it.children, name) }

    private fun node(name: String) = find(tree, name)!!

    @Test
    fun indexGroupsSubjectsBySemester() {
        assertEquals(listOf("2025/26L", "2025/26Z"), index.map { it.code })
        assertEquals("Semestr zimowy 2025/26", index[1].title)
        assertEquals(
            listOf(UsosTestsParser.Ref(55, "S1-00-FIZ1-1", "Fizyka I"), UsosTestsParser.Ref(60, "S1-00-MATDYS-1", "Matematyka dyskretna")),
            index[1].refs,
        )
    }

    @Test
    fun treeSkipsRootAndKeepsTopLevelOrder() {
        assertEquals(listOf("Ćwiczenia", "Wykład", "Ocena końcowa z przedmiotu - I termin"), tree.map { it.name })
    }

    @Test
    fun pointsNodeWithMaxAndChildren() {
        val cw = node("Ćwiczenia")
        assertFalse(cw.isGrade)
        assertEquals("21.00", cw.value)
        assertEquals("42.00", cw.max)
        assertEquals(
            listOf("Kolokwium nr 1", "Kolokwium nr 2", "Kartkówki", "Ocena z ćwiczeń - I termin", "Ocena z ćwiczeń - II termin"),
            cw.children.map { it.name },
        )
    }

    @Test
    fun nestedLevelsAreParsed() {
        assertEquals(listOf("1.0", "0.5"), node("Kartkówki").children.map { it.value })
    }

    @Test
    fun commentIsKept() {
        assertEquals("poprawione zadanie 3", node("Kolokwium nr 1").comment)
        assertEquals("", node("Kolokwium nr 2").comment)
    }

    @Test
    fun gradeNodes() {
        val first = node("Ocena z ćwiczeń - I termin")
        assertTrue(first.isGrade)
        assertEquals("3,5", first.value)
        assertNull(first.max)
        val second = node("Ocena z ćwiczeń - II termin")
        assertNull(second.value)
        assertFalse(second.hidden)
    }

    @Test
    fun hiddenResults() {
        assertTrue(node("Wykład").hidden)
        assertTrue(node("Ocena z wykładu - I termin").hidden)
        assertEquals("30.00", node("Wykład").max)
    }

    @Test(expected = UsosLoginRequired::class)
    fun loginPageIsRecognized() {
        UsosTestsParser.parseIndex(fixture("niezalogowany.html"))
    }
}
