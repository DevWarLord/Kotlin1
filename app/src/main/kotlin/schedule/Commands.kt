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

fun runPeriod(command: Command.Period): Int {
    // TODO: parse every line of command.file, filter by command.from..command.to,
    // print the selected sessions and Total, return 0 or 1.
    println("Not implemented: period")
    return 2
}

fun runType(command: Command.ByType): Int {
    // TODO: parse every line of command.file, filter by command.type,
    // print the selected sessions with teachers and Total, return 0 or 1.
    println("Not implemented: type")
    return 2
}

fun runErrors(command: Command.Errors): Int {
    // TODO: parse every line of command.file, print formatted rejections
    // and Rejected: K of M, return 0 or 1.
    println("Not implemented: errors")
    return 2
}
