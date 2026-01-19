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
}
