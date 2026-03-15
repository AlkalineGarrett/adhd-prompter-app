package org.alkaline.taskbrain.service

import android.app.NotificationManager
import com.google.firebase.Timestamp
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.alkaline.taskbrain.data.Alarm
import org.alkaline.taskbrain.data.AlarmRepository
import org.alkaline.taskbrain.data.AlarmStatus
import org.alkaline.taskbrain.data.RecurrenceType
import org.alkaline.taskbrain.data.RecurringAlarm
import org.alkaline.taskbrain.data.RecurringAlarmRepository
import org.alkaline.taskbrain.data.RecurringAlarmStatus
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.Date

class RecurrenceSchedulerTest {

    private lateinit var mockContext: android.content.Context
    private lateinit var mockRecurringRepo: RecurringAlarmRepository
    private lateinit var mockAlarmRepo: AlarmRepository
    private lateinit var mockAlarmScheduler: AlarmScheduler
    private lateinit var mockUrgentStateManager: UrgentStateManager
    private lateinit var mockNotificationManager: NotificationManager
    private lateinit var scheduler: RecurrenceScheduler

    // Tomorrow at 9 AM
    private val tomorrowMs = System.currentTimeMillis() + 24 * 60 * 60 * 1000
    private val tomorrowDate = Date(tomorrowMs)
    private val tomorrowTimestamp = Timestamp(tomorrowDate)

    // Day after tomorrow at 9 AM (next DAILY occurrence)
    private val dayAfterMs = tomorrowMs + 24 * 60 * 60 * 1000
    private val dayAfterDate = Date(dayAfterMs)

