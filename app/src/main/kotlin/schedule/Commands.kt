package schedule

interface CommandHandlers {
    fun period(command: Command.Period): Int
    fun byType(command: Command.ByType): Int
    fun errors(command: Command.Errors): Int
}

object StudentCommandHandlers : CommandHandlers {
    override fun period(command: Command.Period): Int = runPeriod(command)
    override fun byType(command: Command.ByType): Int = runType(command)
    override fun errors(command: Command.Errors): Int = runErrors(command)
}

private const val EXIT_OK = 0
private const val EXIT_REJECTED_LINES = 1

/** `schedule period`: занятия в диапазоне дат включительно, в порядке файла. */
fun runPeriod(command: Command.Period): Int {
    val period = command.from..command.to
    var total = 0L
    val stats = scanSchedule(command.file) { session ->
        if (session.date in period) {
            println(formatForPeriod(session))
            total++
        }
    }
    println("Total: $total")
    return exitCode(stats)
}

/** `schedule type`: занятия одного типа с преподавателями, в порядке файла. */
fun runType(command: Command.ByType): Int {
    var total = 0L
    val stats = scanSchedule(command.file) { session ->
        if (session.sessionType == command.type) {
            println(formatForType(session))
            total++
        }
    }
    println("Total: $total")
    return exitCode(stats)
}

/** `schedule errors`: причины отказов в порядке строк и итог `Rejected: K of M`. */
fun runErrors(command: Command.Errors): Int {
    val stats = scanSchedule(command.file, onRejected = { println(formatRejection(it)) })
    println("Rejected: ${stats.rejected} of ${stats.lines}")
    return exitCode(stats)
}

/** Итог проверки всего файла: сколько строк прочитано и сколько из них отклонено. */
private class ScanStats(val lines: Long, val rejected: Long)

/**
 * Проходит весь файл потоково: каждая строка разбирается и сразу передаётся дальше,
 * в памяти ничего не накапливается. Файл проверяется целиком, даже если занятие
 * не проходит фильтр команды, — ошибочные строки влияют на код завершения.
 *
 * Ошибки чтения не перехватываются: их обрабатывает готовая точка входа (`Cannot read file`).
 */
private fun scanSchedule(
    file: String,
    onRejected: (ParseResult.Rejected) -> Unit = {},
    onAccepted: (Session) -> Unit = {},
): ScanStats {
    var lines = 0L
    var rejected = 0L
    forEachScheduleLine(file) { number, line ->
        lines++
        when (val result = parseScheduleLine(number, line)) {
            is ParseResult.Accepted -> onAccepted(result.session)
            is ParseResult.Rejected -> {
                rejected++
                onRejected(result)
            }
        }
    }
    return ScanStats(lines, rejected)
}

private fun exitCode(stats: ScanStats): Int = if (stats.rejected == 0L) EXIT_OK else EXIT_REJECTED_LINES
