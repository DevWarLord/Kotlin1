package schedule

import java.time.LocalDate
import java.time.YearMonth

private const val FIELD_SEPARATOR = '|'
private const val TEACHER_SEPARATOR = ','
private const val FIELD_COUNT = 5
private const val EXAM_MIN_TEACHERS = 2

private val ISO_DATE = Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")

/**
 * Разбирает строку `date|type|topic|location|teachers`.
 *
 * Возвращает одну причину отказа в порядке приоритета из задания. Исключения для
 * управления потоком не используются: всё, что может нарушить контракт модели, проверено
 * до вызова конструктора. Если конструктор всё же бросит исключение — это ошибка
 * в программе, и она не маскируется под отказ входной строки.
 */
fun parseScheduleLine(lineNumber: Long, line: String): ParseResult {
    fun reject(reason: RejectReason) = ParseResult.Rejected(lineNumber, reason)

    // 1. Разделитель.
    if (FIELD_SEPARATOR !in line) return reject(RejectReason.InvalidSeparator)

    // 2. Количество полей. split в Kotlin сохраняет конечные пустые поля.
    val fields = line.split(FIELD_SEPARATOR)
    if (fields.size != FIELD_COUNT) return reject(RejectReason.WrongFieldCount(fields.size))

    // 3. Первое пустое или пробельное поле.
    val emptyIndex = fields.indexOfFirst { it.isBlank() }
    if (emptyIndex >= 0) return reject(RejectReason.EmptyField(Field.entries[emptyIndex]))

    val (rawDate, rawType, topic, location, rawTeachers) = fields

    // 4. Дата.
    val date = parseIsoDate(rawDate) ?: return reject(RejectReason.InvalidDate(rawDate))

    // 5. Тип.
    val type = sessionTypeOf(rawType) ?: return reject(RejectReason.UnknownType(rawType))

    val teachers = rawTeachers.split(TEACHER_SEPARATOR)

    // 6. Минимум преподавателей у сдачи.
    if (type == SessionType.EXAM) {
        val usable = teachers.count { it.isNotBlank() && it != MISSING_VALUE }
        if (usable < EXAM_MIN_TEACHERS) {
            return reject(RejectReason.NotEnoughTeachers(type, EXAM_MIN_TEACHERS, usable))
        }
    }

    // 7. Остальные нарушения домена.
    findDomainViolation(type, topic, location, rawTeachers, teachers)?.let { return reject(it) }

    val session = when (type) {
        SessionType.LECTURE -> Lecture(date, topic, location, teachers)
        SessionType.PRACTICE -> Practice(date, topic, location, teachers)
        SessionType.EXAM -> Exam(date, topic, location, teachers)
        SessionType.CANCELLED -> Cancellation(date, topic)
    }
    return ParseResult.Accepted(lineNumber, session)
}

/**
 * Дата строго в виде YYYY-MM-DD (только ASCII-цифры), год 0001..9999, день существует.
 * Возвращает null, если дата некорректна.
 */
internal fun parseIsoDate(text: String): LocalDate? {
    if (!ISO_DATE.matches(text)) return null
    val year = text.substring(0, 4).toInt()
    val month = text.substring(5, 7).toInt()
    val day = text.substring(8, 10).toInt()
    if (year !in VALID_YEARS || month !in 1..12) return null
    if (day !in 1..YearMonth.of(year, month).lengthOfMonth()) return null
    return LocalDate.of(year, month, day)
}

/**
 * Пункт 7 в заданном порядке. Каждая категория проверяется по всем полям сразу,
 * и выбирается первое поле в порядке следования (в списке — первый элемент).
 */
private fun findDomainViolation(
    type: SessionType,
    topic: String,
    location: String,
    rawTeachers: String,
    teachers: List<String>,
): RejectReason? {
    val texts: List<Pair<Field, String>> =
        listOf(Field.TOPIC to topic, Field.LOCATION to location) + teachers.map { Field.TEACHERS to it }

    // 7.1. Управляющие символы.
    texts.firstOrNull { (_, value) -> TextRules.hasControlCharacter(value) }
        ?.let { (field, value) -> return RejectReason.ControlCharacter(field, value) }

    // 7.2. Внешние пробельные символы.
    texts.firstOrNull { (_, value) -> TextRules.hasOuterWhitespace(value) }
        ?.let { (field, value) -> return RejectReason.OuterWhitespace(field, value) }

    // 7.3. Поля отмены. Дальнейшие проверки места и преподавателей к отмене не относятся.
    if (type == SessionType.CANCELLED) {
        if (location != MISSING_VALUE) return RejectReason.CancelledFieldNotMissing(Field.LOCATION, location)
        if (rawTeachers != MISSING_VALUE) return RejectReason.CancelledFieldNotMissing(Field.TEACHERS, rawTeachers)
        return null
    }

    // 7.4. Место действующего занятия (пустое отсеяно в пункте 3).
    if (location == MISSING_VALUE) return RejectReason.MissingLocation(type)

    // 7.5. Недопустимые элементы списка преподавателей.
    teachers.forEachIndexed { index, name ->
        if (name.isEmpty() || name == MISSING_VALUE) return RejectReason.InvalidTeacher(index + 1, name)
    }

    // 7.6. Повторы (регистр значим).
    val seen = HashSet<String>()
    teachers.firstOrNull { !seen.add(it) }?.let { return RejectReason.DuplicateTeacher(it) }

    return null
}
