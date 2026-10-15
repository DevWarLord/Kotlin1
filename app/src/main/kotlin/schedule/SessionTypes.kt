package schedule

/**
 * Связь классов модели с метками [SessionType] из шаблона (метки файла и CLI).
 * Метка — способ записи типа, а не замена четырёх классов.
 */
val Session.sessionType: SessionType
    get() = when (this) {
        is Lecture -> SessionType.LECTURE
        is Practice -> SessionType.PRACTICE
        is Exam -> SessionType.EXAM
        is Cancellation -> SessionType.CANCELLED
    }

/** Метка по точному написанию (регистр значим), или null для неизвестной. */
fun sessionTypeOf(label: String): SessionType? = SessionType.entries.firstOrNull { it.name == label }
