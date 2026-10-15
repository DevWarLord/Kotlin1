package schedule

import java.io.IOException
import java.nio.file.Path
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Обработчики команд на небольших временных файлах. */
class CommandsTest {

    private fun period(file: String, from: String, to: String) =
        Command.Period(file, LocalDate.parse(from), LocalDate.parse(to))

    private fun byType(file: String, type: SessionType) = Command.ByType(file, type)

    private fun errors(file: String) = Command.Errors(file)

    private val example = arrayOf(
        "2026-10-05|LECTURE|Classes and delegation|Room 312|Portnov",
        "2026-10-08|PRACTICE|Domain modeling|Room 312|Anokhin",
        "2026-10-15|EXAM|Oral exam 1|Room 401|Portnov,Stepanova",
        "2026-10-19|CANCELLED|Lecture postponed|-|-",
    )

    @Test
    fun `period prints sessions in range inclusive`() {
        val file = scheduleFile(*example)
        val (code, out) = captureOutput { runPeriod(period(file, "2026-10-05", "2026-10-15")) }
        assertEquals(0, code)
        assertEquals(
            listOf(
                "2026-10-05 | LECTURE | Classes and delegation | Room 312",
                "2026-10-08 | PRACTICE | Domain modeling | Room 312",
                "2026-10-15 | EXAM | Oral exam 1 | Room 401",
                "Total: 3",
            ),
            out,
        )
    }

    @Test
    fun `period prints cancellation with dash location`() {
        val file = scheduleFile(*example)
        val (_, out) = captureOutput { runPeriod(period(file, "2026-10-19", "2026-10-19")) }
        assertEquals(listOf("2026-10-19 | CANCELLED | Lecture postponed | -", "Total: 1"), out)
    }

    @Test
    fun `type prints teachers and dashes for cancellation`() {
        val file = scheduleFile(*example)
        val (examCode, exams) = captureOutput { runType(byType(file, SessionType.EXAM)) }
        assertEquals(0, examCode)
        assertEquals(listOf("2026-10-15 | EXAM | Oral exam 1 | Room 401 | Portnov, Stepanova", "Total: 1"), exams)

        val (_, cancelled) = captureOutput { runType(byType(file, SessionType.CANCELLED)) }
        assertEquals(listOf("2026-10-19 | CANCELLED | Lecture postponed | - | -", "Total: 1"), cancelled)
    }

    @Test
    fun `each of the four types is selected`() {
        val file = scheduleFile(*example)
        for (type in SessionType.entries) {
            val (_, out) = captureOutput { runType(byType(file, type)) }
            assertEquals(2, out.size, type.name)
            assertEquals(true, out[0].contains("| ${type.name} |"), type.name)
        }
    }

    @Test
    fun `rejected lines outside the filter still give exit code 1`() {
        val file = scheduleFile(
            "2026-10-05|LECTURE|T|R|I",
            "2030-01-01|SEMINAR|T|R|I",
        )
        val (code, out) = captureOutput { runPeriod(period(file, "2026-10-01", "2026-10-31")) }
        assertEquals(1, code)
        assertEquals(listOf("2026-10-05 | LECTURE | T | R", "Total: 1"), out)

        val (typeCode, typeOut) = captureOutput { runType(byType(file, SessionType.LECTURE)) }
        assertEquals(1, typeCode)
        assertEquals(2, typeOut.size)
    }

    @Test
    fun `zero total is still printed`() {
        val file = scheduleFile(*example)
        val (code, out) = captureOutput { runPeriod(period(file, "2027-01-01", "2027-12-31")) }
        assertEquals(0, code)
        assertEquals(listOf("Total: 0"), out)
    }

    @Test
    fun `errors prints reasons in line order and the summary`() {
        val file = scheduleFile(
            "2026-10-05|LECTURE|T|R|I",
            "2026-13-45|SEMINAR|Topic|Room 1|Ivanov",
            "",
            "2026-10-05|EXAM|Topic|Room 1|Ivanov,Ivanov",
            "2026-10-05|EXAM|Topic|Room 1|Ivanov",
        )
        val (code, out) = captureOutput { runErrors(errors(file)) }
        assertEquals(1, code)
        assertEquals(
            listOf(
                "line 2: invalid date \"2026-13-45\"",
                "line 3: invalid field separator",
                "line 4: duplicate teacher \"Ivanov\"",
                "line 5: type \"EXAM\" requires at least 2 teachers, got 1",
                "Rejected: 4 of 5",
            ),
            out,
        )
    }

    @Test
    fun `clean file gives exit code 0`() {
        val file = scheduleFile(*example)
        val (code, out) = captureOutput { runErrors(errors(file)) }
        assertEquals(0, code)
        assertEquals(listOf("Rejected: 0 of 4"), out)
    }

    @Test
    fun `empty file has no lines`() {
        val file = scheduleFile()
        assertEquals(0 to listOf("Rejected: 0 of 0"), captureOutput { runErrors(errors(file)) })
        assertEquals(0 to listOf("Total: 0"), captureOutput { runPeriod(period(file, "2026-01-01", "2026-12-31")) })
        assertEquals(0 to listOf("Total: 0"), captureOutput { runType(byType(file, SessionType.EXAM)) })
    }

    @Test
    fun `read errors are not swallowed by the handler`() {
        val missing = Path.of(System.getProperty("java.io.tmpdir"), "missing-${System.nanoTime()}.txt").toString()
        assertFailsWith<IOException> { runErrors(errors(missing)) }
    }

    @Test
    fun `crlf file and blank line are handled`() {
        val file = rawScheduleFile("2026-10-05|LECTURE|T|R|I\r\n\r\n2026-10-19|CANCELLED|C|-|-\r\n")
        val (code, out) = captureOutput { runErrors(errors(file)) }
        assertEquals(1, code)
        assertEquals(listOf("line 2: invalid field separator", "Rejected: 1 of 3"), out)

        val (_, period) = captureOutput { runPeriod(period(file, "2026-10-05", "2026-10-19")) }
        assertEquals(listOf("2026-10-05 | LECTURE | T | R", "2026-10-19 | CANCELLED | C | -", "Total: 2"), period)
    }

    @Test
    fun `period boundaries are inclusive on both sides`() {
        val file = scheduleFile(
            "2026-10-04|LECTURE|Before|R|I",
            "2026-10-05|LECTURE|From|R|I",
            "2026-10-10|LECTURE|To|R|I",
            "2026-10-11|LECTURE|After|R|I",
        )
        val (_, out) = captureOutput { runPeriod(period(file, "2026-10-05", "2026-10-10")) }
        assertEquals(listOf("2026-10-05 | LECTURE | From | R", "2026-10-10 | LECTURE | To | R", "Total: 2"), out)
    }
}
