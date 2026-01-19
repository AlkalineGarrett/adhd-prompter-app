package org.alkaline.taskbrain.service

import org.alkaline.taskbrain.data.AlarmType
import org.junit.Assert.*
import org.junit.Test

class AlarmSchedulerTest {

    // region AlarmScheduleResult Tests

    @Test
    fun `AlarmScheduleResult success is true when triggers are scheduled`() {
        val result = AlarmScheduleResult(
            alarmId = "test_id",
            scheduledTriggers = listOf(AlarmType.ALARM),
            skippedPastTriggers = emptyList(),
            noTriggersConfigured = false,
            usedExactAlarm = true
        )

        assertTrue(result.success)
    }

    @Test
    fun `AlarmScheduleResult success is false when no triggers scheduled`() {
        val result = AlarmScheduleResult(
            alarmId = "test_id",
            scheduledTriggers = emptyList(),
            skippedPastTriggers = listOf(AlarmType.ALARM),
            noTriggersConfigured = false,
            usedExactAlarm = false
        )

        assertFalse(result.success)
    }

    @Test
    fun `AlarmScheduleResult success is false when no triggers configured`() {
        val result = AlarmScheduleResult(
            alarmId = "test_id",
            scheduledTriggers = emptyList(),
            skippedPastTriggers = emptyList(),
            noTriggersConfigured = true,
            usedExactAlarm = false
        )

        assertFalse(result.success)
    }

    @Test
    fun `AlarmScheduleResult message shows no triggers configured`() {
        val result = AlarmScheduleResult(
            alarmId = "test_id",
            scheduledTriggers = emptyList(),
            skippedPastTriggers = emptyList(),
            noTriggersConfigured = true,
            usedExactAlarm = false
        )

        assertEquals("No alarm times were configured", result.message)
    }

    @Test
    fun `AlarmScheduleResult message shows past triggers when all skipped`() {
        val result = AlarmScheduleResult(
            alarmId = "test_id",
            scheduledTriggers = emptyList(),
            skippedPastTriggers = listOf(AlarmType.NOTIFY, AlarmType.ALARM),
            noTriggersConfigured = false,
            usedExactAlarm = false
        )

        assertEquals("All alarm times are in the past: NOTIFY, ALARM", result.message)
    }

    @Test
    fun `AlarmScheduleResult message shows scheduled count on success`() {
        val result = AlarmScheduleResult(
            alarmId = "test_id",
            scheduledTriggers = listOf(AlarmType.NOTIFY, AlarmType.URGENT, AlarmType.ALARM),
            skippedPastTriggers = emptyList(),
            noTriggersConfigured = false,
            usedExactAlarm = true
        )

        assertEquals("Scheduled 3 trigger(s): NOTIFY, URGENT, ALARM", result.message)
    }

    @Test
    fun `AlarmScheduleResult message shows single trigger on success`() {
        val result = AlarmScheduleResult(
            alarmId = "test_id",
            scheduledTriggers = listOf(AlarmType.ALARM),
            skippedPastTriggers = emptyList(),
            noTriggersConfigured = false,
            usedExactAlarm = true
        )

        assertEquals("Scheduled 1 trigger(s): ALARM", result.message)
    }

    @Test
    fun `AlarmScheduleResult partial success with some skipped`() {
        val result = AlarmScheduleResult(
            alarmId = "test_id",
            scheduledTriggers = listOf(AlarmType.ALARM),
            skippedPastTriggers = listOf(AlarmType.NOTIFY),
            noTriggersConfigured = false,
            usedExactAlarm = true
        )

        assertTrue(result.success)
        assertEquals("Scheduled 1 trigger(s): ALARM", result.message)
        assertEquals(1, result.skippedPastTriggers.size)
    }

    @Test
    fun `AlarmScheduleResult tracks exact alarm usage`() {
        val exactResult = AlarmScheduleResult(
            alarmId = "test_id",
            scheduledTriggers = listOf(AlarmType.ALARM),
            skippedPastTriggers = emptyList(),
            noTriggersConfigured = false,
            usedExactAlarm = true
        )

        val inexactResult = AlarmScheduleResult(
            alarmId = "test_id",
            scheduledTriggers = listOf(AlarmType.ALARM),
            skippedPastTriggers = emptyList(),
            noTriggersConfigured = false,
            usedExactAlarm = false
        )

        assertTrue(exactResult.usedExactAlarm)
        assertFalse(inexactResult.usedExactAlarm)
    }

    @Test
    fun `AlarmScheduleResult message for empty scheduled and no past triggers`() {
        val result = AlarmScheduleResult(
            alarmId = "test_id",
            scheduledTriggers = emptyList(),
            skippedPastTriggers = emptyList(),
            noTriggersConfigured = false,
            usedExactAlarm = false
        )

        assertEquals("No triggers could be scheduled", result.message)
    }

    // endregion
}
