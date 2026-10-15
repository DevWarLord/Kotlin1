package schedule

import java.time.LocalDate

/** Результат переноса занятия. [T] — тип исходного занятия. */
sealed interface RescheduleResult<out T : Session> {

    /** Новое занятие того же типа; исходное не изменилось. */
    data class Moved<out T : Session>(val session: T) : RescheduleResult<T>

    data class Refused(val reason: RescheduleRefusal) : RescheduleResult<Nothing>
}

/** Типизированная причина отказа в переносе. */
sealed interface RescheduleRefusal {

    /** Отменённое занятие перенести нельзя. */
    data class SessionCancelled(val session: Cancellation) : RescheduleRefusal

    /** Год новой даты вне 1..9999. */
    data class DateOutOfRange(val date: LocalDate) : RescheduleRefusal

    /** Новая дата совпадает со старой — это не перенос. */
    data class SameDate(val date: LocalDate) : RescheduleRefusal
}

/**
 * Перенос действующего занятия на другую дату.
 *
 * Возвращает новое занятие того же класса с прежними темой, местом и преподавателями;
 * исходный объект не меняется (он и не может измениться — модель неизменяемая).
 * Новое занятие создаётся через конструктор, поэтому все инварианты проверяются заново.
 */
fun <T : Session> T.rescheduledTo(newDate: LocalDate): RescheduleResult<T> {
    val active: ActiveSession = when (val session: Session = this) {
        is Cancellation -> return RescheduleResult.Refused(RescheduleRefusal.SessionCancelled(session))
        is ActiveSession -> session
    }
    if (newDate.year !in VALID_YEARS) return RescheduleResult.Refused(RescheduleRefusal.DateOutOfRange(newDate))
    if (newDate == date) return RescheduleResult.Refused(RescheduleRefusal.SameDate(newDate))

    // withDate в каждом подклассе возвращает ровно свой класс (Lecture → Lecture и т. д.),
    // поэтому приведение к T безопасно.
    @Suppress("UNCHECKED_CAST")
    return RescheduleResult.Moved(active.withDate(newDate) as T)
}
