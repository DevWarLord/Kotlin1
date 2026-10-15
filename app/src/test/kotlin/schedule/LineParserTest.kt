package schedule

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class LineParserTest {

    private fun accepted(line: String): Session =
        assertIs<ParseResult.Accepted>(parseScheduleLine(1, line)).session

    private fun reason(line: String): RejectReason =
        assertIs<ParseResult.Rejected>(parseScheduleLine(1, line)).reason

    private fun message(line: String, number: Long = 7): String =
        formatRejection(assertIs<ParseResult.Rejected>(parseScheduleLine(number, line)))

    // Корректные строки из примера задания.

    @Test
    fun `all four types are parsed`() {
        assertEquals(
            Lecture(LocalDate.of(2026, 10, 5), "Classes and delegation", "Room 312", listOf("Portnov")),
            accepted("2026-10-05|LECTURE|Classes and delegation|Room 312|Portnov"),
        )
        assertEquals(
            Practice(LocalDate.of(2026, 10, 8), "Domain modeling", "Room 312", listOf("Anokhin")),
            accepted("2026-10-08|PRACTICE|Domain modeling|Room 312|Anokhin"),
        )
        assertEquals(
            Exam(LocalDate.of(2026, 10, 15), "Oral exam 1", "Room 401", listOf("Portnov", "Stepanova")),
            accepted("2026-10-15|EXAM|Oral exam 1|Room 401|Portnov,Stepanova"),
        )
        assertEquals(
            Cancellation(LocalDate.of(2026, 10, 19), "Lecture postponed"),
            accepted("2026-10-19|CANCELLED|Lecture postponed|-|-"),
        )
    }

    @Test
    fun `line number is kept in the result`() {
        assertEquals(42L, parseScheduleLine(42, "2026-10-19|CANCELLED|T|-|-").lineNumber)
        assertEquals(43L, parseScheduleLine(43, "garbage").lineNumber)
    }

    // Обязательные сообщения.

    @Test
    fun `mandatory messages`() {
        assertEquals("line 7: invalid date \"2026-13-45\"", message("2026-13-45|LECTURE|T|R|I"))
        assertEquals("line 7: empty required field \"date\"", message("|LECTURE|T|R|I"))
        assertEquals("line 7: invalid field separator", message("2026-10-05;LECTURE;T;R;I"))
        assertEquals("line 7: unknown session type \"SEMINAR\"", message("2026-10-05|SEMINAR|T|R|I"))
        assertEquals("line 7: type \"EXAM\" requires at least 2 teachers, got 1", message("2026-10-05|EXAM|T|R|Ivanov"))
        assertEquals("line 7: got 6 fields, expected 5", message("2026-10-05|LECTURE|T|R|I|extra"))
    }

    // 1–2. Разделитель и количество полей.

    @Test
    fun `empty line is an invalid separator`() {
        assertEquals(RejectReason.InvalidSeparator, reason(""))
    }

    @Test
    fun `trailing empty fields are counted`() {
        assertEquals(RejectReason.WrongFieldCount(6), reason("2026-10-05|LECTURE|T|R|I|"))
        assertEquals(RejectReason.WrongFieldCount(4), reason("2026-10-05|LECTURE|T|R"))
        assertEquals(RejectReason.WrongFieldCount(2), reason("|"))
        assertEquals(RejectReason.EmptyField(Field.TEACHERS), reason("2026-10-05|LECTURE|T|R|"))
    }

    // 3. Пустые поля.

    @Test
    fun `first empty or blank field wins`() {
        assertEquals(RejectReason.EmptyField(Field.DATE), reason("||||"))
        assertEquals(RejectReason.EmptyField(Field.TYPE), reason("2026-10-05| |T||"))
        assertEquals(RejectReason.EmptyField(Field.TOPIC), reason("2026-10-05|LECTURE|\t|R|I"))
        assertEquals(RejectReason.EmptyField(Field.LOCATION), reason("bad-date|SEMINAR|T|  |I"))
    }

    // 4. Дата.

    @Test
    fun `invalid dates`() {
        val bad = listOf("2026-02-29", "2026-04-31", "0000-01-01", "26-10-05", "2026-1-05", "2026/10/05",
            " 2026-10-05", "2026-10-05 ", "+2026-10-05", "２０２６-10-05", "12026-10-05")
        for (value in bad) {
            assertEquals(RejectReason.InvalidDate(value), reason("$value|LECTURE|T|R|I"), value)
        }
    }

    @Test
    fun `valid boundary dates`() {
        assertEquals(LocalDate.of(2028, 2, 29), accepted("2028-02-29|LECTURE|T|R|I").date)
        assertEquals(LocalDate.of(1, 1, 1), accepted("0001-01-01|LECTURE|T|R|I").date)
        assertEquals(LocalDate.of(9999, 12, 31), accepted("9999-12-31|CANCELLED|T|-|-").date)
    }

    // 5. Тип.

    @Test
    fun `type is case sensitive and exact`() {
        assertEquals(RejectReason.UnknownType("lecture"), reason("2026-10-05|lecture|T|R|I"))
        assertEquals(RejectReason.UnknownType(" EXAM"), reason("2026-10-05| EXAM|T|R|A,B"))
        assertEquals(RejectReason.UnknownType("CANCELED"), reason("2026-10-05|CANCELED|T|-|-"))
    }

    // 6. Минимум преподавателей у сдачи.

    @Test
    fun `exam counts only non blank non dash elements`() {
        assertEquals(RejectReason.NotEnoughTeachers(SessionType.EXAM, 2, 0), reason("2026-10-05|EXAM|T|R|-"))
        assertEquals(RejectReason.NotEnoughTeachers(SessionType.EXAM, 2, 1), reason("2026-10-05|EXAM|T|R|Ivanov,-"))
        assertEquals(RejectReason.NotEnoughTeachers(SessionType.EXAM, 2, 1), reason("2026-10-05|EXAM|T|R|Ivanov,,  "))
        // Два годных элемента есть — дальше пункт 7.
        assertEquals(RejectReason.InvalidTeacher(2, ""), reason("2026-10-05|EXAM|T|R|Ivanov,,Petrov"))
    }

    // 7. Остальные нарушения домена — по порядку.

    @Test
    fun `control characters come before whitespace`() {
        assertEquals(
            RejectReason.ControlCharacter(Field.LOCATION, "Room\t1"),
            reason("2026-10-05|LECTURE| Topic|Room\t1|I"),
        )
        assertEquals(
            RejectReason.ControlCharacter(Field.TEACHERS, "Pe\u0007trov"),
            reason("2026-10-05|EXAM|T|R|Ivanov,Pe\u0007trov"),
        )
    }

    @Test
    fun `outer whitespace in the first field in order`() {
        assertEquals(RejectReason.OuterWhitespace(Field.TOPIC, "Topic "), reason("2026-10-05|LECTURE|Topic |Room |I"))
        assertEquals(RejectReason.OuterWhitespace(Field.LOCATION, " Room"), reason("2026-10-05|LECTURE|T| Room|I"))
        assertEquals(RejectReason.OuterWhitespace(Field.TEACHERS, " Petrov"), reason("2026-10-05|EXAM|T|R|Ivanov, Petrov"))
    }

    @Test
    fun `cancelled fields must be dashes`() {
        assertEquals(
            RejectReason.CancelledFieldNotMissing(Field.LOCATION, "Room 1"),
            reason("2026-10-05|CANCELLED|T|Room 1|Ivanov"),
        )
        assertEquals(
            RejectReason.CancelledFieldNotMissing(Field.TEACHERS, "Ivanov,Petrov"),
            reason("2026-10-05|CANCELLED|T|-|Ivanov,Petrov"),
        )
    }

    @Test
    fun `active session needs a location`() {
        assertEquals(RejectReason.MissingLocation(SessionType.LECTURE), reason("2026-10-05|LECTURE|T|-|I"))
        assertEquals(RejectReason.MissingLocation(SessionType.EXAM), reason("2026-10-05|EXAM|T|-|A,B"))
        // "A,-" — у сдачи один годный преподаватель: пункт 6 срабатывает раньше пункта 7.
        assertEquals(RejectReason.NotEnoughTeachers(SessionType.EXAM, 2, 1), reason("2026-10-05|EXAM|T|-|A,-"))
    }

    @Test
    fun `invalid teacher elements`() {
        assertEquals(RejectReason.InvalidTeacher(1, "-"), reason("2026-10-05|LECTURE|T|R|-"))
        assertEquals(RejectReason.InvalidTeacher(2, ""), reason("2026-10-05|PRACTICE|T|R|Ivanov,"))
        assertEquals(RejectReason.InvalidTeacher(3, "-"), reason("2026-10-05|EXAM|T|R|A,B,-,"))
    }

    @Test
    fun `duplicate names are found and case matters`() {
        assertEquals(RejectReason.DuplicateTeacher("Ivanov"), reason("2026-10-05|EXAM|Topic|Room 1|Ivanov,Ivanov"))
        assertEquals(RejectReason.DuplicateTeacher("B"), reason("2026-10-05|LECTURE|T|R|A,B,C,B,A"))
        assertEquals(listOf("Ivanov", "ivanov"), (accepted("2026-10-05|EXAM|T|R|Ivanov,ivanov") as Exam).teachers)
    }

    @Test
    fun `values are not trimmed or changed`() {
        val lecture = accepted("2026-10-05|LECTURE|Big  topic|Main hall, room 3|Van der Berg,O'Neil") as Lecture
        assertEquals("Big  topic", lecture.topic)
        assertEquals("Main hall, room 3", lecture.location)
        assertEquals(listOf("Van der Berg", "O'Neil"), lecture.teachers)
    }

    // Несколько дефектов в одной строке: выигрывает самый приоритетный.

    @Test
    fun `several defects - date wins over type`() {
        assertEquals("line 1: invalid date \"2026-13-45\"", message("2026-13-45|SEMINAR|Topic|Room 1|Ivanov", 1))
    }

    @Test
    fun `several defects - duplicate after exam count`() {
        assertEquals("line 1: duplicate teacher \"Ivanov\"", message("2026-10-05|EXAM|Topic|Room 1|Ivanov,Ivanov", 1))
    }

    @Test
    fun `several defects - priorities step by step`() {
        // Нет разделителя — даже если строка пустая по смыслу.
        assertEquals(RejectReason.InvalidSeparator, reason("2026-13-45 SEMINAR"))
        // Количество полей раньше пустых полей.
        assertEquals(RejectReason.WrongFieldCount(6), reason("|||||"))
        // Пустое поле раньше неверной даты.
        assertEquals(RejectReason.EmptyField(Field.TEACHERS), reason("2026-13-45|SEMINAR|T|R|"))
        // Неизвестный тип раньше нарушений домена.
        assertEquals(RejectReason.UnknownType("SEMINAR"), reason("2026-10-05|SEMINAR| T |-|-,-"))
        // Нехватка преподавателей сдачи раньше пробелов и управляющих символов.
        assertEquals(RejectReason.NotEnoughTeachers(SessionType.EXAM, 2, 1), reason("2026-10-05|EXAM|\tT|-|Ivanov"))
        // Пробел в теме раньше неверных полей отмены.
        assertEquals(RejectReason.OuterWhitespace(Field.TOPIC, "T "), reason("2026-10-05|CANCELLED|T |Room|I"))
        // Место раньше элементов списка.
        assertEquals(RejectReason.MissingLocation(SessionType.PRACTICE), reason("2026-10-05|PRACTICE|T|-|-"))
        // Недопустимый элемент раньше повтора.
        assertEquals(RejectReason.InvalidTeacher(3, "-"), reason("2026-10-05|EXAM|T|R|A,A,-"))
    }

    @Test
    fun `parser and model agree on what is invalid`() {
        // Всё, что парсер отклоняет как нарушение домена, модель тоже не даёт создать.
        val badTopics = listOf(" T", "T ", "T\u0000", " T")
        for (topic in badTopics) {
            assertIs<ParseResult.Rejected>(parseScheduleLine(1, "2026-10-05|LECTURE|$topic|R|I"))
            kotlin.test.assertFailsWith<IllegalArgumentException> { lecture(topic = topic) }
        }
    }
}
