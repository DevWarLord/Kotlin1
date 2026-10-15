package schedule

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SessionRepositoryTest {

    @Test
    fun `add and addAll keep the original order`() {
        val repository = InMemorySessionRepository()
        val first = lecture()
        val batch = listOf(exam(), cancellation(), practice())

        repository.add(first)
        repository.addAll(batch)

        assertEquals(listOf(first) + batch, repository.all())
    }

    @Test
    fun `equal sessions are stored separately`() {
        val repository = InMemorySessionRepository()
        repository.add(lecture())
        repository.add(lecture())
        repository.addAll(listOf(lecture(), lecture()))
        assertEquals(4, repository.all().size)
    }

    @Test
    fun `snapshot does not change after later additions`() {
        val repository = InMemorySessionRepository()
        repository.add(lecture())
        val snapshot = repository.all()

        repository.add(exam())
        repository.addAll(listOf(practice(), cancellation()))

        assertEquals(listOf<Session>(lecture()), snapshot)
        assertEquals(4, repository.all().size)
    }

    @Test
    fun `snapshot cannot modify the repository`() {
        val repository = InMemorySessionRepository()
        repository.add(lecture())

        @Suppress("UNCHECKED_CAST")
        val snapshot = repository.all() as MutableList<Session>
        assertFailsWith<UnsupportedOperationException> { snapshot.add(exam()) }
        assertFailsWith<UnsupportedOperationException> { snapshot.clear() }

        assertEquals(1, repository.all().size)
    }

    @Test
    fun `changing the list passed to addAll does not change the repository`() {
        val repository = InMemorySessionRepository()
        val batch = mutableListOf<Session>(lecture(), exam())
        repository.addAll(batch)

        batch.clear()

        assertEquals(2, repository.all().size)
    }

    @Test
    fun `addAll of own snapshot doubles the content`() {
        val repository = InMemorySessionRepository()
        repository.addAll(listOf(lecture(), exam()))
        repository.addAll(repository.all())
        assertEquals(listOf(lecture(), exam(), lecture(), exam()), repository.all())
    }

    @Test
    fun `addAll of an empty list adds nothing`() {
        val repository = InMemorySessionRepository()
        repository.addAll(emptyList())
        assertEquals(emptyList(), repository.all())
    }
}
