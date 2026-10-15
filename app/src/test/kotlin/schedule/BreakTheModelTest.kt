package schedule

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * «Сломай свою модель»: попытки нарушить инварианты через публичный API.
 * Номера 1–8 соответствуют списку из задания и таблице в README.
 */
class BreakTheModelTest {

    // 1. Пустая тема.

    @Test
    fun `1 empty topic is rejected for every type`() {
        assertFailsWith<IllegalArgumentException> { lecture(topic = "") }
        assertFailsWith<IllegalArgumentException> { practice(topic = "") }
        assertFailsWith<IllegalArgumentException> { exam(topic = "") }
        assertFailsWith<IllegalArgumentException> { cancellation(topic = "") }
    }

    @Test
    fun `1 blank topic is rejected`() {
        assertFailsWith<IllegalArgumentException> { lecture(topic = "   ") }
        assertFailsWith<IllegalArgumentException> { cancellation(topic = "\t") }
        assertFailsWith<IllegalArgumentException> { cancellation(topic = " ") }
    }

    @Test
    fun `exception message is understandable`() {
        val error = assertFailsWith<IllegalArgumentException> { lecture(topic = "") }
        assertEquals("Lecture: topic must not be empty or blank, got \"\"", error.message)
    }

    // 2. Год 0 и 10000.

    @Test
    fun `2 year 0 and year 10000 are rejected`() {
        for (year in listOf(0, 10000, -1)) {
            val date = LocalDate.of(year, 1, 1)
            assertFailsWith<IllegalArgumentException> { lecture(date = date) }
            assertFailsWith<IllegalArgumentException> { practice(date = date) }
            assertFailsWith<IllegalArgumentException> { exam(date = date) }
            assertFailsWith<IllegalArgumentException> { cancellation(date = date) }
        }
    }

    @Test
    fun `2 boundary years 1 and 9999 are allowed`() {
        assertEquals(1, lecture(date = LocalDate.of(1, 1, 1)).date.year)
        assertEquals(9999, cancellation(date = LocalDate.of(9999, 12, 31)).date.year)
    }

    // 3. Действующее занятие без преподавателей.

    @Test
    fun `3 active session without teachers is rejected`() {
        assertFailsWith<IllegalArgumentException> { lecture(teachers = emptyList()) }
        assertFailsWith<IllegalArgumentException> { practice(teachers = emptyList()) }
        assertFailsWith<IllegalArgumentException> { exam(teachers = emptyList()) }
    }

    // 4. Сдача с одним преподавателем.

    @Test
    fun `4 exam with one teacher is rejected`() {
        val error = assertFailsWith<IllegalArgumentException> { exam(teachers = listOf("Portnov")) }
        assertEquals("Exam requires at least 2 different teacher(s), got 1", error.message)
    }

    // 5. Сдача с двумя одинаковыми преподавателями.

    @Test
    fun `5 exam with two equal teachers is rejected`() {
        val error = assertFailsWith<IllegalArgumentException> { exam(teachers = listOf("Ivanov", "Ivanov")) }
        assertEquals("Exam: duplicate teacher \"Ivanov\"", error.message)
    }

    @Test
    fun `5 duplicates are rejected for lectures too`() {
        assertFailsWith<IllegalArgumentException> { lecture(teachers = listOf("Ivanov", "Petrov", "Ivanov")) }
    }

    @Test
    fun `5 names differing only in case are different teachers`() {
        val exam = exam(teachers = listOf("Ivanov", "ivanov"))
        assertEquals(listOf("Ivanov", "ivanov"), exam.teachers)
    }

    // 6. Изменение исходного списка после создания.

    @Test
    fun `6 clearing the source list does not change the exam`() {
        val names = mutableListOf("Ivanov", "Petrov")
        val exam = exam(teachers = names)

        names.clear()

        assertEquals(listOf("Ivanov", "Petrov"), exam.teachers)
    }

    @Test
    fun `6 adding an invalid name to the source list after creation does not leak in`() {
        val names = mutableListOf("Ivanov")
        val lecture = lecture(teachers = names)

        names.add("-")
        names[0] = "Ivanov"

        assertEquals(listOf("Ivanov"), lecture.teachers)
    }

    // 7. Изменение состояния через выданный список.

