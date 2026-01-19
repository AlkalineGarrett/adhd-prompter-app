package org.alkaline.taskbrain.service

import com.google.firebase.Timestamp
import org.alkaline.taskbrain.data.Alarm
import org.alkaline.taskbrain.data.AlarmStatus
import org.alkaline.taskbrain.data.AlarmType
import org.junit.Assert.*
import org.junit.Test
import java.util.Date

class AlarmUtilsTest {

    // region generateRequestCode tests

    @Test
    fun `generateRequestCode returns different codes for different alarm types`() {
        val alarmId = "test_alarm"

        val notifyCode = AlarmUtils.generateRequestCode(alarmId, AlarmType.NOTIFY)
        val urgentCode = AlarmUtils.generateRequestCode(alarmId, AlarmType.URGENT)
        val alarmCode = AlarmUtils.generateRequestCode(alarmId, AlarmType.ALARM)

        assertNotEquals(notifyCode, urgentCode)
        assertNotEquals(urgentCode, alarmCode)
        assertNotEquals(notifyCode, alarmCode)
    }

    @Test
    fun `generateRequestCode returns same code for same inputs`() {
        val alarmId = "test_alarm"

        val code1 = AlarmUtils.generateRequestCode(alarmId, AlarmType.NOTIFY)
        val code2 = AlarmUtils.generateRequestCode(alarmId, AlarmType.NOTIFY)

        assertEquals(code1, code2)
    }

    @Test
    fun `generateRequestCode returns different codes for different alarm IDs`() {
        val code1 = AlarmUtils.generateRequestCode("alarm_1", AlarmType.NOTIFY)
        val code2 = AlarmUtils.generateRequestCode("alarm_2", AlarmType.NOTIFY)

        assertNotEquals(code1, code2)
    }

    // endregion

    // region determineAlarmTypeForSnooze tests

    @Test
    fun `determineAlarmTypeForSnooze returns ALARM when alarmTime is set`() {
        val alarm = Alarm(
            alarmTime = Timestamp(Date()),
            urgentTime = Timestamp(Date()),
            notifyTime = Timestamp(Date())
        )

        val result = AlarmUtils.determineAlarmTypeForSnooze(alarm)

        assertEquals(AlarmType.ALARM, result)
    }

    @Test
    fun `determineAlarmTypeForSnooze returns URGENT when only urgentTime and notifyTime set`() {
        val alarm = Alarm(
            alarmTime = null,
            urgentTime = Timestamp(Date()),
            notifyTime = Timestamp(Date())
        )

        val result = AlarmUtils.determineAlarmTypeForSnooze(alarm)

        assertEquals(AlarmType.URGENT, result)
    }

    @Test
    fun `determineAlarmTypeForSnooze returns NOTIFY when only notifyTime set`() {
        val alarm = Alarm(
            alarmTime = null,
            urgentTime = null,
            notifyTime = Timestamp(Date())
        )

        val result = AlarmUtils.determineAlarmTypeForSnooze(alarm)

        assertEquals(AlarmType.NOTIFY, result)
    }

    @Test
    fun `determineAlarmTypeForSnooze returns NOTIFY when no times set`() {
        val alarm = Alarm(
            alarmTime = null,
            urgentTime = null,
            notifyTime = null
        )

        val result = AlarmUtils.determineAlarmTypeForSnooze(alarm)

        assertEquals(AlarmType.NOTIFY, result)
    }

    // endregion

    // region getTriggersToSchedule tests

    @Test
    fun `getTriggersToSchedule returns empty list when no times set`() {
        val alarm = Alarm()

        val triggers = AlarmUtils.getTriggersToSchedule(alarm)

        assertTrue(triggers.isEmpty())
    }

    @Test
    fun `getTriggersToSchedule returns single trigger when only notifyTime set`() {
        val notifyTime = Timestamp(Date(1000L))
        val alarm = Alarm(notifyTime = notifyTime)

        val triggers = AlarmUtils.getTriggersToSchedule(alarm)

        assertEquals(1, triggers.size)
        assertEquals(1000L to AlarmType.NOTIFY, triggers[0])
    }

