package org.alkaline.taskbrain.service

import com.google.firebase.Timestamp
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.alkaline.taskbrain.data.Alarm
import org.alkaline.taskbrain.data.AlarmRepository
import org.alkaline.taskbrain.data.RecurrenceType
import org.alkaline.taskbrain.data.RecurringAlarmRepository
import org.alkaline.taskbrain.data.TimeOfDay
import org.alkaline.taskbrain.service.RecurrenceTestHelpers.HOUR_MS
import org.alkaline.taskbrain.service.RecurrenceTestHelpers.day1_9am
import org.alkaline.taskbrain.service.RecurrenceTestHelpers.day2_9am
import org.alkaline.taskbrain.service.RecurrenceTestHelpers.successScheduleResult
import org.alkaline.taskbrain.service.RecurrenceTestHelpers.threeStageConfig
import org.alkaline.taskbrain.service.RecurrenceTestHelpers.ts
import org.alkaline.taskbrain.service.RecurrenceTestHelpers.alarm
import org.alkaline.taskbrain.service.RecurrenceTestHelpers.dailyRecurring
import org.alkaline.taskbrain.ui.currentnote.components.RecurrenceConfig
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class RecurrenceTemplateManagerTest {

    private lateinit var mockRecurringRepo: RecurringAlarmRepository
    private lateinit var mockAlarmRepo: AlarmRepository
    private lateinit var mockAlarmStateManager: AlarmStateManager
    private lateinit var manager: RecurrenceTemplateManager

    @Before
    fun setUp() {
        mockRecurringRepo = mockk(relaxed = true)
        mockAlarmRepo = mockk(relaxed = true)
        mockAlarmStateManager = mockk(relaxed = true)
        manager = RecurrenceTemplateManager(mockRecurringRepo, mockAlarmRepo, mockAlarmStateManager)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    // region updateInstanceTimes

    @Test
    fun `updateInstanceTimes updates instance only when alsoUpdateRecurrence is false`() = runTest {
        val instance = alarm("a1")
        val newDueTime = ts(day1_9am + HOUR_MS)
        coEvery { mockAlarmStateManager.update(any(), any(), any()) } returns
                Result.success(successScheduleResult)

        manager.updateInstanceTimes(instance, newDueTime, threeStageConfig, alsoUpdateRecurrence = false)

        coVerify(exactly = 1) { mockAlarmStateManager.update(instance, newDueTime, threeStageConfig) }
        coVerify(exactly = 0) { mockRecurringRepo.updateTimes(any(), any(), any()) }
    }

    @Test
    fun `updateInstanceTimes updates instance and template when alsoUpdateRecurrence is true`() = runTest {
        val instance = alarm("a1")
        val newDueTime = ts(day1_9am + HOUR_MS)
        coEvery { mockAlarmStateManager.update(any(), any(), any()) } returns
                Result.success(successScheduleResult)
        coEvery { mockRecurringRepo.updateTimes(any(), any(), any()) } returns Result.success(Unit)

        manager.updateInstanceTimes(instance, newDueTime, threeStageConfig, alsoUpdateRecurrence = true)

        coVerify { mockAlarmStateManager.update(instance, newDueTime, threeStageConfig) }
        coVerify { mockRecurringRepo.updateTimes("rec1", TimeOfDay(10, 0), threeStageConfig) }
    }

    @Test
    fun `updateInstanceTimes skips template update when no recurringAlarmId`() = runTest {
        val instance = Alarm(
            id = "a1", noteId = "note1", lineContent = "test",
            dueTime = ts(day1_9am), stages = threeStageConfig, recurringAlarmId = null
        )
        coEvery { mockAlarmStateManager.update(any(), any(), any()) } returns
                Result.success(successScheduleResult)

        manager.updateInstanceTimes(instance, ts(day1_9am + HOUR_MS), threeStageConfig, alsoUpdateRecurrence = true)

        coVerify(exactly = 0) { mockRecurringRepo.updateTimes(any(), any(), any()) }
    }

    // endregion

    // region updateRecurrenceTemplate

    @Test
    fun `updateRecurrenceTemplate fails when template not found`() = runTest {
        coEvery { mockRecurringRepo.get("rec1") } returns Result.success(null)

        val result = manager.updateRecurrenceTemplate(
            "rec1", ts(day1_9am), threeStageConfig,
            RecurrenceConfig(enabled = true, recurrenceType = RecurrenceType.FIXED),
            alsoUpdateMatchingInstances = false
        )

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { mockRecurringRepo.update(any()) }
    }

    @Test
    fun `updateRecurrenceTemplate updates template without propagation`() = runTest {
        val existing = dailyRecurring(currentAlarmId = "a1", anchorTimeOfDay = TimeOfDay(9, 0))
        coEvery { mockRecurringRepo.get("rec1") } returns Result.success(existing)
        coEvery { mockRecurringRepo.update(any()) } returns Result.success(Unit)

        val result = manager.updateRecurrenceTemplate(
            "rec1", ts(day1_9am), threeStageConfig,
            RecurrenceConfig(enabled = true, recurrenceType = RecurrenceType.FIXED),
            alsoUpdateMatchingInstances = false
        )

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { mockRecurringRepo.update(any()) }
        coVerify(exactly = 0) { mockAlarmRepo.getPendingInstancesForRecurring(any()) }
    }

    @Test
    fun `updateRecurrenceTemplate propagates to matching instances`() = runTest {
        val existing = dailyRecurring(currentAlarmId = "a1", anchorTimeOfDay = TimeOfDay(9, 0))
        val matchingInstance = alarm("a1", dueTimeMs = day1_9am)

        coEvery { mockRecurringRepo.get("rec1") } returns Result.success(existing)
        coEvery { mockRecurringRepo.update(any()) } returns Result.success(Unit)
        coEvery { mockRecurringRepo.getMatchingPendingInstances("rec1", TimeOfDay(9, 0), threeStageConfig, mockAlarmRepo) } returns
                listOf(matchingInstance)
        coEvery { mockAlarmStateManager.update(any(), any(), any()) } returns
                Result.success(successScheduleResult)

        // Change time from 9:00 to 14:00
        val newDueTime = ts(day1_9am + 5 * HOUR_MS)
        val newStages = Alarm.DEFAULT_STAGES

        manager.updateRecurrenceTemplate(
            "rec1", newDueTime, newStages,
            RecurrenceConfig(enabled = true, recurrenceType = RecurrenceType.FIXED),
            alsoUpdateMatchingInstances = true
        )

        coVerify(exactly = 1) {
            mockAlarmStateManager.update(
                match { it.id == "a1" },
                any(),
                eq(newStages)
            )
        }
    }

    @Test
    fun `updateRecurrenceTemplate skips propagation when dueTime is null`() = runTest {
        val existing = dailyRecurring(currentAlarmId = "a1", anchorTimeOfDay = TimeOfDay(9, 0))
        coEvery { mockRecurringRepo.get("rec1") } returns Result.success(existing)
        coEvery { mockRecurringRepo.update(any()) } returns Result.success(Unit)

        manager.updateRecurrenceTemplate(
            "rec1", null, threeStageConfig,
            RecurrenceConfig(enabled = true, recurrenceType = RecurrenceType.FIXED),
            alsoUpdateMatchingInstances = true
        )

        coVerify(exactly = 0) { mockAlarmRepo.getPendingInstancesForRecurring(any()) }
    }

    // endregion

    // region mergeTemplate

    @Test
    fun `mergeTemplate preserves identity fields from existing template`() {
        val existing = dailyRecurring(currentAlarmId = "a1", anchorTimeOfDay = TimeOfDay(9, 0)).copy(
            userId = "user1",
            completionCount = 5,
            lastCompletionDate = Timestamp.now(),
            createdAt = Timestamp.now()
        )

        val merged = RecurrenceTemplateManager.mergeTemplate(
            existing, ts(day1_9am), threeStageConfig,
            RecurrenceConfig(enabled = true, recurrenceType = RecurrenceType.FIXED)
        )

        assertEquals(existing.id, merged.id)
        assertEquals(existing.userId, merged.userId)
        assertEquals(existing.completionCount, merged.completionCount)
        assertEquals(existing.lastCompletionDate, merged.lastCompletionDate)
        assertEquals(existing.currentAlarmId, merged.currentAlarmId)
        assertEquals(existing.status, merged.status)
        assertEquals(existing.createdAt, merged.createdAt)
    }

    @Test
    fun `mergeTemplate updates anchorTimeOfDay from new dueTime`() {
        val existing = dailyRecurring(anchorTimeOfDay = TimeOfDay(9, 0))
        val newDueTime = ts(day1_9am + 5 * HOUR_MS) // 14:00

        val merged = RecurrenceTemplateManager.mergeTemplate(
            existing, newDueTime, threeStageConfig,
            RecurrenceConfig(enabled = true, recurrenceType = RecurrenceType.FIXED)
        )

        assertEquals(TimeOfDay(14, 0), merged.anchorTimeOfDay)
    }

    // endregion

    // region propagateTimesToMatchingInstances

    @Test
    fun `propagateTimesToMatchingInstances shifts time-of-day for each matching instance`() = runTest {
        val day1Instance = alarm("a1", dueTimeMs = day1_9am)
        val day2Instance = alarm("a2", dueTimeMs = day2_9am)

        coEvery { mockRecurringRepo.getMatchingPendingInstances("rec1", TimeOfDay(9, 0), threeStageConfig, mockAlarmRepo) } returns
                listOf(day1Instance, day2Instance)
        coEvery { mockAlarmStateManager.update(any(), any(), any()) } returns
                Result.success(successScheduleResult)

        val newDueTime = ts(day1_9am + 5 * HOUR_MS) // 14:00
        manager.propagateTimesToMatchingInstances(
            "rec1", TimeOfDay(9, 0), threeStageConfig, newDueTime, threeStageConfig
        )

        coVerify(exactly = 2) { mockAlarmStateManager.update(any(), any(), eq(threeStageConfig)) }
    }

    @Test
    fun `propagateTimesToMatchingInstances skips instances without dueTime`() = runTest {
        val instanceWithoutDue = Alarm(
            id = "a1", noteId = "note1", lineContent = "test",
            dueTime = null, stages = threeStageConfig, recurringAlarmId = "rec1"
        )

        coEvery { mockRecurringRepo.getMatchingPendingInstances("rec1", null, threeStageConfig, mockAlarmRepo) } returns
                listOf(instanceWithoutDue)

        manager.propagateTimesToMatchingInstances(
            "rec1", null, threeStageConfig, ts(day1_9am), threeStageConfig
        )

        coVerify(exactly = 0) { mockAlarmStateManager.update(any(), any(), any()) }
    }

    // endregion
}
