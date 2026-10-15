package schedule

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotSame

/** Задание со звёздочкой: перенос занятия. */
class RescheduleTest {

    private val newDate: LocalDate = LocalDate.of(2026, 11, 2)

    @Test
    fun `lecture is moved and stays a lecture`() {
        val original = lecture(teachers = listOf("Portnov", "Anokhin"))

        val result = original.rescheduledTo(newDate)

        // Статический тип результата — RescheduleResult<Lecture>.
        val moved: Lecture = assertIs<RescheduleResult.Moved<Lecture>>(result).session
        assertEquals(newDate, moved.date)
        assertEquals(original.topic, moved.topic)
        assertEquals(original.location, moved.location)
        assertEquals(original.teachers, moved.teachers)
        assertNotSame(original, moved)
    }

    @Test
    fun `original session is unchanged`() {
        val original = exam()
        original.rescheduledTo(newDate)
        assertEquals(OCT_5, original.date)
        assertEquals(exam(), original)
    }

    @Test
    fun `practice and exam keep their types`() {
        assertIs<Practice>(assertIs<RescheduleResult.Moved<Practice>>(practice().rescheduledTo(newDate)).session)
        assertIs<Exam>(assertIs<RescheduleResult.Moved<Exam>>(exam().rescheduledTo(newDate)).session)
    }

    @Test
    fun `works through the general Session type`() {
        val session: Session = practice()
        val moved = assertIs<RescheduleResult.Moved<Session>>(session.rescheduledTo(newDate)).session
        assertIs<Practice>(moved)
    }

    @Test
    fun `cancelled session is refused with a typed reason`() {
        val cancelled = cancellation()
        val refused = assertIs<RescheduleResult.Refused>(cancelled.rescheduledTo(newDate))
        assertEquals(RescheduleRefusal.SessionCancelled(cancelled), refused.reason)
    }

    @Test
    fun `date outside the allowed years is refused without exception`() {
        for (date in listOf(LocalDate.of(0, 1, 1), LocalDate.of(10000, 1, 1))) {
            val refused = assertIs<RescheduleResult.Refused>(lecture().rescheduledTo(date))
            assertEquals(RescheduleRefusal.DateOutOfRange(date), refused.reason)
        }
    }

    @Test
    fun `same date is not a move`() {
        val refused = assertIs<RescheduleResult.Refused>(lecture().rescheduledTo(OCT_5))
        assertEquals(RescheduleRefusal.SameDate(OCT_5), refused.reason)
    }
}
