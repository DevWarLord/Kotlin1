package schedule

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class FormattingTest {

    @Test
    fun `quoted escapes backslash quote control and non ascii`() {
        assertEquals("\"plain text 1-2\"", quoted("plain text 1-2"))
        assertEquals("\"a\\\\b\"", quoted("a\\b"))
        assertEquals("\"say \\\"hi\\\"\"", quoted("say \"hi\""))
        assertEquals("\"a\\u000Ab\"", quoted("a\nb"))
        assertEquals("\"\\u0009\\u007F\"", quoted("\t\u007F"))
        assertEquals("\"\\u0422\\u0435\\u043C\\u0430\"", quoted("Тема"))
        // Символ вне BMP — две UTF-16 единицы, каждая экранируется отдельно.
        assertEquals("\"\\uD83D\\uDE00\"", quoted("😀"))
    }

    @Test
    fun `messages never contain line breaks or non ascii`() {
        val nasty = listOf(
            "2026-1\n0-05|LECTURE|T|R|I",
            "2026-10-05|ЛЕКЦИЯ|T|R|I",
            "2026-10-05|LECTURE|T |R|I",
            "2026-10-05|LECTURE|\"Тема\"\u0000|R|I",
        )
        for (line in nasty) {
            val result = parseScheduleLine(3, line) as ParseResult.Rejected
            val text = formatRejection(result)
            assertEquals(true, text.all { it.code in 0x20..0x7E }, text)
        }
    }

    @Test
    fun `custom messages`() {
        fun msg(reason: RejectReason) = describe(reason)
        assertEquals("control character in field \"topic\": \"A\\u0007B\"",
            msg(RejectReason.ControlCharacter(Field.TOPIC, "A\u0007B")))
        assertEquals("leading or trailing whitespace in field \"teachers\": \" Petrov\"",
            msg(RejectReason.OuterWhitespace(Field.TEACHERS, " Petrov")))
        assertEquals("type \"CANCELLED\" requires \"-\" in field \"location\", got \"Room 1\"",
            msg(RejectReason.CancelledFieldNotMissing(Field.LOCATION, "Room 1")))
        assertEquals("type \"LECTURE\" requires a location, got \"-\"",
            msg(RejectReason.MissingLocation(SessionType.LECTURE)))
        assertEquals("invalid teacher name \"\" at position 2",
            msg(RejectReason.InvalidTeacher(2, "")))
        assertEquals("duplicate teacher \"Ivanov\"",
            msg(RejectReason.DuplicateTeacher("Ivanov")))
    }

    @Test
    fun `period and type lines`() {
        val exam = Exam(LocalDate.of(2026, 10, 15), "Oral exam 1", "Room 401", listOf("Portnov", "Stepanova"))
        val cancelled = Cancellation(LocalDate.of(2026, 10, 19), "Lecture postponed")

        assertEquals("2026-10-15 | EXAM | Oral exam 1 | Room 401", formatForPeriod(exam))
        assertEquals("2026-10-15 | EXAM | Oral exam 1 | Room 401 | Portnov, Stepanova", formatForType(exam))
        assertEquals("2026-10-19 | CANCELLED | Lecture postponed | -", formatForPeriod(cancelled))
        assertEquals("2026-10-19 | CANCELLED | Lecture postponed | - | -", formatForType(cancelled))
    }

    @Test
    fun `dates are printed with four digit year`() {
        assertEquals("0001-01-01 | LECTURE | T | R", formatForPeriod(lecture(date = LocalDate.of(1, 1, 1), topic = "T", location = "R")))
    }
}
