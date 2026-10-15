package schedule

/** Поля строки файла в порядке следования. [label] — имя поля в сообщениях. */
enum class Field(val label: String) {
    DATE("date"),
    TYPE("type"),
    TOPIC("topic"),
    LOCATION("location"),
    TEACHERS("teachers"),
}

/** Результат разбора одной строки файла. Номер строки начинается с единицы. */
sealed interface ParseResult {
    val lineNumber: Long

    data class Accepted(override val lineNumber: Long, val session: Session) : ParseResult

    data class Rejected(override val lineNumber: Long, val reason: RejectReason) : ParseResult
}

/**
 * Структурированная причина отказа: только данные из входной строки, без готового текста
 * и без Throwable. Текст строит отдельная функция [describe].
 */
sealed interface RejectReason {

    /** 1. В строке нет ни одного `|`. */
    data object InvalidSeparator : RejectReason

    /** 2. Полей не пять. */
    data class WrongFieldCount(val actual: Int) : RejectReason

    /** 3. Первое пустое или полностью пробельное поле. */
    data class EmptyField(val field: Field) : RejectReason

    /** 4. Дата не в формате YYYY-MM-DD, год вне 0001..9999 или такого дня нет. */
    data class InvalidDate(val value: String) : RejectReason

    /** 5. Тип не из LECTURE, PRACTICE, EXAM, CANCELLED (регистр значим). */
    data class UnknownType(val value: String) : RejectReason

    /** 6. У сдачи меньше двух непустых элементов списка, отличных от `-`. */
    data class NotEnoughTeachers(val type: SessionType, val required: Int, val actual: Int) : RejectReason

    // 7. Остальные нарушения домена — в порядке проверки.

    /** 7.1. Управляющий символ в теме, месте или имени преподавателя. */
    data class ControlCharacter(val field: Field, val value: String) : RejectReason

    /** 7.2. Пробельный символ в начале или конце темы, места или имени преподавателя. */
    data class OuterWhitespace(val field: Field, val value: String) : RejectReason

    /** 7.3. У отмены место или преподаватели записаны не как `-`. */
    data class CancelledFieldNotMissing(val field: Field, val value: String) : RejectReason

    /** 7.4. У действующего занятия вместо места стоит `-`. */
    data class MissingLocation(val type: SessionType) : RejectReason

    /** 7.5. Пустой элемент или `-` в списке преподавателей; [position] начинается с единицы. */
    data class InvalidTeacher(val position: Int, val value: String) : RejectReason

    /** 7.6. Имя преподавателя встречается повторно. */
    data class DuplicateTeacher(val name: String) : RejectReason
}