    @Test
    fun `getTriggersToSchedule returns all triggers when all times set`() {
        val notifyTime = Timestamp(Date(1000L))
        val urgentTime = Timestamp(Date(2000L))
        val alarmTime = Timestamp(Date(3000L))
        val alarm = Alarm(
            notifyTime = notifyTime,
            urgentTime = urgentTime,
            alarmTime = alarmTime
        )

        val triggers = AlarmUtils.getTriggersToSchedule(alarm)

        assertEquals(3, triggers.size)
        assertTrue(triggers.contains(1000L to AlarmType.NOTIFY))
        assertTrue(triggers.contains(2000L to AlarmType.URGENT))
        assertTrue(triggers.contains(3000L to AlarmType.ALARM))
    }

    // endregion

    // region filterFutureTriggers tests

    @Test
    fun `filterFutureTriggers removes past triggers`() {
        val currentTime = 5000L
        val triggers = listOf(
            1000L to AlarmType.NOTIFY,  // Past
            3000L to AlarmType.URGENT,  // Past
            8000L to AlarmType.ALARM    // Future
        )

        val filtered = AlarmUtils.filterFutureTriggers(triggers, currentTime)

        assertEquals(1, filtered.size)
        assertEquals(8000L to AlarmType.ALARM, filtered[0])
    }

    @Test
    fun `filterFutureTriggers removes trigger at exactly current time`() {
        val currentTime = 5000L
        val triggers = listOf(
            5000L to AlarmType.NOTIFY  // Exactly current time
        )

        val filtered = AlarmUtils.filterFutureTriggers(triggers, currentTime)

        assertTrue(filtered.isEmpty())
    }

    @Test
    fun `filterFutureTriggers keeps all future triggers`() {
        val currentTime = 1000L
        val triggers = listOf(
            2000L to AlarmType.NOTIFY,
            3000L to AlarmType.URGENT,
            4000L to AlarmType.ALARM
        )

        val filtered = AlarmUtils.filterFutureTriggers(triggers, currentTime)

        assertEquals(3, filtered.size)
    }

    // endregion

    // region calculateSnoozeEndTime tests

    @Test
    fun `calculateSnoozeEndTime adds correct milliseconds for 2 minutes`() {
        val currentTime = 1000L

        val snoozeEnd = AlarmUtils.calculateSnoozeEndTime(2, currentTime)

        assertEquals(1000L + 2 * 60 * 1000L, snoozeEnd)
    }

    @Test
    fun `calculateSnoozeEndTime adds correct milliseconds for 10 minutes`() {
        val currentTime = 1000L

        val snoozeEnd = AlarmUtils.calculateSnoozeEndTime(10, currentTime)

        assertEquals(1000L + 10 * 60 * 1000L, snoozeEnd)
    }

    @Test
    fun `calculateSnoozeEndTime adds correct milliseconds for 60 minutes`() {
        val currentTime = 1000L

        val snoozeEnd = AlarmUtils.calculateSnoozeEndTime(60, currentTime)

        assertEquals(1000L + 60 * 60 * 1000L, snoozeEnd)
    }

    // endregion

    // region shouldShowAlarm tests

    @Test
    fun `shouldShowAlarm returns true for pending alarm without snooze`() {
        val alarm = Alarm(status = AlarmStatus.PENDING)

        val result = AlarmUtils.shouldShowAlarm(alarm, currentTimeMillis = 1000L)

        assertTrue(result)
    }

    @Test
    fun `shouldShowAlarm returns false for done alarm`() {
        val alarm = Alarm(status = AlarmStatus.DONE)

        val result = AlarmUtils.shouldShowAlarm(alarm, currentTimeMillis = 1000L)

        assertFalse(result)
    }

    @Test
    fun `shouldShowAlarm returns false for cancelled alarm`() {
        val alarm = Alarm(status = AlarmStatus.CANCELLED)

        val result = AlarmUtils.shouldShowAlarm(alarm, currentTimeMillis = 1000L)

        assertFalse(result)
    }

    @Test
    fun `shouldShowAlarm returns false for snoozed alarm before snooze expires`() {
        val snoozeUntil = Timestamp(Date(5000L))
        val alarm = Alarm(
            status = AlarmStatus.PENDING,
            snoozedUntil = snoozeUntil
        )

        val result = AlarmUtils.shouldShowAlarm(alarm, currentTimeMillis = 3000L)

        assertFalse(result)
    }

