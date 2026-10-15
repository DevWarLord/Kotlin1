package schedule

import java.util.Collections

/** Хранилище занятий в памяти. */
interface SessionRepository {
    fun add(session: Session)
    fun addAll(sessions: List<Session>)
    fun all(): List<Session>
}

/**
 * Простой реестр в памяти. Одинаковые занятия допускаются и хранятся отдельно,
 * порядок добавления сохраняется.
 */
class InMemorySessionRepository : SessionRepository {

    private val sessions = ArrayList<Session>()

    override fun add(session: Session) {
        sessions.add(session)
    }

    override fun addAll(sessions: List<Session>) {
        // ArrayList.addAll сначала берёт toArray() у аргумента, поэтому безопасен
        // и для repository.addAll(repository.all()).
        this.sessions.addAll(sessions)
    }

    /**
     * Снимок: новая копия, обёрнутая в неизменяемое представление.
     * Последующие add/addAll его не меняют, а через него нельзя изменить реестр.
     */
    override fun all(): List<Session> = Collections.unmodifiableList(ArrayList(sessions))
}
