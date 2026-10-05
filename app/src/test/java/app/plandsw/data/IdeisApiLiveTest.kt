package app.plandsw.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Hits the real site; run with: ./gradlew testDebugUnitTest -Plive */
class IdeisApiLiveTest {
    private val api = IdeisApi()

    @Test
    fun groupSearchAndSchedule() = runBlocking {
        assumeTrue(System.getProperty("live") != null)
        val groups = api.searchGroups("Informatyka 1sem ćw")
        println("groups: ${groups.size}, first: ${groups.take(3)}")
        println("psychologia: ${api.searchGroups("psychologia").size}, code 'Infor. 1st': ${api.searchGroups("Infor. 1st").size}")
        assertTrue(groups.isNotEmpty())
        val data = api.fetchSchedule(groups.first())
        println("semester ${data.from}..${data.to}, lessons: ${data.lessons.size}")
        data.lessons.take(3).forEach(::println)
        assertTrue(data.lessons.isNotEmpty())
        assertTrue(data.lessons.all { it.subject.isNotBlank() })
    }

    @Test
    fun teacherSearchAndSchedule() = runBlocking {
        assumeTrue(System.getProperty("live") != null)
        val teachers = api.searchTeachers("Gurbiel")
        println("teachers: $teachers")
        assertTrue(teachers.isNotEmpty())
        assertTrue(teachers.first().subtitle.contains('@'))
        val data = api.fetchSchedule(teachers.first())
        println("teacher lessons: ${data.lessons.size}; ${data.lessons.firstOrNull()}")
        assertTrue(data.lessons.isNotEmpty())
        assertTrue(data.lessons.any { it.groups.isNotBlank() })
        println("two words: ${api.searchTeachers("Ewa Gurbiel").size}, reversed: ${api.searchTeachers("Gurbiel Ewa").size}, first name: ${api.searchTeachers("Ewa").size}")
    }

    @Test
    fun roomsAndSchedule() = runBlocking {
        assumeTrue(System.getProperty("live") != null)
        val rooms = api.loadRooms()
        println("rooms: ${rooms.size}, sample: ${rooms.take(3)}")
        assertTrue(rooms.size > 50)
        val room = rooms.first { it.name == "S55 111" }
        val data = api.fetchSchedule(room)
        println("room lessons: ${data.lessons.size}; ${data.lessons.firstOrNull()}")
        assertTrue(data.lessons.isNotEmpty())
    }

    @Test
    fun tokWizardAndSchedule() = runBlocking {
        assumeTrue(System.getProperty("live") != null)
        val filters = api.loadTokFilters()
        println("faculties: ${filters.faculties}")
        println("intakes: ${filters.intakes.take(3)}, modes: ${filters.modes}")
        assertTrue(filters.faculties.size >= 3 && filters.intakes.isNotEmpty() && filters.modes.size == 2)
        val faculty = filters.faculties.first { it.name.contains("Stosowanych") }
        val kierunki = api.loadKierunki(faculty.id)
        println("kierunki: ${kierunki.size}, ${kierunki.take(3)}")
        val informatyka = kierunki.first { it.name == "Informatyka" }
        val intake = filters.intakes.first()
        val mode = filters.modes.first { it.name == "Stacjonarne" }
        val toks = api.searchToks(faculty.id, informatyka.id, intake.id, mode.id)
        println("toks: $toks")
        assertTrue(toks.isNotEmpty())
        val started = System.currentTimeMillis()
        val data = api.fetchSchedule(toks.first())
        println("tok loaded in ${System.currentTimeMillis() - started} ms")
        println("tok ${data.from}..${data.to}: ${data.lessons.size} lessons, groups ${data.lessons.flatMap { it.groupNames }.toSortedSet()}")
        data.lessons.take(3).forEach(::println)
        assertTrue(data.lessons.size > 100)
        assertTrue(data.lessons.all { it.groupNames.isNotEmpty() && it.subject.isNotBlank() })
    }
}
