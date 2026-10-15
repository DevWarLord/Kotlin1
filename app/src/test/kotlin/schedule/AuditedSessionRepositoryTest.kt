package schedule

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AuditedSessionRepositoryTest {

    @Test
    fun `one add and a batch of three give four sessions and two events`() {
        val repository = AuditedSessionRepository(InMemorySessionRepository())

        repository.add(lecture())
        repository.addAll(listOf(practice(), exam(), cancellation()))

        assertEquals(4, repository.all().size) // Sessions: 4
        assertEquals(4, repository.addedCount) // Added: 4
        assertEquals(2, repository.auditLog().size) // Audit events: 2
        assertEquals(
            listOf(AuditEvent(AuditOperation.ADD, 1), AuditEvent(AuditOperation.ADD_ALL, 3)),
            repository.auditLog(),
        )
    }

    @Test
    fun `empty batch creates an event with zero`() {
        val repository = AuditedSessionRepository(InMemorySessionRepository())
        repository.addAll(emptyList())
        assertEquals(listOf(AuditEvent(AuditOperation.ADD_ALL, 0)), repository.auditLog())
        assertEquals(0, repository.addedCount)
    }

    @Test
    fun `all creates no events and is delegated`() {
        val inner = InMemorySessionRepository()
        inner.add(lecture())
        val repository = AuditedSessionRepository(inner)

        repository.all()
        repository.all()

        assertEquals(emptyList(), repository.auditLog())
        assertEquals(listOf<Session>(lecture()), repository.all())
    }

    @Test
    fun `storage is done by the delegate`() {
        val inner = InMemorySessionRepository()
        val repository = AuditedSessionRepository(inner)
        repository.add(exam())
        assertEquals(listOf<Session>(exam()), inner.all())
    }

    @Test
    fun `delegate that implements addAll through add does not cause double logging`() {
        // Внутренний реестр зовёт свой add из addAll. Это вызов на делегате, а не на обёртке,
        // поэтому в журнале остаётся одно событие ADD_ALL, а не три события ADD.
        val inner = object : SessionRepository {
            val stored = ArrayList<Session>()
            override fun add(session: Session) {
                stored.add(session)
            }
            override fun addAll(sessions: List<Session>) = sessions.forEach { add(it) }
            override fun all(): List<Session> = stored.toList()
        }
        val repository = AuditedSessionRepository(inner)

        repository.addAll(listOf(lecture(), exam(), practice()))

        assertEquals(listOf(AuditEvent(AuditOperation.ADD_ALL, 3)), repository.auditLog())
        assertEquals(3, repository.addedCount)
        assertEquals(3, inner.stored.size)
    }

    @Test
    fun `failed operation is not logged`() {
        val failing = object : SessionRepository {
            override fun add(session: Session) = throw IllegalStateException("storage is full")
            override fun addAll(sessions: List<Session>) = throw IllegalStateException("storage is full")
            override fun all(): List<Session> = emptyList()
        }
        val repository = AuditedSessionRepository(failing)

        assertFailsWith<IllegalStateException> { repository.add(lecture()) }
        assertFailsWith<IllegalStateException> { repository.addAll(listOf(lecture())) }

        assertEquals(emptyList(), repository.auditLog())
        assertEquals(0, repository.addedCount)
    }

    @Test
    fun `audit log snapshot cannot change the log`() {
        val repository = AuditedSessionRepository(InMemorySessionRepository())
        repository.add(lecture())
        val log = repository.auditLog()

        repository.add(exam())
        @Suppress("UNCHECKED_CAST")
        assertFailsWith<UnsupportedOperationException> { (log as MutableList<AuditEvent>).clear() }

        assertEquals(1, log.size)
        assertEquals(2, repository.auditLog().size)
    }
}
