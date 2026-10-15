package schedule

import java.util.Collections

/** Операция добавления, попавшая в журнал. */
enum class AuditOperation { ADD, ADD_ALL }

/** Событие журнала: какая операция и сколько занятий она добавила. */
data class AuditEvent(val operation: AuditOperation, val count: Int)

/**
 * Реестр с журналом добавлений.
 *
 * Интерфейс реализуется делегированием (`by delegate`): `all()` компилятор пробрасывает
 * во внутренний реестр сам, и он не журналируется. `add` и `addAll` переопределены явно
 * и пишут ровно одно событие на один успешный вызов.
 *
 * Переопределить только `add` было бы недостаточно: сгенерированный `addAll` вызывает
 * `delegate.addAll(...)`, и даже если внутренний реестр внутри себя зовёт `add`,
 * это его собственный `add`, а не метод обёртки. Делегирование — не наследование:
 * обратного виртуального вызова в обёртку нет.
 */
class AuditedSessionRepository(
    private val delegate: SessionRepository,
) : SessionRepository by delegate {

    private val events = ArrayList<AuditEvent>()

    /** Сколько занятий успешно добавлено через эту обёртку. */
    var addedCount: Int = 0
        private set

    override fun add(session: Session) {
        delegate.add(session)
        record(AuditOperation.ADD, 1)
    }

    override fun addAll(sessions: List<Session>) {
        // Снимок фиксирует пакет: в журнал попадёт ровно столько, сколько передано внутрь.
        val batch = sessions.toList()
        delegate.addAll(batch)
        record(AuditOperation.ADD_ALL, batch.size)
    }

    /** Снимок журнала в порядке операций. */
    fun auditLog(): List<AuditEvent> = Collections.unmodifiableList(ArrayList(events))

    // Вызывается только после успешного возврата из делегата: неудачная операция в журнал не попадает.
    private fun record(operation: AuditOperation, count: Int) {
        events.add(AuditEvent(operation, count))
        addedCount += count
    }
}
