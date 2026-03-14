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
        assertNull(alarm.dueTime)
        assertEquals(Alarm.DEFAULT_STAGES, alarm.stages)
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
            dueTime = now,
            status = AlarmStatus.DONE
        )

        val copy = alarm.copy(lineContent = "Updated content")

        assertEquals("alarm_1", copy.id)
        assertEquals("user_1", copy.userId)
        assertEquals("note_1", copy.noteId)
        assertEquals("Updated content", copy.lineContent)
        assertEquals(now, copy.dueTime)
        assertEquals(AlarmStatus.DONE, copy.status)
    }

    @Test
    fun `Alarm equality works correctly`() {
        val now = Timestamp(Date())
        val alarm1 = Alarm(id = "alarm_1", noteId = "note_1", dueTime = now)
        val alarm2 = Alarm(id = "alarm_1", noteId = "note_1", dueTime = now)

        assertEquals(alarm1, alarm2)
    }

    @Test
    fun `Alarm inequality works correctly`() {
        val alarm1 = Alarm(id = "alarm_1")
        val alarm2 = Alarm(id = "alarm_2")

        assertNotEquals(alarm1, alarm2)
    }

    // endregion

    // region latestThresholdTime tests

    @Test
    fun `latestThresholdTime returns null when dueTime not set`() {
        val alarm = Alarm(id = "test")
        assertNull(alarm.latestThresholdTime)
    }

    @Test
    fun `latestThresholdTime returns dueTime when set`() {
        val time = Timestamp(Date(5000))
        val alarm = Alarm(id = "test", dueTime = time)
        assertEquals(time, alarm.latestThresholdTime)
    }

    // endregion

    // region AlarmStage tests

    @Test
    fun `AlarmStage resolveTime returns dueTime minus offset`() {
        val dueTime = Timestamp(Date(10000000L))
        val stage = AlarmStage(AlarmStageType.NOTIFICATION, offsetMs = 3600000L)

        val resolved = stage.resolveTime(dueTime)

        assertEquals(10000000L - 3600000L, resolved.toDate().time)
    }

    @Test
    fun `AlarmStage resolveTime returns absoluteTime when set`() {
        val dueTime = Timestamp(Date(10000000L))
        val absoluteTime = Timestamp(Date(5000000L))
        val stage = AlarmStage(
            AlarmStageType.NOTIFICATION,
            offsetMs = 3600000L,
            absoluteTime = absoluteTime
        )

        val resolved = stage.resolveTime(dueTime)

        assertEquals(5000000L, resolved.toDate().time)
    }

    @Test
    fun `AlarmStageType toAlarmType maps correctly`() {
        assertEquals(AlarmType.ALARM, AlarmStageType.SOUND_ALARM.toAlarmType())
        assertEquals(AlarmType.URGENT, AlarmStageType.LOCK_SCREEN.toAlarmType())
        assertEquals(AlarmType.NOTIFY, AlarmStageType.NOTIFICATION.toAlarmType())
    }

    @Test
    fun `enabledStages filters disabled stages`() {
        val stages = listOf(
            AlarmStage(AlarmStageType.SOUND_ALARM, enabled = true),
            AlarmStage(AlarmStageType.LOCK_SCREEN, enabled = false),
            AlarmStage(AlarmStageType.NOTIFICATION, enabled = true)
        )
        val alarm = Alarm(id = "test", stages = stages)

        assertEquals(2, alarm.enabledStages.size)
        assertTrue(alarm.enabledStages.all { it.enabled })
    }

    // endregion

    // region earliestThresholdTime tests

    @Test
    fun `earliestThresholdTime returns null when dueTime not set`() {
        val alarm = Alarm(id = "test", dueTime = null)
        assertNull(alarm.earliestThresholdTime)
    }

    @Test
    fun `earliestThresholdTime returns null when no stages enabled`() {
        val alarm = Alarm(
            id = "test",
            dueTime = Timestamp(Date(10000000L)),
            stages = listOf(
                AlarmStage(AlarmStageType.SOUND_ALARM, offsetMs = 0, enabled = false),
                AlarmStage(AlarmStageType.NOTIFICATION, offsetMs = 3600000L, enabled = false)
            )
        )
        assertNull(alarm.earliestThresholdTime)
    }

    @Test
    fun `earliestThresholdTime returns earliest enabled stage time`() {
        val dueTime = Timestamp(Date(10000000L))
        val alarm = Alarm(
            id = "test",
            dueTime = dueTime,
            stages = listOf(
                AlarmStage(AlarmStageType.SOUND_ALARM, offsetMs = 0, enabled = true),
                AlarmStage(AlarmStageType.LOCK_SCREEN, offsetMs = 1800000L, enabled = true),
                AlarmStage(AlarmStageType.NOTIFICATION, offsetMs = 7200000L, enabled = true)
            )
        )

        // Notification has the largest offset, so its resolved time is the earliest
        val expected = Timestamp(Date(10000000L - 7200000L))
        assertEquals(expected, alarm.earliestThresholdTime)
    }

    @Test
    fun `earliestThresholdTime skips disabled stages`() {
        val dueTime = Timestamp(Date(10000000L))
        val alarm = Alarm(
            id = "test",
            dueTime = dueTime,
            stages = listOf(
                AlarmStage(AlarmStageType.SOUND_ALARM, offsetMs = 0, enabled = true),
                AlarmStage(AlarmStageType.NOTIFICATION, offsetMs = 7200000L, enabled = false)
            )
        )

        // Only sound alarm is enabled (offset 0), so earliest = dueTime
        assertEquals(dueTime, alarm.earliestThresholdTime)
    }

    @Test
    fun `earliestThresholdTime uses absoluteTime when set`() {
        val dueTime = Timestamp(Date(10000000L))
        val earlyAbsolute = Timestamp(Date(1000000L))
        val alarm = Alarm(
            id = "test",
            dueTime = dueTime,
            stages = listOf(
                AlarmStage(AlarmStageType.SOUND_ALARM, offsetMs = 0, enabled = true),
                AlarmStage(AlarmStageType.NOTIFICATION, offsetMs = 0, enabled = true, absoluteTime = earlyAbsolute)
            )
        )

        assertEquals(earlyAbsolute, alarm.earliestThresholdTime)
    }

    // endregion

    // region displayName tests

    @Test
    fun `displayName returns plain text unchanged`() {
        val alarm = Alarm(lineContent = "Buy groceries")
        assertEquals("Buy groceries", alarm.displayName)
    }

    @Test
    fun `displayName strips leading tabs`() {
        val alarm = Alarm(lineContent = "\t\tNested task")
        assertEquals("Nested task", alarm.displayName)
    }

    @Test
    fun `displayName strips bullet prefix`() {
        val alarm = Alarm(lineContent = "• Bullet item")
        assertEquals("Bullet item", alarm.displayName)
    }

    @Test
    fun `displayName strips unchecked checkbox prefix`() {
        val alarm = Alarm(lineContent = "☐ Todo item")
        assertEquals("Todo item", alarm.displayName)
    }

    @Test
    fun `displayName strips checked checkbox prefix`() {
        val alarm = Alarm(lineContent = "☑ Done item")
        assertEquals("Done item", alarm.displayName)
    }

    @Test
    fun `displayName strips trailing alarm symbol`() {
        val alarm = Alarm(lineContent = "Meeting ⏰")
        assertEquals("Meeting", alarm.displayName)
    }

    @Test
    fun `displayName strips both prefix and alarm symbol`() {
        val alarm = Alarm(lineContent = "\t• Important task ⏰")
        assertEquals("Important task", alarm.displayName)
    }

    @Test
    fun `displayName handles empty lineContent`() {
        val alarm = Alarm(lineContent = "")
        assertEquals("", alarm.displayName)
    }

    @Test
    fun `displayName handles alarm symbol only`() {
        val alarm = Alarm(lineContent = "⏰")
        assertEquals("", alarm.displayName)
    }

    @Test
    fun `displayName handles tab bullet and alarm symbol`() {
        val alarm = Alarm(lineContent = "\t☐ ⏰")
        assertEquals("", alarm.displayName)
    }

    // endregion
}