    @Test
    fun `7 list from the property cannot be modified even after a cast`() {
        val exam = exam(teachers = listOf("Ivanov", "Petrov"))

        @Suppress("UNCHECKED_CAST")
        val leaked = exam.teachers as MutableList<String>
        assertFailsWith<UnsupportedOperationException> { leaked.clear() }
        assertFailsWith<UnsupportedOperationException> { leaked.add("Ivanov") }
        assertFailsWith<UnsupportedOperationException> { leaked[0] = "-" }

        assertEquals(listOf("Ivanov", "Petrov"), exam.teachers)
    }

    @Test
    fun `7 iterator of the property cannot remove elements`() {
        val lecture = lecture(teachers = listOf("Ivanov", "Petrov"))
        @Suppress("UNCHECKED_CAST")
        val iterator = lecture.teachers.iterator() as MutableIterator<String>
        iterator.next()
        assertFailsWith<UnsupportedOperationException> { iterator.remove() }
        assertEquals(2, lecture.teachers.size)
    }

    // 8. Обход проверок через copy.

    @Test
    fun `8 model has no copy or componentN that could bypass the constructor`() {
        val classes = listOf(Lecture::class.java, Practice::class.java, Exam::class.java, Cancellation::class.java)
        for (type in classes) {
            val names = type.methods.map { it.name }
            assertTrue(names.none { it == "copy" || it.startsWith("copy$") }, "${type.simpleName} has copy")
            assertTrue(names.none { it.startsWith("component") }, "${type.simpleName} has componentN")
        }
    }

    @Test
    fun `8 the only way to get a modified session goes through the constructor`() {
        // withDate — внутренний аналог copy для переноса; он вызывает конструктор и потому проверяет год.
        assertFailsWith<IllegalArgumentException> { lecture().withDate(LocalDate.of(10000, 1, 1)) }
        assertFailsWith<IllegalArgumentException> { exam().withDate(LocalDate.of(0, 1, 1)) }
    }

    // Другие попытки: место, пробелы, управляющие символы, «-».

    @Test
    fun `active session location must be a real place`() {
        assertFailsWith<IllegalArgumentException> { lecture(location = "") }
        assertFailsWith<IllegalArgumentException> { lecture(location = " ") }
        assertFailsWith<IllegalArgumentException> { practice(location = "-") }
        assertFailsWith<IllegalArgumentException> { exam(location = "-") }
    }

    @Test
    fun `outer whitespace is rejected and never trimmed`() {
        assertFailsWith<IllegalArgumentException> { lecture(topic = " Topic") }
        assertFailsWith<IllegalArgumentException> { lecture(topic = "Topic ") }
        assertFailsWith<IllegalArgumentException> { lecture(location = "Room 1 ") }
        assertFailsWith<IllegalArgumentException> { lecture(teachers = listOf(" Ivanov")) }
        assertFailsWith<IllegalArgumentException> { cancellation(topic = "Postponed ") }
    }

    @Test
    fun `control characters are rejected even inside a value`() {
        assertFailsWith<IllegalArgumentException> { lecture(topic = "Two\nlines") }
        assertFailsWith<IllegalArgumentException> { lecture(location = "Room\t1") }
        assertFailsWith<IllegalArgumentException> { exam(teachers = listOf("Ivanov", "Pet\u0000rov")) }
        assertFailsWith<IllegalArgumentException> { cancellation(topic = "Bell\u0007") }
    }

    @Test
    fun `empty name and dash name are rejected`() {
        assertFailsWith<IllegalArgumentException> { lecture(teachers = listOf("")) }
        assertFailsWith<IllegalArgumentException> { lecture(teachers = listOf("-")) }
        assertFailsWith<IllegalArgumentException> { exam(teachers = listOf("Ivanov", "-")) }
        assertFailsWith<IllegalArgumentException> { exam(teachers = listOf("Ivanov", "Petrov", "")) }
    }

    @Test
    fun `null inside the teacher list from Java code is rejected`() {
        val withNull = java.util.ArrayList<String?>().apply { add("Ivanov"); add(null) }
        @Suppress("UNCHECKED_CAST")
        val disguised = withNull as List<String>
        assertFailsWith<IllegalArgumentException> { exam(teachers = disguised) }
    }
}
