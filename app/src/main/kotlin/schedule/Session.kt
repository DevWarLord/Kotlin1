package schedule

import java.time.LocalDate
import java.util.Objects

/**
 * Занятие расписания.
 *
 * Интерфейс sealed: все варианты известны компилятору, поэтому `when (session)`
 * проверяется на полноту, а «пятый тип занятия» вне этого файла создать нельзя.
 */
sealed interface Session {
    val date: LocalDate
    val topic: String
}

/**
 * Действующее (не отменённое) занятие: лекция, практикум или сдача.
 *
 * Все проверки и защитная копия списка преподавателей выполняются здесь, в конструкторе,
 * один раз для всех трёх типов. Подклассы лишь сообщают минимальное число преподавателей.
 *
 * [minTeachers] передаётся через конструктор, а не через `abstract val`: конструктор базового
 * класса выполняется раньше инициализации полей подкласса, и переопределённое свойство
 * в этот момент было бы ещё не заполнено.
 */
sealed class ActiveSession(
    final override val date: LocalDate,
    final override val topic: String,
    val location: String,
    teachers: List<String>,
    minTeachers: Int,
) : Session {

    /** Неизменяемый список: ни исходный список, ни полученный через свойство не меняют объект. */
    val teachers: List<String>

    init {
        val kind = javaClass.simpleName
        requireValidYear(kind, date)
        requireValidText(kind, "topic", topic)
        requireValidText(kind, "location", location)
        require(location != MISSING_VALUE) { "$kind: location must be a real place, got \"-\"" }

        // Сначала снимок, потом проверки: проверяем ровно то, что сохраним,
        // и последующие изменения переданного списка объект уже не затронут.
        val snapshot = ArrayList(teachers)
        requireValidTeachers(kind, snapshot, minTeachers)
        // Наружу — неизменяемый список JDK: add/clear/set бросают UnsupportedOperationException
        // даже после приведения к MutableList. Обычный ArrayList (и результат toList()) так изменить можно.
        this.teachers = java.util.List.copyOf(snapshot)
    }

    /** Новое занятие того же типа с другой датой. Идёт через конструктор, поэтому инварианты сохраняются. */
    internal abstract fun withDate(newDate: LocalDate): ActiveSession

    final override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || other.javaClass != javaClass) return false
        other as ActiveSession
        return date == other.date &&
            topic == other.topic &&
            location == other.location &&
            teachers == other.teachers
    }

    final override fun hashCode(): Int = Objects.hash(javaClass.name, date, topic, location, teachers)

    final override fun toString(): String =
        "${javaClass.simpleName}(date=$date, topic=$topic, location=$location, teachers=$teachers)"
}

/** Лекция: место и хотя бы один преподаватель. */
class Lecture(date: LocalDate, topic: String, location: String, teachers: List<String>) :
    ActiveSession(date, topic, location, teachers, minTeachers = 1) {
    override fun withDate(newDate: LocalDate): Lecture = Lecture(newDate, topic, location, teachers)
}

/** Практикум: место и хотя бы один преподаватель. */
class Practice(date: LocalDate, topic: String, location: String, teachers: List<String>) :
    ActiveSession(date, topic, location, teachers, minTeachers = 1) {
    override fun withDate(newDate: LocalDate): Practice = Practice(newDate, topic, location, teachers)
}

/** Сдача: место и не менее двух разных преподавателей. */
class Exam(date: LocalDate, topic: String, location: String, teachers: List<String>) :
    ActiveSession(date, topic, location, teachers, minTeachers = 2) {
    override fun withDate(newDate: LocalDate): Exam = Exam(newDate, topic, location, teachers)
}

/**
 * Отмена: только дата и тема. Свойств места и преподавателей нет вовсе (даже nullable),
 * поэтому обратиться к ним у отмены нельзя уже на этапе компиляции.
 */
class Cancellation(
    override val date: LocalDate,
    override val topic: String,
) : Session {

    init {
        requireValidYear("Cancellation", date)
        requireValidText("Cancellation", "topic", topic)
    }

    override fun equals(other: Any?): Boolean =
        this === other || (other is Cancellation && date == other.date && topic == other.topic)

    override fun hashCode(): Int = Objects.hash(javaClass.name, date, topic)

    override fun toString(): String = "Cancellation(date=$date, topic=$topic)"
}

// ---------------------------------------------------------------------------
// Проверки контракта модели. Нарушение из программного кода — IllegalArgumentException.
// ---------------------------------------------------------------------------

private fun requireValidYear(kind: String, date: LocalDate) {
    require(date.year in VALID_YEARS) { "$kind: year must be in 1..9999, got ${date.year}" }
}

private fun requireValidText(kind: String, what: String, value: String) {
    require(value.isNotBlank()) { "$kind: $what must not be empty or blank, got ${quoted(value)}" }
    require(!TextRules.hasControlCharacter(value)) {
        "$kind: $what must not contain control characters, got ${quoted(value)}"
    }
    require(!TextRules.hasOuterWhitespace(value)) {
        "$kind: $what must not have leading or trailing whitespace, got ${quoted(value)}"
    }
}

private fun requireValidTeachers(kind: String, teachers: List<String>, minTeachers: Int) {
    // Защита от вызова из Java: в List<String> там может оказаться null.
    val nullable: List<String?> = teachers
    val nullIndex = nullable.indexOf(null)
    require(nullIndex < 0) { "$kind: teacher #${nullIndex + 1} must not be null" }

    teachers.forEachIndexed { index, name ->
        val what = "teacher #${index + 1}"
        requireValidText(kind, what, name)
        require(name != MISSING_VALUE) { "$kind: $what must be a name, got \"-\"" }
    }

    // Регистр значим: "Ivanov" и "ivanov" — разные имена, поэтому сравнение точное.
    val seen = HashSet<String>()
    val duplicate = teachers.firstOrNull { !seen.add(it) }
    require(duplicate == null) { "$kind: duplicate teacher ${quoted(duplicate.orEmpty())}" }

    require(teachers.size >= minTeachers) {
        "$kind requires at least $minTeachers different teacher(s), got ${teachers.size}"
    }
}