    @Test
    fun `shouldShowAlarm returns true for snoozed alarm after snooze expires`() {
        val snoozeUntil = Timestamp(Date(5000L))
        val alarm = Alarm(
            status = AlarmStatus.PENDING,
            snoozedUntil = snoozeUntil
        )

        val result = AlarmUtils.shouldShowAlarm(alarm, currentTimeMillis = 6000L)

        assertTrue(result)
    }

    @Test
    fun `shouldShowAlarm returns true for snoozed alarm at exact snooze time`() {
        val snoozeUntil = Timestamp(Date(5000L))
        val alarm = Alarm(
            status = AlarmStatus.PENDING,
            snoozedUntil = snoozeUntil
        )

        // At exactly snooze time, snooze has expired (not > currentTime)
        val result = AlarmUtils.shouldShowAlarm(alarm, currentTimeMillis = 5000L)

        assertTrue(result)
    }

    // endregion

    // region calculateEffectiveNotifyTime tests

    @Test
    fun `calculateEffectiveNotifyTime returns notifyTime when set`() {
        val notifyTime = Timestamp(Date(1000000L))
        val alarm = Alarm(
            notifyTime = notifyTime,
            alarmTime = Timestamp(Date(2000000L))
        )

        val result = AlarmUtils.calculateEffectiveNotifyTime(alarm)

        assertEquals(1000000L, result)
    }

    @Test
    fun `calculateEffectiveNotifyTime returns 3 hours before alarmTime when notifyTime not set`() {
        val alarmTimeMillis = 10000000L
        val alarm = Alarm(
            notifyTime = null,
            alarmTime = Timestamp(Date(alarmTimeMillis))
        )

        val result = AlarmUtils.calculateEffectiveNotifyTime(alarm)

        val expectedTime = alarmTimeMillis - (3 * 60 * 60 * 1000L)
        assertEquals(expectedTime, result)
    }

    @Test
    fun `calculateEffectiveNotifyTime returns null when neither notifyTime nor alarmTime set`() {
        val alarm = Alarm(
            notifyTime = null,
            alarmTime = null,
            urgentTime = Timestamp(Date(1000000L)) // urgentTime alone shouldn't trigger notify
        )

        val result = AlarmUtils.calculateEffectiveNotifyTime(alarm)

        assertNull(result)
    }

    @Test
    fun `calculateEffectiveNotifyTime prefers notifyTime over calculated time`() {
        val notifyTime = Timestamp(Date(5000000L))
        val alarmTime = Timestamp(Date(10000000L))
        val alarm = Alarm(
            notifyTime = notifyTime,
            alarmTime = alarmTime
        )

        val result = AlarmUtils.calculateEffectiveNotifyTime(alarm)

        // Should use explicit notifyTime, not 3 hours before alarm
        assertEquals(5000000L, result)
    }

    @Test
    fun `calculateEffectiveNotifyTime handles alarmTime only scenario`() {
        // Alarm at 12:00, should notify at 09:00
        val noonMillis = 12 * 60 * 60 * 1000L // 12 hours in millis
        val alarm = Alarm(
            alarmTime = Timestamp(Date(noonMillis))
        )

        val result = AlarmUtils.calculateEffectiveNotifyTime(alarm)

        val expected = 9 * 60 * 60 * 1000L // 9 hours in millis
        assertEquals(expected, result)
    }

    // endregion

    // region calculateEffectiveUrgentTime tests

    @Test
    fun `calculateEffectiveUrgentTime returns urgentTime when set`() {
        val urgentTime = Timestamp(Date(1000000L))
        val alarm = Alarm(
            urgentTime = urgentTime,
            alarmTime = Timestamp(Date(2000000L))
        )

        val result = AlarmUtils.calculateEffectiveUrgentTime(alarm)

        assertEquals(1000000L, result)
    }

    @Test
    fun `calculateEffectiveUrgentTime returns 30 minutes before alarmTime when urgentTime not set`() {
        val alarmTimeMillis = 10000000L
        val alarm = Alarm(
            urgentTime = null,
            alarmTime = Timestamp(Date(alarmTimeMillis))
        )

        val result = AlarmUtils.calculateEffectiveUrgentTime(alarm)

        val expectedTime = alarmTimeMillis - (30 * 60 * 1000L)
        assertEquals(expectedTime, result)
    }