    @Before
    fun setUp() {
        mockContext = mockk(relaxed = true)
        mockRecurringRepo = mockk(relaxed = true)
        mockAlarmRepo = mockk(relaxed = true)
        mockAlarmScheduler = mockk(relaxed = true)
        mockUrgentStateManager = mockk(relaxed = true)
        mockNotificationManager = mockk(relaxed = true)

        scheduler = RecurrenceScheduler(
            context = mockContext,
            recurringRepo = mockRecurringRepo,
            alarmRepo = mockAlarmRepo,
            alarmScheduler = mockAlarmScheduler,
            urgentStateManager = mockUrgentStateManager,
            notificationManager = mockNotificationManager
        )
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun createDailyRecurring(
        id: String = "recurring1",
        currentAlarmId: String? = null
    ) = RecurringAlarm(
        id = id,
        noteId = "note1",
        lineContent = "Daily standup",
        recurrenceType = RecurrenceType.FIXED,
        rrule = "FREQ=DAILY",
        dueOffsetMs = 0,
        stages = Alarm.DEFAULT_STAGES,
        currentAlarmId = currentAlarmId,
        status = RecurringAlarmStatus.ACTIVE,
        createdAt = Timestamp.now()
    )

    private fun createAlarmInstance(
        id: String,
        recurringAlarmId: String = "recurring1",
        dueTime: Timestamp? = tomorrowTimestamp,
        status: AlarmStatus = AlarmStatus.PENDING
    ) = Alarm(
        id = id,
        noteId = "note1",
        lineContent = "Daily standup",
        dueTime = dueTime,
        stages = Alarm.DEFAULT_STAGES,
        recurringAlarmId = recurringAlarmId,
        status = status
    )

    // region createFirstInstance

    @Test
    fun `createFirstInstance creates exactly one alarm record`() = runTest {
        val recurring = createDailyRecurring()

        coEvery { mockAlarmRepo.createAlarm(any()) } returns Result.success("alarm1")
        coEvery { mockAlarmRepo.getAlarm("alarm1") } returns
                Result.success(createAlarmInstance("alarm1"))

        scheduler.createFirstInstance(recurring, tomorrowDate)

        coVerify(exactly = 1) { mockAlarmRepo.createAlarm(any()) }
    }

    @Test
    fun `createFirstInstance schedules exactly one alarm`() = runTest {
        val recurring = createDailyRecurring()
        val createdAlarm = createAlarmInstance("alarm1")

        coEvery { mockAlarmRepo.createAlarm(any()) } returns Result.success("alarm1")
        coEvery { mockAlarmRepo.getAlarm("alarm1") } returns Result.success(createdAlarm)

        scheduler.createFirstInstance(recurring, tomorrowDate)

        verify(exactly = 1) { mockAlarmScheduler.scheduleAlarm(any()) }
    }

    @Test
    fun `createFirstInstance updates currentAlarmId on template`() = runTest {
        val recurring = createDailyRecurring()

        coEvery { mockAlarmRepo.createAlarm(any()) } returns Result.success("alarm1")
        coEvery { mockAlarmRepo.getAlarm("alarm1") } returns
                Result.success(createAlarmInstance("alarm1"))

        scheduler.createFirstInstance(recurring, tomorrowDate)

        coVerify(exactly = 1) { mockRecurringRepo.updateCurrentAlarmId("recurring1", "alarm1") }
    }

    // endregion

    // region onFixedInstanceTriggered — no duplicate creation

    @Test
    fun `onFixedInstanceTriggered creates exactly one next instance`() = runTest {
        val recurring = createDailyRecurring(currentAlarmId = "alarm1")
        val triggeredAlarm = createAlarmInstance("alarm1")

        coEvery { mockRecurringRepo.get("recurring1") } returns Result.success(recurring)
        coEvery { mockAlarmRepo.createAlarm(any()) } returns Result.success("alarm2")
        coEvery { mockAlarmRepo.getAlarm("alarm2") } returns
                Result.success(createAlarmInstance("alarm2", dueTime = Timestamp(dayAfterDate)))
        coEvery { mockAlarmRepo.getPendingInstancesForRecurring("recurring1") } returns
                Result.success(listOf(createAlarmInstance("alarm2", dueTime = Timestamp(dayAfterDate))))

        scheduler.onFixedInstanceTriggered(triggeredAlarm)

        coVerify(exactly = 1) { mockAlarmRepo.createAlarm(any()) }
        verify(exactly = 1) { mockAlarmScheduler.scheduleAlarm(any()) }
    }

    @Test
    fun `onFixedInstanceTriggered skips if next instance already exists`() = runTest {
        // currentAlarmId is "alarm2" but triggered alarm is "alarm1" — next already created
        val recurring = createDailyRecurring(currentAlarmId = "alarm2")
        val triggeredAlarm = createAlarmInstance("alarm1")

        coEvery { mockRecurringRepo.get("recurring1") } returns Result.success(recurring)

        scheduler.onFixedInstanceTriggered(triggeredAlarm)

        coVerify(exactly = 0) { mockAlarmRepo.createAlarm(any()) }
        verify(exactly = 0) { mockAlarmScheduler.scheduleAlarm(any()) }
    }

    @Test
    fun `multiple stage triggers for same alarm create only one next instance`() = runTest {
        val recurring = createDailyRecurring(currentAlarmId = "alarm1")
        val triggeredAlarm = createAlarmInstance("alarm1")

        // First trigger: currentAlarmId matches, so it creates next instance
        coEvery { mockRecurringRepo.get("recurring1") } returns Result.success(recurring)
        coEvery { mockAlarmRepo.createAlarm(any()) } returns Result.success("alarm2")
        coEvery { mockAlarmRepo.getAlarm("alarm2") } returns
                Result.success(createAlarmInstance("alarm2", dueTime = Timestamp(dayAfterDate)))
        coEvery { mockAlarmRepo.getPendingInstancesForRecurring("recurring1") } returns
                Result.success(listOf(createAlarmInstance("alarm2", dueTime = Timestamp(dayAfterDate))))

        scheduler.onFixedInstanceTriggered(triggeredAlarm)

        // After first trigger, recurring template's currentAlarmId is updated to "alarm2"
        val updatedRecurring = recurring.copy(currentAlarmId = "alarm2")
        coEvery { mockRecurringRepo.get("recurring1") } returns Result.success(updatedRecurring)

        // Second stage trigger for the same alarm — should skip
        scheduler.onFixedInstanceTriggered(triggeredAlarm)

        // Only one alarm created total
        coVerify(exactly = 1) { mockAlarmRepo.createAlarm(any()) }
    }

    // endregion

    // region onInstanceCompleted — next instance creation

    @Test
    fun `completing first instance creates exactly one next instance`() = runTest {
        val recurring = createDailyRecurring(currentAlarmId = "alarm1")
        val completedAlarm = createAlarmInstance("alarm1")

        coEvery { mockRecurringRepo.get("recurring1") } returns Result.success(recurring)
        coEvery { mockAlarmRepo.createAlarm(any()) } returns Result.success("alarm2")
        coEvery { mockAlarmRepo.getAlarm("alarm2") } returns
                Result.success(createAlarmInstance("alarm2", dueTime = Timestamp(dayAfterDate)))
        coEvery { mockAlarmRepo.getPendingInstancesForRecurring("recurring1") } returns
                Result.success(listOf(createAlarmInstance("alarm2", dueTime = Timestamp(dayAfterDate))))

        scheduler.onInstanceCompleted(completedAlarm)

        coVerify(exactly = 1) { mockAlarmRepo.createAlarm(any()) }
        coVerify(exactly = 1) { mockRecurringRepo.recordCompletion(eq("recurring1"), any()) }
    }

    // endregion

    // region cleanUpOrphanedInstances

    @Test
    fun `orphaned instances are fully deactivated during cleanup`() = runTest {
        val recurring = createDailyRecurring(currentAlarmId = "alarm1")
        val triggeredAlarm = createAlarmInstance("alarm1")
        val orphan = createAlarmInstance("orphan1")

        coEvery { mockRecurringRepo.get("recurring1") } returns Result.success(recurring)
        coEvery { mockAlarmRepo.createAlarm(any()) } returns Result.success("alarm2")
        coEvery { mockAlarmRepo.getAlarm("alarm2") } returns
                Result.success(createAlarmInstance("alarm2", dueTime = Timestamp(dayAfterDate)))
        // Return both the new instance and an orphan
        coEvery { mockAlarmRepo.getPendingInstancesForRecurring("recurring1") } returns
                Result.success(listOf(
                    createAlarmInstance("alarm2", dueTime = Timestamp(dayAfterDate)),
                    orphan
                ))

        scheduler.onFixedInstanceTriggered(triggeredAlarm)

        // Orphan should get full deactivation
        verify(exactly = 1) { mockAlarmScheduler.cancelAlarm("orphan1") }
        verify(exactly = 1) { mockUrgentStateManager.exitUrgentState("orphan1") }
        verify(exactly = 1) { mockNotificationManager.cancel(AlarmUtils.getNotificationId("orphan1")) }
        coVerify(exactly = 1) { mockAlarmRepo.markCancelled("orphan1") }

        // Current instance should NOT be cleaned up
        verify(exactly = 0) { mockAlarmScheduler.cancelAlarm("alarm2") }
        coVerify(exactly = 0) { mockAlarmRepo.markCancelled("alarm2") }
    }

    // endregion

    // region full lifecycle — no extra records

    @Test
    fun `full daily recurring lifecycle creates exactly one record per day`() = runTest {
        val recurring = createDailyRecurring()

        // Step 1: Create first instance for tomorrow
        coEvery { mockAlarmRepo.createAlarm(any()) } returns Result.success("alarm1")
        coEvery { mockAlarmRepo.getAlarm("alarm1") } returns
                Result.success(createAlarmInstance("alarm1"))

        val firstId = scheduler.createFirstInstance(recurring, tomorrowDate)
        assertEquals("alarm1", firstId)

        // Step 2: First instance triggers — creates exactly one next instance
        val recurringWithFirst = recurring.copy(currentAlarmId = "alarm1")
        coEvery { mockRecurringRepo.get("recurring1") } returns Result.success(recurringWithFirst)
        coEvery { mockAlarmRepo.createAlarm(any()) } returns Result.success("alarm2")
        coEvery { mockAlarmRepo.getAlarm("alarm2") } returns
                Result.success(createAlarmInstance("alarm2", dueTime = Timestamp(dayAfterDate)))
        coEvery { mockAlarmRepo.getPendingInstancesForRecurring("recurring1") } returns
                Result.success(listOf(createAlarmInstance("alarm2", dueTime = Timestamp(dayAfterDate))))

        scheduler.onFixedInstanceTriggered(createAlarmInstance("alarm1"))

        // Step 3: Second stage trigger for same alarm — should not create another
        val recurringWithSecond = recurring.copy(currentAlarmId = "alarm2")
        coEvery { mockRecurringRepo.get("recurring1") } returns Result.success(recurringWithSecond)

        scheduler.onFixedInstanceTriggered(createAlarmInstance("alarm1"))

        // Total: exactly 2 alarm records created (first instance + one next instance)
        coVerify(exactly = 2) { mockAlarmRepo.createAlarm(any()) }
    }

    // endregion
}
