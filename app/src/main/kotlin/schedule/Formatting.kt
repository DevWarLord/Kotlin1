package schedule

/** Сообщение об отклонённой строке: `line N: <причина>`. */
fun formatRejection(rejected: ParseResult.Rejected): String =
    "line ${rejected.lineNumber}: ${describe(rejected.reason)}"

/**
 * Английский текст причины. Значения из входной строки проходят через [quoted],
 * поэтому повреждённый вход не добавит в сообщение перевод строки или не-ASCII текст.
 */
fun describe(reason: RejectReason): String = when (reason) {
    RejectReason.InvalidSeparator ->
        "invalid field separator"

    is RejectReason.WrongFieldCount ->
        "got ${reason.actual} fields, expected 5"

    is RejectReason.EmptyField ->
        "empty required field \"${reason.field.label}\""

    is RejectReason.InvalidDate ->
        "invalid date ${quoted(reason.value)}"

    is RejectReason.UnknownType ->
        "unknown session type ${quoted(reason.value)}"

    is RejectReason.NotEnoughTeachers ->
        "type \"${reason.type.name}\" requires at least ${reason.required} teachers, got ${reason.actual}"

    is RejectReason.ControlCharacter ->
        "control character in field \"${reason.field.label}\": ${quoted(reason.value)}"

    is RejectReason.OuterWhitespace ->
        "leading or trailing whitespace in field \"${reason.field.label}\": ${quoted(reason.value)}"

    is RejectReason.CancelledFieldNotMissing ->
        "type \"CANCELLED\" requires \"-\" in field \"${reason.field.label}\", got ${quoted(reason.value)}"

    is RejectReason.MissingLocation ->
        "type \"${reason.type.name}\" requires a location, got \"-\""

    is RejectReason.InvalidTeacher ->
        "invalid teacher name ${quoted(reason.value)} at position ${reason.position}"

    is RejectReason.DuplicateTeacher ->
        "duplicate teacher ${quoted(reason.name)}"
}

/** Строка команды `period`: `date | TYPE | topic | location`. */
fun formatForPeriod(session: Session): String =
    listOf(session.date.toString(), session.sessionType.name, session.topic, session.locationOrMissing())
        .joinToString(" | ")

/** Строка команды `type`: как у `period`, плюс преподаватели через `, `. */
fun formatForType(session: Session): String =
    formatForPeriod(session) + " | " + session.teachersOrMissing()

private fun Session.locationOrMissing(): String = when (this) {
    is ActiveSession -> location
    is Cancellation -> MISSING_VALUE
}

private fun Session.teachersOrMissing(): String = when (this) {
    is ActiveSession -> teachers.joinToString(", ")
    is Cancellation -> MISSING_VALUE
}
