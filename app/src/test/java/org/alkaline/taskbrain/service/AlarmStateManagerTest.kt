package org.alkaline.taskbrain.service

import android.app.NotificationManager
import com.google.firebase.Timestamp
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.alkaline.taskbrain.data.Alarm
import org.alkaline.taskbrain.data.AlarmRepository
import org.alkaline.taskbrain.data.AlarmStage
import org.alkaline.taskbrain.data.AlarmStageType
import org.alkaline.taskbrain.data.AlarmType
import org.alkaline.taskbrain.data.SnoozeDuration
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.Date

class AlarmStateManagerTest {

    private lateinit var mockRepository: AlarmRepository
    private lateinit var mockScheduler: AlarmScheduler
    private lateinit var mockUrgentStateManager: UrgentStateManager
    private lateinit var mockNotificationManager: NotificationManager
    private lateinit var stateManager: AlarmStateManager

    @Before
    fun setUp() {
        mockRepository = mockk(relaxed = true)
        mockScheduler = mockk(relaxed = true)
        mockUrgentStateManager = mockk(relaxed = true)
        mockNotificationManager = mockk(relaxed = true)

        stateManager = AlarmStateManager(
            repository = mockRepository,
            scheduler = mockScheduler,
            urgentStateManager = mockUrgentStateManager,
            notificationManager = mockNotificationManager
        )
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun createTestAlarm(
        id: String = "test_alarm",
        dueTime: Timestamp? = Timestamp(Date(System.currentTimeMillis() + 3600000)),
        stages: List<AlarmStage> = Alarm.DEFAULT_STAGES
    ) = Alarm(
        id = id,
        noteId = "note1",
        lineContent = "Test alarm",
        dueTime = dueTime,
        stages = stages
    )

    // region deactivate

    @Test
    fun `deactivate cancels scheduled triggers`() {
        stateManager.deactivate("alarm1")

        verify { mockScheduler.cancelAlarm("alarm1") }
    }

    @Test
    fun `deactivate exits urgent state`() {
        stateManager.deactivate("alarm1")

        verify { mockUrgentStateManager.exitUrgentState("alarm1") }
    }

    @Test
    fun `deactivate dismisses notification`() {
        val notificationId = AlarmUtils.getNotificationId("alarm1")

        stateManager.deactivate("alarm1")

        verify { mockNotificationManager.cancel(notificationId) }
    }

    @Test
    fun `deactivate handles null notification manager`() {
        val manager = AlarmStateManager(
            repository = mockRepository,
            scheduler = mockScheduler,
            urgentStateManager = mockUrgentStateManager,
            notificationManager = null
        )

        // Should not throw
        manager.deactivate("alarm1")

        verify { mockScheduler.cancelAlarm("alarm1") }
        verify { mockUrgentStateManager.exitUrgentState("alarm1") }
    }

    // endregion

    // region create

    @Test
    fun `create persists alarm and schedules triggers`() = runTest {
        val alarm = createTestAlarm()
        val scheduleResult = AlarmScheduleResult(
            alarmId = "new_id",
            scheduledTriggers = listOf(AlarmType.NOTIFY),
            skippedPastTriggers = emptyList(),
            noTriggersConfigured = false,
            usedExactAlarm = true
        )

        coEvery { mockRepository.createAlarm(alarm) } returns Result.success("new_id")
        coEvery { mockRepository.getAlarm("new_id") } returns Result.success(alarm.copy(id = "new_id"))
        every { mockScheduler.scheduleAlarm(any()) } returns scheduleResult

        val result = stateManager.create(alarm)

        assertTrue(result.isSuccess)
        val (alarmId, returnedScheduleResult) = result.getOrThrow()
        assertEquals("new_id", alarmId)
        assertEquals(scheduleResult, returnedScheduleResult)
        coVerify { mockRepository.createAlarm(alarm) }
        coVerify { mockRepository.getAlarm("new_id") }
        verify { mockScheduler.scheduleAlarm(any()) }
    }

    @Test
    fun `create returns failure when repository create fails`() = runTest {
        val alarm = createTestAlarm()
        coEvery { mockRepository.createAlarm(alarm) } returns Result.failure(RuntimeException("db error"))

        val result = stateManager.create(alarm)

        assertTrue(result.isFailure)
        verify(exactly = 0) { mockScheduler.scheduleAlarm(any()) }
    }

    @Test
    fun `create falls back to alarm copy when getAlarm returns null`() = runTest {
        val alarm = createTestAlarm()
        val scheduleResult = AlarmScheduleResult(
            alarmId = "new_id",
            scheduledTriggers = emptyList(),
            skippedPastTriggers = emptyList(),
            noTriggersConfigured = false,
            usedExactAlarm = true
        )

        coEvery { mockRepository.createAlarm(alarm) } returns Result.success("new_id")
        coEvery { mockRepository.getAlarm("new_id") } returns Result.success(null)
        every { mockScheduler.scheduleAlarm(any()) } returns scheduleResult

        val result = stateManager.create(alarm)

        assertTrue(result.isSuccess)
        // Should schedule with the alarm copy (id = "new_id")
        verify { mockScheduler.scheduleAlarm(match { it.id == "new_id" }) }
    }

    // endregion

    // region snooze

    @Test
    fun `snooze updates DB, deactivates, and schedules snooze trigger`() = runTest {
        val alarm = createTestAlarm(id = "alarm1")

        coEvery { mockRepository.getAlarm("alarm1") } returns Result.success(alarm)
        coEvery { mockRepository.snoozeAlarm("alarm1", SnoozeDuration.TEN_MINUTES) } returns Result.success(Unit)
        every { mockScheduler.scheduleSnooze(any(), any(), any()) } returns true

        val result = stateManager.snooze("alarm1", SnoozeDuration.TEN_MINUTES)

        assertTrue(result.isSuccess)
        coVerify { mockRepository.snoozeAlarm("alarm1", SnoozeDuration.TEN_MINUTES) }
        verify { mockScheduler.cancelAlarm("alarm1") }
        verify { mockUrgentStateManager.exitUrgentState("alarm1") }
        verify { mockNotificationManager.cancel(any()) }
        verify { mockScheduler.scheduleSnooze(eq("alarm1"), any(), any()) }
    }

    @Test
    fun `snooze fails when alarm not found`() = runTest {
        coEvery { mockRepository.getAlarm("alarm1") } returns Result.success(null)

        val result = stateManager.snooze("alarm1", SnoozeDuration.TEN_MINUTES)

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { mockRepository.snoozeAlarm(any(), any()) }
        verify(exactly = 0) { mockScheduler.cancelAlarm(any()) }
    }

    @Test
    fun `snooze fails when getAlarm fails`() = runTest {
        coEvery { mockRepository.getAlarm("alarm1") } returns Result.failure(RuntimeException("network"))

        val result = stateManager.snooze("alarm1", SnoozeDuration.TEN_MINUTES)

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { mockRepository.snoozeAlarm(any(), any()) }
    }

    @Test
    fun `snooze fails when snoozeAlarm DB write fails`() = runTest {
        val alarm = createTestAlarm(id = "alarm1")

        coEvery { mockRepository.getAlarm("alarm1") } returns Result.success(alarm)
        coEvery { mockRepository.snoozeAlarm("alarm1", SnoozeDuration.TEN_MINUTES) } returns Result.failure(RuntimeException("write fail"))

        val result = stateManager.snooze("alarm1", SnoozeDuration.TEN_MINUTES)

        assertTrue(result.isFailure)
        // Should not deactivate or schedule if DB write failed
        verify(exactly = 0) { mockScheduler.cancelAlarm(any()) }
        verify(exactly = 0) { mockScheduler.scheduleSnooze(any(), any(), any()) }
    }

    // endregion

    // region dismiss

    @Test
    fun `dismiss exits urgent state and dismisses notification`() {
        val notificationId = AlarmUtils.getNotificationId("alarm1")

        stateManager.dismiss("alarm1")

        verify { mockUrgentStateManager.exitUrgentState("alarm1") }
        verify { mockNotificationManager.cancel(notificationId) }
    }

    @Test
    fun `dismiss does not cancel scheduled triggers`() {
        stateManager.dismiss("alarm1")

        verify(exactly = 0) { mockScheduler.cancelAlarm(any()) }
    }

    @Test
    fun `dismiss handles null notification manager`() {
        val manager = AlarmStateManager(
            repository = mockRepository,
            scheduler = mockScheduler,
            urgentStateManager = mockUrgentStateManager,
            notificationManager = null
        )

        manager.dismiss("alarm1")

        verify { mockUrgentStateManager.exitUrgentState("alarm1") }
    }

    // endregion

    // region markDone

    @Test
    fun `markDone deactivates and updates repository`() = runTest {
        coEvery { mockRepository.markDone("alarm1") } returns Result.success(Unit)

        val result = stateManager.markDone("alarm1")

        assertTrue(result.isSuccess)
        verify { mockScheduler.cancelAlarm("alarm1") }
        verify { mockUrgentStateManager.exitUrgentState("alarm1") }
        verify { mockNotificationManager.cancel(any()) }
        coVerify { mockRepository.markDone("alarm1") }
    }

    @Test
    fun `markDone deactivates even when repository fails`() = runTest {
        coEvery { mockRepository.markDone("alarm1") } returns Result.failure(RuntimeException("fail"))

        val result = stateManager.markDone("alarm1")

        assertTrue(result.isFailure)
        // Deactivation still happens (cleanup before repo call)
        verify { mockScheduler.cancelAlarm("alarm1") }
        verify { mockUrgentStateManager.exitUrgentState("alarm1") }
        verify { mockNotificationManager.cancel(any()) }
    }

    // endregion

    // region markCancelled

    @Test
    fun `markCancelled deactivates and updates repository`() = runTest {
        coEvery { mockRepository.markCancelled("alarm1") } returns Result.success(Unit)

        val result = stateManager.markCancelled("alarm1")

        assertTrue(result.isSuccess)
        verify { mockScheduler.cancelAlarm("alarm1") }
        verify { mockUrgentStateManager.exitUrgentState("alarm1") }
        verify { mockNotificationManager.cancel(any()) }
        coVerify { mockRepository.markCancelled("alarm1") }
    }

    @Test
    fun `markCancelled deactivates even when repository fails`() = runTest {
        coEvery { mockRepository.markCancelled("alarm1") } returns Result.failure(RuntimeException("fail"))

        val result = stateManager.markCancelled("alarm1")

        assertTrue(result.isFailure)
        verify { mockScheduler.cancelAlarm("alarm1") }
        verify { mockUrgentStateManager.exitUrgentState("alarm1") }
    }

    // endregion

    // region delete

    @Test
    fun `delete deactivates and removes from repository`() = runTest {
        coEvery { mockRepository.deleteAlarm("alarm1") } returns Result.success(Unit)

        val result = stateManager.delete("alarm1")

        assertTrue(result.isSuccess)
        verify { mockScheduler.cancelAlarm("alarm1") }
        verify { mockUrgentStateManager.exitUrgentState("alarm1") }
        verify { mockNotificationManager.cancel(any()) }
        coVerify { mockRepository.deleteAlarm("alarm1") }
    }

    @Test
    fun `delete deactivates even when repository fails`() = runTest {
        coEvery { mockRepository.deleteAlarm("alarm1") } returns Result.failure(RuntimeException("fail"))

        val result = stateManager.delete("alarm1")

        assertTrue(result.isFailure)
        verify { mockScheduler.cancelAlarm("alarm1") }
        verify { mockUrgentStateManager.exitUrgentState("alarm1") }
    }

    // endregion

    // region update

    @Test
    fun `update deactivates old state and reschedules`() = runTest {
        val alarm = createTestAlarm(id = "alarm1")
        val newDueTime = Timestamp(Date(System.currentTimeMillis() + 7200000))
        val scheduleResult = AlarmScheduleResult(
            alarmId = "alarm1",
            scheduledTriggers = listOf(org.alkaline.taskbrain.data.AlarmType.NOTIFY),
            skippedPastTriggers = emptyList(),
            noTriggersConfigured = false,
            usedExactAlarm = true
        )

        coEvery { mockRepository.updateAlarm(any()) } returns Result.success(Unit)
        every { mockScheduler.scheduleAlarm(any()) } returns scheduleResult

        val result = stateManager.update(alarm, newDueTime, Alarm.DEFAULT_STAGES)

        assertTrue(result.isSuccess)
        // Old state is deactivated
        verify { mockScheduler.cancelAlarm("alarm1") }
        verify { mockUrgentStateManager.exitUrgentState("alarm1") }
        verify { mockNotificationManager.cancel(any()) }
        // New alarm is scheduled
        verify { mockScheduler.scheduleAlarm(any()) }
        coVerify { mockRepository.updateAlarm(any()) }
    }

    @Test
    fun `update does not schedule when repository fails`() = runTest {
        val alarm = createTestAlarm(id = "alarm1")
        coEvery { mockRepository.updateAlarm(any()) } returns Result.failure(RuntimeException("fail"))

        val result = stateManager.update(alarm, null, Alarm.DEFAULT_STAGES)

        assertTrue(result.isFailure)
        // Should not deactivate or schedule since repo failed before that
        verify(exactly = 0) { mockScheduler.cancelAlarm(any()) }
        verify(exactly = 0) { mockScheduler.scheduleAlarm(any()) }
    }

    @Test
    fun `update returns the schedule result from scheduler`() = runTest {
        val alarm = createTestAlarm(id = "alarm1")
        val scheduleResult = AlarmScheduleResult(
            alarmId = "alarm1",
            scheduledTriggers = listOf(org.alkaline.taskbrain.data.AlarmType.ALARM),
            skippedPastTriggers = listOf(org.alkaline.taskbrain.data.AlarmType.URGENT),
            noTriggersConfigured = false,
            usedExactAlarm = true
        )

        coEvery { mockRepository.updateAlarm(any()) } returns Result.success(Unit)
        every { mockScheduler.scheduleAlarm(any()) } returns scheduleResult

        val result = stateManager.update(alarm, null, Alarm.DEFAULT_STAGES)

        assertEquals(scheduleResult, result.getOrNull())
    }

    @Test
    fun `update passes updated alarm with new dueTime and stages to repository`() = runTest {
        val alarm = createTestAlarm(id = "alarm1")
        val dueTime = Timestamp(Date(3000))
        val stages = listOf(
            AlarmStage(AlarmStageType.SOUND_ALARM, offsetMs = 0, enabled = true)
        )
        val scheduleResult = AlarmScheduleResult(
            alarmId = "alarm1",
            scheduledTriggers = emptyList(),
            skippedPastTriggers = emptyList(),
            noTriggersConfigured = false,
            usedExactAlarm = true
        )

        coEvery { mockRepository.updateAlarm(any()) } returns Result.success(Unit)
        every { mockScheduler.scheduleAlarm(any()) } returns scheduleResult

        stateManager.update(alarm, dueTime, stages)

        // Verify repository gets alarm with updated fields
        coVerify {
            mockRepository.updateAlarm(match {
                it.dueTime == dueTime &&
                it.stages == stages
            })
        }

        // Verify scheduler gets the same updated alarm
        verify {
            mockScheduler.scheduleAlarm(match {
                it.dueTime == dueTime &&
                it.stages == stages
            })
        }
    }

    @Test
    fun `update resets notifiedStageType when dueTime changes`() = runTest {
        val alarm = createTestAlarm(id = "alarm1").copy(
            notifiedStageType = AlarmStageType.LOCK_SCREEN
        )
        val newDueTime = Timestamp(Date(System.currentTimeMillis() + 7200000))
        val scheduleResult = AlarmScheduleResult(
            alarmId = "alarm1",
            scheduledTriggers = emptyList(),
            skippedPastTriggers = emptyList(),
            noTriggersConfigured = false,
            usedExactAlarm = true
        )

        coEvery { mockRepository.updateAlarm(any()) } returns Result.success(Unit)
        every { mockScheduler.scheduleAlarm(any()) } returns scheduleResult

        stateManager.update(alarm, newDueTime, alarm.stages)

        coVerify {
            mockRepository.updateAlarm(match { it.notifiedStageType == null })
        }
    }

    @Test
    fun `update resets notifiedStageType when stages change`() = runTest {
        val alarm = createTestAlarm(id = "alarm1").copy(
            notifiedStageType = AlarmStageType.NOTIFICATION
        )
        val newStages = listOf(
            AlarmStage(AlarmStageType.SOUND_ALARM, offsetMs = 0, enabled = true),
            AlarmStage(AlarmStageType.NOTIFICATION, offsetMs = 3600000, enabled = true)
        )
        val scheduleResult = AlarmScheduleResult(
            alarmId = "alarm1",
            scheduledTriggers = emptyList(),
            skippedPastTriggers = emptyList(),
            noTriggersConfigured = false,
            usedExactAlarm = true
        )

        coEvery { mockRepository.updateAlarm(any()) } returns Result.success(Unit)
        every { mockScheduler.scheduleAlarm(any()) } returns scheduleResult

        stateManager.update(alarm, alarm.dueTime, newStages)

        coVerify {
            mockRepository.updateAlarm(match { it.notifiedStageType == null })
        }
    }

    @Test
    fun `update preserves notifiedStageType when timings unchanged`() = runTest {
        val alarm = createTestAlarm(id = "alarm1").copy(
            notifiedStageType = AlarmStageType.LOCK_SCREEN
        )
        val scheduleResult = AlarmScheduleResult(
            alarmId = "alarm1",
            scheduledTriggers = emptyList(),
            skippedPastTriggers = emptyList(),
            noTriggersConfigured = false,
            usedExactAlarm = true
        )

        coEvery { mockRepository.updateAlarm(any()) } returns Result.success(Unit)
        every { mockScheduler.scheduleAlarm(any()) } returns scheduleResult

        stateManager.update(alarm, alarm.dueTime, alarm.stages)

        coVerify {
            mockRepository.updateAlarm(match { it.notifiedStageType == AlarmStageType.LOCK_SCREEN })
        }
    }

    // endregion

    // region reactivate

    @Test
    fun `reactivate updates repository and reschedules`() = runTest {
        val alarm = createTestAlarm(id = "alarm1")
        val scheduleResult = AlarmScheduleResult(
            alarmId = "alarm1",
            scheduledTriggers = listOf(org.alkaline.taskbrain.data.AlarmType.NOTIFY),
            skippedPastTriggers = emptyList(),
            noTriggersConfigured = false,
            usedExactAlarm = true
        )

        coEvery { mockRepository.reactivateAlarm("alarm1") } returns Result.success(Unit)
        coEvery { mockRepository.getAlarm("alarm1") } returns Result.success(alarm)
        every { mockScheduler.scheduleAlarm(alarm) } returns scheduleResult

        val result = stateManager.reactivate("alarm1")

        assertTrue(result.isSuccess)
        assertEquals(scheduleResult, result.getOrNull())
        coVerify { mockRepository.reactivateAlarm("alarm1") }
        coVerify { mockRepository.getAlarm("alarm1") }
        verify { mockScheduler.scheduleAlarm(alarm) }
    }

    @Test
    fun `reactivate returns null schedule result when alarm not found`() = runTest {
        coEvery { mockRepository.reactivateAlarm("alarm1") } returns Result.success(Unit)
        coEvery { mockRepository.getAlarm("alarm1") } returns Result.success(null)

        val result = stateManager.reactivate("alarm1")

        assertTrue(result.isSuccess)
        assertNull(result.getOrNull())
        verify(exactly = 0) { mockScheduler.scheduleAlarm(any()) }
    }

    @Test
    fun `reactivate does not schedule when repository fails`() = runTest {
        coEvery { mockRepository.reactivateAlarm("alarm1") } returns Result.failure(RuntimeException("fail"))

        val result = stateManager.reactivate("alarm1")

        assertTrue(result.isFailure)
        verify(exactly = 0) { mockScheduler.scheduleAlarm(any()) }
    }

    @Test
    fun `reactivate returns success with null when getAlarm fails`() = runTest {
        coEvery { mockRepository.reactivateAlarm("alarm1") } returns Result.success(Unit)
        coEvery { mockRepository.getAlarm("alarm1") } returns Result.failure(RuntimeException("not found"))

        val result = stateManager.reactivate("alarm1")

        assertTrue(result.isSuccess)
        assertNull(result.getOrNull())
        verify(exactly = 0) { mockScheduler.scheduleAlarm(any()) }
    }

    // endregion

    // region all transitions deactivate consistently

    @Test
    fun `all deactivating transitions call the same three side effects`() = runTest {
        coEvery { mockRepository.markDone(any()) } returns Result.success(Unit)
        coEvery { mockRepository.markCancelled(any()) } returns Result.success(Unit)
        coEvery { mockRepository.deleteAlarm(any()) } returns Result.success(Unit)

        val operations = listOf(
            "markDone" to suspend { stateManager.markDone("alarm1") },
            "markCancelled" to suspend { stateManager.markCancelled("alarm2") },
            "delete" to suspend { stateManager.delete("alarm3") }
        )

        for ((name, operation) in operations) {
            operation()
        }

        // Each operation should cancel, exit urgent, and dismiss notification
        verify(exactly = 1) { mockScheduler.cancelAlarm("alarm1") }
        verify(exactly = 1) { mockScheduler.cancelAlarm("alarm2") }
        verify(exactly = 1) { mockScheduler.cancelAlarm("alarm3") }
        verify(exactly = 1) { mockUrgentStateManager.exitUrgentState("alarm1") }
        verify(exactly = 1) { mockUrgentStateManager.exitUrgentState("alarm2") }
        verify(exactly = 1) { mockUrgentStateManager.exitUrgentState("alarm3") }
        verify(exactly = 3) { mockNotificationManager.cancel(any()) }
    }

    @Test
    fun `dismiss does not deactivate - only exits urgent and dismisses notification`() {
        stateManager.dismiss("alarm1")

        verify { mockUrgentStateManager.exitUrgentState("alarm1") }
        verify { mockNotificationManager.cancel(AlarmUtils.getNotificationId("alarm1")) }
        verify(exactly = 0) { mockScheduler.cancelAlarm(any()) }
    }

    @Test
    fun `snooze deactivates then reschedules`() = runTest {
        val alarm = createTestAlarm(id = "alarm1")
        coEvery { mockRepository.getAlarm("alarm1") } returns Result.success(alarm)
        coEvery { mockRepository.snoozeAlarm(any(), any()) } returns Result.success(Unit)
        every { mockScheduler.scheduleSnooze(any(), any(), any()) } returns true

        stateManager.snooze("alarm1", SnoozeDuration.TEN_MINUTES)

        // Deactivation side effects
        verify(exactly = 1) { mockScheduler.cancelAlarm("alarm1") }
        verify(exactly = 1) { mockUrgentStateManager.exitUrgentState("alarm1") }
        verify(exactly = 1) { mockNotificationManager.cancel(any()) }
        // Then reschedule
        verify(exactly = 1) { mockScheduler.scheduleSnooze(eq("alarm1"), any(), any()) }
    }

    // endregion
}
