package schedule

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SessionCreationTest {

    @Test
    fun `lecture keeps all values exactly`() {
        val lecture = Lecture(OCT_5, "Classes and delegation", "Room 312", listOf("Portnov"))
        assertEquals(OCT_5, lecture.date)
        assertEquals("Classes and delegation", lecture.topic)
        assertEquals("Room 312", lecture.location)
        assertEquals(listOf("Portnov"), lecture.teachers)
    }

    @Test
    fun `practice is created with one teacher`() {
        val practice = Practice(OCT_5, "Domain modeling", "Lab 2", listOf("Anokhin"))
        assertEquals(listOf("Anokhin"), practice.teachers)
        assertEquals(SessionType.PRACTICE, practice.sessionType)
    }

    @Test
    fun `exam keeps teacher order`() {
        val exam = Exam(OCT_5, "Oral exam 1", "Room 401", listOf("Stepanova", "Portnov", "Anokhin"))
        assertEquals(listOf("Stepanova", "Portnov", "Anokhin"), exam.teachers)
    }

    @Test
    fun `cancellation has only date and topic`() {
        val cancellation = Cancellation(OCT_5, "Lecture postponed")
        assertEquals(OCT_5, cancellation.date)
        assertEquals("Lecture postponed", cancellation.topic)

        val properties = Cancellation::class.java.methods.map { it.name }
        assertFalse("getLocation" in properties)
        assertFalse("getTeachers" in properties)
        val asSession: Session = cancellation
        assertFalse(asSession is ActiveSession)
    }

    @Test
    fun `inner spaces and a dash topic are allowed`() {
        val lecture = lecture(topic = "Big  topic - part 2", location = "Main building, room 3", teachers = listOf("Van der Berg"))
        assertEquals("Big  topic - part 2", lecture.topic)
        assertEquals("Van der Berg", lecture.teachers.single())
    }

    @Test
    fun `equality depends on type and values`() {
        assertEquals(lecture(), lecture())
        assertEquals(lecture().hashCode(), lecture().hashCode())
        assertEquals(cancellation(), cancellation())

        // Те же значения, но другой тип — разные занятия.
        val asLecture = Lecture(OCT_5, "T", "R", listOf("A"))
        val asPractice = Practice(OCT_5, "T", "R", listOf("A"))
        assertNotEquals<Session>(asLecture, asPractice)

        assertNotEquals(exam(teachers = listOf("A", "B")), exam(teachers = listOf("B", "A")))
    }

    @Test
    fun `when over session is exhaustive without else`() {
        val sessions: List<Session> = listOf(lecture(), practice(), exam(), cancellation())
        val labels = sessions.map {
            when (it) {
                is Lecture -> "L"
                is Practice -> "P"
                is Exam -> "E"
                is Cancellation -> "C"
            }
        }
        assertEquals(listOf("L", "P", "E", "C"), labels)
    }

    @Test
    fun `toString shows the values`() {
        assertTrue(exam().toString().startsWith("Exam(date=2026-10-05"))
        assertEquals("Cancellation(date=2026-10-05, topic=Lecture postponed)", cancellation().toString())
    }
}
