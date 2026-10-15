package schedule

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * «Ничего не падает»: случайные строки из опасных фрагментов. Парсер обязан вернуть
 * результат для каждой, а принятая строка — уже собранное корректное занятие
 * (конструктор модели после проверок парсера не бросает исключений).
 */
class ParserNeverThrowsTest {

    private val dates = listOf("2026-10-05", "2028-02-29", "2026-02-29", "0000-01-01", "0001-01-01", "9999-12-31",
        "2026-13-45", " 2026-10-05", "", " ", "abc", "2026-1-5")
    private val types = listOf("LECTURE", "PRACTICE", "EXAM", "CANCELLED", "lecture", "SEMINAR", "", " EXAM", "\t")
    private val texts = listOf("Topic", "Room 1", "-", "", " ", "A B", " lead", "trail ", "tab\tin", "\u0000",
        "Тема", "a\"b", "back\\slash", " nbsp", "x,y", "😀")
    private val teacherLists = listOf("Ivanov", "Ivanov,Petrov", "Ivanov,Ivanov", "-", "-,-", "Ivanov,", ",Petrov",
        "Ivanov, Petrov", "Ivanov,ivanov", "", " ", "A,B,C,A", "A,\u0007B", "Ivanov,-")

    @Test
    fun `parser returns a result for every random line`() {
        val random = Random(20261015)
        var accepted = 0
        repeat(50_000) { index ->
            val fields = mutableListOf(dates.random(random), types.random(random), texts.random(random),
                texts.random(random), teacherLists.random(random))
            // Иногда ломаем структуру строки.
            when (random.nextInt(10)) {
                0 -> fields.add(texts.random(random))
                1 -> fields.removeAt(random.nextInt(fields.size))
            }
            val separator = if (random.nextInt(50) == 0) ";" else "|"
            val line = fields.joinToString(separator)

            val number = index + 1L
            when (val result = parseScheduleLine(number, line)) {
                is ParseResult.Accepted -> {
                    accepted++
                    assertEquals(number, result.lineNumber)
                }
                is ParseResult.Rejected -> {
                    val message = formatRejection(result)
                    assertTrue(message.startsWith("line $number: "), message)
                    assertTrue(message.all { it.code in 0x20..0x7E }, message)
                }
            }
        }
        assertTrue(accepted > 0, "the generator should also produce valid lines")
    }
}
