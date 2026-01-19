package org.alkaline.taskbrain.data

import com.google.firebase.Timestamp
import org.junit.Assert.*
import org.junit.Test
import java.util.Date

class AlarmTest {

    // region SnoozeDuration tests

    @Test
    fun `SnoozeDuration TWO_MINUTES has correct minutes value`() {
        assertEquals(2, SnoozeDuration.TWO_MINUTES.minutes)
    }

    @Test
    fun `SnoozeDuration TEN_MINUTES has correct minutes value`() {
        assertEquals(10, SnoozeDuration.TEN_MINUTES.minutes)
    }

    @Test
    fun `SnoozeDuration ONE_HOUR has correct minutes value`() {
        assertEquals(60, SnoozeDuration.ONE_HOUR.minutes)
    }

    // endregion

    // region AlarmPriority tests

    @Test
    fun `AlarmPriority ordering is UPCOMING less than NOTIFY`() {
        assertTrue(AlarmPriority.UPCOMING < AlarmPriority.NOTIFY)
    }

    @Test
    fun `AlarmPriority ordering is NOTIFY less than URGENT`() {
        assertTrue(AlarmPriority.NOTIFY < AlarmPriority.URGENT)
    }

    @Test
    fun `AlarmPriority ordering is URGENT less than ALARM`() {
        assertTrue(AlarmPriority.URGENT < AlarmPriority.ALARM)
    }

    @Test
    fun `AlarmPriority ALARM is highest priority`() {
        val priorities = AlarmPriority.entries
        assertEquals(AlarmPriority.ALARM, priorities.maxOrNull())
    }

    // endregion

    // region AlarmStatus tests

    @Test
    fun `AlarmStatus has three values`() {
        assertEquals(3, AlarmStatus.entries.size)
    }

    @Test
    fun `AlarmStatus contains PENDING DONE CANCELLED`() {
        val statuses = AlarmStatus.entries.map { it.name }
        assertTrue(statuses.containsAll(listOf("PENDING", "DONE", "CANCELLED")))
    }

    // endregion

    // region AlarmType tests

    @Test
    fun `AlarmType has three values`() {
        assertEquals(3, AlarmType.entries.size)
    }

    @Test
    fun `AlarmType contains NOTIFY URGENT ALARM`() {
        val types = AlarmType.entries.map { it.name }
        assertTrue(types.containsAll(listOf("NOTIFY", "URGENT", "ALARM")))
    }

    // endregion

    // region Alarm data class tests

    @Test
    fun `Alarm default values are correct`() {
        val alarm = Alarm()

        assertEquals("", alarm.id)
        assertEquals("", alarm.userId)
        assertEquals("", alarm.noteId)
        assertEquals("", alarm.lineContent)
        assertNull(alarm.createdAt)
        assertNull(alarm.updatedAt)
        assertNull(alarm.upcomingTime)
        assertNull(alarm.notifyTime)
        assertNull(alarm.urgentTime)
        assertNull(alarm.alarmTime)
        assertEquals(AlarmStatus.PENDING, alarm.status)
        assertNull(alarm.snoozedUntil)
    }

    @Test
    fun `Alarm copy preserves values`() {
        val now = Timestamp(Date())
        val alarm = Alarm(
            id = "alarm_1",
            userId = "user_1",
            noteId = "note_1",
            lineContent = "Test content",
            upcomingTime = now,
            status = AlarmStatus.DONE
        )

        val copy = alarm.copy(lineContent = "Updated content")

        assertEquals("alarm_1", copy.id)
        assertEquals("user_1", copy.userId)
        assertEquals("note_1", copy.noteId)
        assertEquals("Updated content", copy.lineContent)
        assertEquals(now, copy.upcomingTime)
        assertEquals(AlarmStatus.DONE, copy.status)
    }

    @Test
    fun `Alarm equality works correctly`() {
        val now = Timestamp(Date())
        val alarm1 = Alarm(id = "alarm_1", noteId = "note_1", upcomingTime = now)
        val alarm2 = Alarm(id = "alarm_1", noteId = "note_1", upcomingTime = now)

        assertEquals(alarm1, alarm2)
    }

    @Test
    fun `Alarm inequality works correctly`() {
        val alarm1 = Alarm(id = "alarm_1")
        val alarm2 = Alarm(id = "alarm_2")

        assertNotEquals(alarm1, alarm2)
    }

    // endregion
}
