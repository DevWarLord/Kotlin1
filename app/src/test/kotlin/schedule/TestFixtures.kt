package schedule

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate

val OCT_5: LocalDate = LocalDate.of(2026, 10, 5)

fun lecture(
    date: LocalDate = OCT_5,
    topic: String = "Classes and delegation",
    location: String = "Room 312",
    teachers: List<String> = listOf("Portnov"),
) = Lecture(date, topic, location, teachers)

fun practice(
    date: LocalDate = OCT_5,
    topic: String = "Domain modeling",
    location: String = "Room 312",
    teachers: List<String> = listOf("Anokhin"),
) = Practice(date, topic, location, teachers)

fun exam(
    date: LocalDate = OCT_5,
    topic: String = "Oral exam 1",
    location: String = "Room 401",
    teachers: List<String> = listOf("Portnov", "Stepanova"),
) = Exam(date, topic, location, teachers)

fun cancellation(date: LocalDate = OCT_5, topic: String = "Lecture postponed") = Cancellation(date, topic)

/** Временный файл расписания; строки соединяются через \n, завершающий перевод строки добавляется. */
fun scheduleFile(vararg lines: String): String =
    rawScheduleFile(if (lines.isEmpty()) "" else lines.joinToString("\n", postfix = "\n"))

/** Временный файл с текстом как есть (например, с CRLF). Возвращает путь строкой, как в Command. */
fun rawScheduleFile(text: String): String {
    val file: Path = Files.createTempFile("schedule", ".txt")
    file.toFile().deleteOnExit()
    Files.writeString(file, text)
    return file.toString()
}

/** Перехватывает println, возвращает код завершения и напечатанные строки. */
fun captureOutput(block: () -> Int): Pair<Int, List<String>> {
    val original = System.out
    val buffer = ByteArrayOutputStream()
    System.setOut(PrintStream(buffer, true, Charsets.UTF_8))
    val code = try {
        block()
    } finally {
        System.setOut(original)
    }
    val text = buffer.toString(Charsets.UTF_8).replace("\r\n", "\n")
    return code to text.lines().dropLast(1)
}
