package org.alkaline.taskbrain.dsl.directives

import com.google.firebase.Timestamp
import org.alkaline.taskbrain.dsl.runtime.values.ScheduleFrequency
import org.junit.Assert.*
import org.junit.Test
import java.util.Date

/**
 * Tests for Schedule data model.
 */
class ScheduleTest {

    @Test
    fun `default schedule has expected values`() {
        val schedule = Schedule()

        assertEquals("", schedule.id)
        assertEquals("", schedule.userId)
        assertEquals("", schedule.noteId)
        assertEquals("", schedule.notePath)
        assertEquals("", schedule.directiveHash)
        assertEquals("", schedule.directiveSource)
        assertEquals(ScheduleFrequency.DAILY, schedule.frequency)
        assertNull(schedule.atTime)
        assertNull(schedule.nextExecution)
        assertEquals(ScheduleStatus.ACTIVE, schedule.status)
        assertNull(schedule.lastExecution)
        assertNull(schedule.lastError)
        assertEquals(0, schedule.failureCount)
    }

    @Test
    fun `schedule with all fields set`() {
        val now = Timestamp.now()
        val schedule = Schedule(
            id = "sched-123",
            userId = "user-456",
            noteId = "note-789",
            notePath = "projects/2026",
            directiveHash = "abc123",
            directiveSource = "[schedule(daily, [new(path: date)])]",
            frequency = ScheduleFrequency.DAILY,
            atTime = "09:00",
            nextExecution = now,
            status = ScheduleStatus.ACTIVE,
            lastExecution = now,
            lastError = null,
            failureCount = 0,
            createdAt = now,
            updatedAt = now
        )

        assertEquals("sched-123", schedule.id)
        assertEquals("user-456", schedule.userId)
        assertEquals("note-789", schedule.noteId)
        assertEquals("projects/2026", schedule.notePath)
        assertEquals("abc123", schedule.directiveHash)
        assertEquals("[schedule(daily, [new(path: date)])]", schedule.directiveSource)
        assertEquals(ScheduleFrequency.DAILY, schedule.frequency)
        assertEquals("09:00", schedule.atTime)
        assertEquals(now, schedule.nextExecution)
        assertEquals(ScheduleStatus.ACTIVE, schedule.status)
        assertEquals(now, schedule.lastExecution)
        assertNull(schedule.lastError)
        assertEquals(0, schedule.failureCount)
    }

    // region displayDescription

    @Test
    fun `displayDescription for daily schedule`() {
        val schedule = Schedule(frequency = ScheduleFrequency.DAILY)
        assertEquals("daily", schedule.displayDescription)
    }

    @Test
    fun `displayDescription for hourly schedule`() {
        val schedule = Schedule(frequency = ScheduleFrequency.HOURLY)
        assertEquals("hourly", schedule.displayDescription)
    }

    @Test
    fun `displayDescription for weekly schedule`() {
        val schedule = Schedule(frequency = ScheduleFrequency.WEEKLY)
        assertEquals("weekly", schedule.displayDescription)
    }

    @Test
    fun `displayDescription with atTime`() {
        val schedule = Schedule(frequency = ScheduleFrequency.DAILY, atTime = "09:00")
        assertEquals("daily at 09:00", schedule.displayDescription)
    }

    @Test
    fun `displayDescription for paused schedule`() {
        val schedule = Schedule(
            frequency = ScheduleFrequency.DAILY,
            status = ScheduleStatus.PAUSED
        )
        assertEquals("daily (paused)", schedule.displayDescription)
    }

    @Test
    fun `displayDescription for failed schedule with atTime`() {
        val schedule = Schedule(
            frequency = ScheduleFrequency.DAILY,
            atTime = "14:30",
            status = ScheduleStatus.FAILED
        )
        assertEquals("daily at 14:30 (failed)", schedule.displayDescription)
    }

    // endregion

    // region isActive

    @Test
    fun `isActive returns true for ACTIVE status`() {
        val schedule = Schedule(status = ScheduleStatus.ACTIVE)
        assertTrue(schedule.isActive)
    }

    @Test
    fun `isActive returns false for PAUSED status`() {
        val schedule = Schedule(status = ScheduleStatus.PAUSED)
        assertFalse(schedule.isActive)
    }

    @Test
    fun `isActive returns false for FAILED status`() {
        val schedule = Schedule(status = ScheduleStatus.FAILED)
        assertFalse(schedule.isActive)
    }

    @Test
    fun `isActive returns false for CANCELLED status`() {
        val schedule = Schedule(status = ScheduleStatus.CANCELLED)
        assertFalse(schedule.isActive)
    }

    // endregion

    // region shouldPause

    @Test
    fun `shouldPause returns false when failures below threshold`() {
        val schedule = Schedule(failureCount = 2)
        assertFalse(schedule.shouldPause)
    }

    @Test
    fun `shouldPause returns true when failures at threshold`() {
        val schedule = Schedule(failureCount = Schedule.MAX_FAILURES)
        assertTrue(schedule.shouldPause)
    }

    @Test
    fun `shouldPause returns true when failures above threshold`() {
        val schedule = Schedule(failureCount = Schedule.MAX_FAILURES + 1)
        assertTrue(schedule.shouldPause)
    }

    // endregion

    // region ScheduleStatus

    @Test
    fun `ScheduleStatus has expected values`() {
        val statuses = ScheduleStatus.entries
        assertEquals(4, statuses.size)
        assertTrue(statuses.contains(ScheduleStatus.ACTIVE))
        assertTrue(statuses.contains(ScheduleStatus.PAUSED))
        assertTrue(statuses.contains(ScheduleStatus.FAILED))
        assertTrue(statuses.contains(ScheduleStatus.CANCELLED))
    }

    // endregion

    // region ScheduleFrequency

    @Test
    fun `ScheduleFrequency identifiers`() {
        assertEquals("daily", ScheduleFrequency.DAILY.identifier)
        assertEquals("hourly", ScheduleFrequency.HOURLY.identifier)
        assertEquals("weekly", ScheduleFrequency.WEEKLY.identifier)
    }

    @Test
    fun `ScheduleFrequency fromIdentifier`() {
        assertEquals(ScheduleFrequency.DAILY, ScheduleFrequency.fromIdentifier("daily"))
        assertEquals(ScheduleFrequency.HOURLY, ScheduleFrequency.fromIdentifier("hourly"))
        assertEquals(ScheduleFrequency.WEEKLY, ScheduleFrequency.fromIdentifier("weekly"))
        assertNull(ScheduleFrequency.fromIdentifier("invalid"))
    }

    // endregion

    // region Companion constants

    @Test
    fun `MAX_FAILURES is 3`() {
        assertEquals(3, Schedule.MAX_FAILURES)
    }

    // endregion

    // region precise scheduling

    @Test
    fun `default schedule is not precise`() {
        val schedule = Schedule()
        assertFalse(schedule.precise)
    }

    @Test
    fun `schedule with precise true`() {
        val schedule = Schedule(precise = true)
        assertTrue(schedule.precise)
    }

    @Test
    fun `displayDescription includes clock emoji for precise schedule`() {
        val schedule = Schedule(
            frequency = ScheduleFrequency.DAILY,
            atTime = "09:00",
            precise = true
        )
        assertTrue(schedule.displayDescription.contains("⏰"))
    }

    @Test
    fun `displayDescription does not include clock emoji for non-precise schedule`() {
        val schedule = Schedule(
            frequency = ScheduleFrequency.DAILY,
            atTime = "09:00",
            precise = false
        )
        assertFalse(schedule.displayDescription.contains("⏰"))
    }

    // endregion
}