    @Test
    fun `calculateEffectiveUrgentTime returns null when neither urgentTime nor alarmTime set`() {
        val alarm = Alarm(
            urgentTime = null,
            alarmTime = null,
            notifyTime = Timestamp(Date(1000000L)) // notifyTime alone shouldn't trigger urgent
        )

        val result = AlarmUtils.calculateEffectiveUrgentTime(alarm)

        assertNull(result)
    }

    @Test
    fun `calculateEffectiveUrgentTime prefers urgentTime over calculated time`() {
        val urgentTime = Timestamp(Date(5000000L))
        val alarmTime = Timestamp(Date(10000000L))
        val alarm = Alarm(
            urgentTime = urgentTime,
            alarmTime = alarmTime
        )

        val result = AlarmUtils.calculateEffectiveUrgentTime(alarm)

        // Should use explicit urgentTime, not 30 minutes before alarm
        assertEquals(5000000L, result)
    }

    @Test
    fun `calculateEffectiveUrgentTime handles alarmTime only scenario`() {
        // Alarm at 60 minutes, should urgent at 30 minutes
        val sixtyMinutesMillis = 60 * 60 * 1000L
        val alarm = Alarm(
            alarmTime = Timestamp(Date(sixtyMinutesMillis))
        )

        val result = AlarmUtils.calculateEffectiveUrgentTime(alarm)

        val expected = 30 * 60 * 1000L // 30 minutes in millis
        assertEquals(expected, result)
    }

    // endregion

    // region isNotifyTimeInPast tests

    @Test
    fun `isNotifyTimeInPast returns true when time is before current time`() {
        val result = AlarmUtils.isNotifyTimeInPast(
            effectiveNotifyTime = 1000L,
            currentTimeMillis = 2000L
        )

        assertTrue(result)
    }

    @Test
    fun `isNotifyTimeInPast returns true when time equals current time`() {
        val result = AlarmUtils.isNotifyTimeInPast(
            effectiveNotifyTime = 1000L,
            currentTimeMillis = 1000L
        )

        assertTrue(result)
    }

    @Test
    fun `isNotifyTimeInPast returns false when time is after current time`() {
        val result = AlarmUtils.isNotifyTimeInPast(
            effectiveNotifyTime = 2000L,
            currentTimeMillis = 1000L
        )

        assertFalse(result)
    }

    // endregion

    // region getNotificationId tests

    @Test
    fun `getNotificationId returns consistent value for same alarm ID`() {
        val alarmId = "test_alarm_123"

        val id1 = AlarmUtils.getNotificationId(alarmId)
        val id2 = AlarmUtils.getNotificationId(alarmId)

        assertEquals(id1, id2)
    }

    @Test
    fun `getNotificationId returns different values for different alarm IDs`() {
        val id1 = AlarmUtils.getNotificationId("alarm_1")
        val id2 = AlarmUtils.getNotificationId("alarm_2")

        assertNotEquals(id1, id2)
    }

    @Test
    fun `getNotificationId handles empty string`() {
        val id = AlarmUtils.getNotificationId("")

        // Should not throw, and should return a consistent value
        assertEquals("".hashCode(), id)
    }

    @Test
    fun `getNotificationId handles special characters in alarm ID`() {
        val alarmId = "alarm-with_special.chars:123"

        val id1 = AlarmUtils.getNotificationId(alarmId)
        val id2 = AlarmUtils.getNotificationId(alarmId)

        assertEquals(id1, id2)
    }

    @Test
    fun `getNotificationId uses hashCode for consistency with notification system`() {
        val alarmId = "test_alarm"

        val notificationId = AlarmUtils.getNotificationId(alarmId)

        // Verify it uses hashCode (this is important for consistency when showing/dismissing)
        assertEquals(alarmId.hashCode(), notificationId)
    }

    @Test
    fun `getNotificationId is deterministic across multiple calls`() {
        // This is critical for notification dismiss to work correctly
        // The same ID must be used when showing AND dismissing a notification
        val alarmId = "firebase_generated_id_abc123"

        // Simulate showing notification
        val showNotificationId = AlarmUtils.getNotificationId(alarmId)

        // Simulate dismissing notification (from different code path)
        val dismissNotificationId = AlarmUtils.getNotificationId(alarmId)

        assertEquals(showNotificationId, dismissNotificationId)
    }

    // endregion
}
