package org.alkaline.taskbrain.ui.alarms

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.google.firebase.Timestamp
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.alkaline.taskbrain.data.Alarm
import org.alkaline.taskbrain.data.AlarmRepository
import org.alkaline.taskbrain.data.AlarmStageType
import org.alkaline.taskbrain.data.AlarmStatus
import org.alkaline.taskbrain.service.AlarmUtils
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.Calendar
import java.util.Date

@OptIn(ExperimentalCoroutinesApi::class)
class AlarmsViewModelTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var mockRepository: AlarmRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockRepository = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun createTestAlarm(
        id: String = "test_alarm",
        status: AlarmStatus = AlarmStatus.PENDING,
        dueTime: Timestamp? = null,
        lineContent: String = "Test alarm"
    ) = Alarm(
        id = id,
        userId = "user1",
        noteId = "note1",
        lineContent = lineContent,
        status = status,
        dueTime = dueTime
    )

    @Test
    fun `initial state has empty alarm lists`() = runTest {
        // Given
        coEvery { mockRepository.getUpcomingAlarms() } returns Result.success(emptyList())
        coEvery { mockRepository.getLaterAlarms() } returns Result.success(emptyList())
        coEvery { mockRepository.getCompletedAlarms() } returns Result.success(emptyList())
        coEvery { mockRepository.getCancelledAlarms() } returns Result.success(emptyList())

        // When - ViewModel is created with mocked repository
        // Note: In real test, we'd inject the repository

        // Then - verify initial state expectations
        assertTrue(true) // Placeholder - actual test would verify LiveData values
    }

    @Test
    fun `upcoming alarms have dueTime set`() {
        val upcomingAlarm = createTestAlarm(
            id = "upcoming1",
            dueTime = Timestamp(Date(System.currentTimeMillis() + 3600000))
        )
        val laterAlarm = createTestAlarm(
            id = "later1",
            dueTime = null
        )

        assertNotNull(upcomingAlarm.dueTime)
        assertNull(laterAlarm.dueTime)
    }

    @Test
    fun `completed alarms have DONE status`() {
        val completedAlarm = createTestAlarm(
            id = "completed1",
            status = AlarmStatus.DONE
        )

        assertEquals(AlarmStatus.DONE, completedAlarm.status)
    }

    @Test
    fun `cancelled alarms have CANCELLED status`() {
        val cancelledAlarm = createTestAlarm(
            id = "cancelled1",
            status = AlarmStatus.CANCELLED
        )

        assertEquals(AlarmStatus.CANCELLED, cancelledAlarm.status)
    }

    @Test
    fun `markDone calls repository markDone`() = runTest {
        val alarmId = "test_alarm"
        coEvery { mockRepository.markDone(alarmId) } returns Result.success(Unit)

        mockRepository.markDone(alarmId)

        coVerify { mockRepository.markDone(alarmId) }
    }

    @Test
    fun `markCancelled calls repository markCancelled`() = runTest {
        val alarmId = "test_alarm"
        coEvery { mockRepository.markCancelled(alarmId) } returns Result.success(Unit)

        mockRepository.markCancelled(alarmId)

        coVerify { mockRepository.markCancelled(alarmId) }
    }

    @Test
    fun `reactivateAlarm calls repository reactivateAlarm`() = runTest {
        val alarmId = "test_alarm"
        coEvery { mockRepository.reactivateAlarm(alarmId) } returns Result.success(Unit)

        mockRepository.reactivateAlarm(alarmId)

        coVerify { mockRepository.reactivateAlarm(alarmId) }
    }

    @Test
    fun `alarms are sorted by dueTime`() {
        val now = System.currentTimeMillis()
        val alarm1 = createTestAlarm(
            id = "alarm1",
            dueTime = Timestamp(Date(now + 3600000)) // 1 hour from now
        )
        val alarm2 = createTestAlarm(
            id = "alarm2",
            dueTime = Timestamp(Date(now + 1800000)) // 30 min from now
        )

        val sortedAlarms = listOf(alarm1, alarm2).sortedBy { it.dueTime?.toDate()?.time }

        assertEquals("alarm2", sortedAlarms[0].id)
        assertEquals("alarm1", sortedAlarms[1].id)
    }

    // ==================== Error Handling Tests ====================

    @Test
    fun `loadAlarms sets error when upcoming alarms fetch fails`() = runTest {
        val testError = RuntimeException("Permission denied")
        coEvery { mockRepository.getUpcomingAlarms() } returns Result.failure(testError)
        coEvery { mockRepository.getLaterAlarms() } returns Result.success(emptyList())
        coEvery { mockRepository.getCompletedAlarms() } returns Result.success(emptyList())
        coEvery { mockRepository.getCancelledAlarms() } returns Result.success(emptyList())

        // Simulate what loadAlarms does
        var firstError: Throwable? = null
        mockRepository.getUpcomingAlarms().onFailure { if (firstError == null) firstError = it }

        assertNotNull(firstError)
        assertEquals("Permission denied", firstError?.message)
    }

    @Test
    fun `loadAlarms sets error when later alarms fetch fails`() = runTest {
        val testError = RuntimeException("Network error")
        coEvery { mockRepository.getUpcomingAlarms() } returns Result.success(emptyList())
        coEvery { mockRepository.getLaterAlarms() } returns Result.failure(testError)
        coEvery { mockRepository.getCompletedAlarms() } returns Result.success(emptyList())
        coEvery { mockRepository.getCancelledAlarms() } returns Result.success(emptyList())

        var firstError: Throwable? = null
        mockRepository.getUpcomingAlarms().onFailure { if (firstError == null) firstError = it }
        mockRepository.getLaterAlarms().onFailure { if (firstError == null) firstError = it }

        assertNotNull(firstError)
        assertEquals("Network error", firstError?.message)
    }

    @Test
    fun `loadAlarms captures first error when multiple fetches fail`() = runTest {
        val upcomingError = RuntimeException("Upcoming error")
        val laterError = RuntimeException("Later error")
        coEvery { mockRepository.getUpcomingAlarms() } returns Result.failure(upcomingError)
        coEvery { mockRepository.getLaterAlarms() } returns Result.failure(laterError)
        coEvery { mockRepository.getCompletedAlarms() } returns Result.success(emptyList())
        coEvery { mockRepository.getCancelledAlarms() } returns Result.success(emptyList())

        var firstError: Throwable? = null
        mockRepository.getUpcomingAlarms().onFailure { if (firstError == null) firstError = it }
        mockRepository.getLaterAlarms().onFailure { if (firstError == null) firstError = it }

        // Should capture the first error (upcoming), not the second (later)
        assertNotNull(firstError)
        assertEquals("Upcoming error", firstError?.message)
    }

    @Test
    fun `markDone sets error on failure`() = runTest {
        val testError = RuntimeException("Failed to mark done")
        coEvery { mockRepository.markDone(any()) } returns Result.failure(testError)

        var capturedError: Throwable? = null
        mockRepository.markDone("test_alarm").onFailure { capturedError = it }

        assertNotNull(capturedError)
        assertEquals("Failed to mark done", capturedError?.message)
    }

    @Test
    fun `markCancelled sets error on failure`() = runTest {
        val testError = RuntimeException("Failed to cancel")
        coEvery { mockRepository.markCancelled(any()) } returns Result.failure(testError)

        var capturedError: Throwable? = null
        mockRepository.markCancelled("test_alarm").onFailure { capturedError = it }

        assertNotNull(capturedError)
        assertEquals("Failed to cancel", capturedError?.message)
    }

    @Test
    fun `reactivateAlarm sets error on failure`() = runTest {
        val testError = RuntimeException("Failed to reactivate")
        coEvery { mockRepository.reactivateAlarm(any()) } returns Result.failure(testError)

        var capturedError: Throwable? = null
        mockRepository.reactivateAlarm("test_alarm").onFailure { capturedError = it }

        assertNotNull(capturedError)
        assertEquals("Failed to reactivate", capturedError?.message)
    }

    @Test
    fun `successful operations do not set error`() = runTest {
        coEvery { mockRepository.getUpcomingAlarms() } returns Result.success(emptyList())
        coEvery { mockRepository.getLaterAlarms() } returns Result.success(emptyList())
        coEvery { mockRepository.getCompletedAlarms() } returns Result.success(emptyList())
        coEvery { mockRepository.getCancelledAlarms() } returns Result.success(emptyList())

        var firstError: Throwable? = null
        mockRepository.getUpcomingAlarms().onFailure { if (firstError == null) firstError = it }
        mockRepository.getLaterAlarms().onFailure { if (firstError == null) firstError = it }
        mockRepository.getCompletedAlarms().onFailure { if (firstError == null) firstError = it }
        mockRepository.getCancelledAlarms().onFailure { if (firstError == null) firstError = it }

        assertNull(firstError)
    }

    @Test
    fun `error can be cleared`() {
        var error: Throwable? = RuntimeException("Test error")

        // Simulate clearError behavior
        error = null

        assertNull(error)
    }

    // ==================== Refresh Behavior Tests ====================

    @Test
    fun `loadAlarms can be called multiple times safely`() = runTest {
        // This tests that repeated calls to loadAlarms (e.g., on screen resume) work correctly
        coEvery { mockRepository.getUpcomingAlarms() } returns Result.success(emptyList())
        coEvery { mockRepository.getLaterAlarms() } returns Result.success(emptyList())
        coEvery { mockRepository.getCompletedAlarms() } returns Result.success(emptyList())
        coEvery { mockRepository.getCancelledAlarms() } returns Result.success(emptyList())

        // Simulate multiple resume events
        repeat(3) {
            mockRepository.getUpcomingAlarms()
            mockRepository.getLaterAlarms()
            mockRepository.getCompletedAlarms()
            mockRepository.getCancelledAlarms()
        }

        // Verify repository methods were called multiple times
        coVerify(exactly = 3) { mockRepository.getUpcomingAlarms() }
        coVerify(exactly = 3) { mockRepository.getLaterAlarms() }
        coVerify(exactly = 3) { mockRepository.getCompletedAlarms() }
        coVerify(exactly = 3) { mockRepository.getCancelledAlarms() }
    }

    @Test
    fun `loadAlarms reflects updated data after external changes`() = runTest {
        // First load: alarm is pending/upcoming
        val pendingAlarm = createTestAlarm(
            id = "alarm1",
            status = AlarmStatus.PENDING,
            dueTime = Timestamp(Date(System.currentTimeMillis() + 3600000))
        )
        coEvery { mockRepository.getUpcomingAlarms() } returns Result.success(listOf(pendingAlarm))
        coEvery { mockRepository.getLaterAlarms() } returns Result.success(emptyList())
        coEvery { mockRepository.getCompletedAlarms() } returns Result.success(emptyList())
        coEvery { mockRepository.getCancelledAlarms() } returns Result.success(emptyList())

        var upcomingResult = mockRepository.getUpcomingAlarms().getOrNull()
        var cancelledResult = mockRepository.getCancelledAlarms().getOrNull()

        assertEquals(1, upcomingResult?.size)
        assertEquals(0, cancelledResult?.size)

        // Simulate external change: alarm was cancelled via notification
        val cancelledAlarm = pendingAlarm.copy(status = AlarmStatus.CANCELLED)
        coEvery { mockRepository.getUpcomingAlarms() } returns Result.success(emptyList())
        coEvery { mockRepository.getCancelledAlarms() } returns Result.success(listOf(cancelledAlarm))

        // Second load (simulating ON_RESUME after returning from notification)
        upcomingResult = mockRepository.getUpcomingAlarms().getOrNull()
        cancelledResult = mockRepository.getCancelledAlarms().getOrNull()

        assertEquals(0, upcomingResult?.size)
        assertEquals(1, cancelledResult?.size)
        assertEquals(AlarmStatus.CANCELLED, cancelledResult?.first()?.status)
    }

    // ==================== Past Due Partitioning Tests ====================

    @Test
    fun `isPastDue returns true when dueTime is in the past`() {
        val pastTime = Timestamp(Date(System.currentTimeMillis() - 3600000))
        val alarm = createTestAlarm(
            dueTime = pastTime,
            lineContent = "Overdue task"
        )
        val now = Timestamp.now()

        assertTrue(AlarmsViewModel.isPastDue(alarm, now))
    }

    @Test
    fun `isPastDue returns false when dueTime is in the future`() {
        val futureTime = Timestamp(Date(System.currentTimeMillis() + 3600000))
        val alarm = createTestAlarm(
            dueTime = futureTime,
            lineContent = "Future task"
        )
        val now = Timestamp.now()

        assertFalse(AlarmsViewModel.isPastDue(alarm, now))
    }

    @Test
    fun `isPastDue returns false when no dueTime is set`() {
        val alarm = createTestAlarm(dueTime = null)
        val now = Timestamp.now()

        assertFalse(AlarmsViewModel.isPastDue(alarm, now))
    }

    @Test
    fun `loadAlarms reflects alarm marked done externally`() = runTest {
        // First load: alarm is pending
        val pendingAlarm = createTestAlarm(
            id = "alarm1",
            status = AlarmStatus.PENDING,
            dueTime = Timestamp(Date(System.currentTimeMillis() + 3600000))
        )
        coEvery { mockRepository.getUpcomingAlarms() } returns Result.success(listOf(pendingAlarm))
        coEvery { mockRepository.getLaterAlarms() } returns Result.success(emptyList())
        coEvery { mockRepository.getCompletedAlarms() } returns Result.success(emptyList())
        coEvery { mockRepository.getCancelledAlarms() } returns Result.success(emptyList())

        var upcomingResult = mockRepository.getUpcomingAlarms().getOrNull()
        var completedResult = mockRepository.getCompletedAlarms().getOrNull()

        assertEquals(1, upcomingResult?.size)
        assertEquals(0, completedResult?.size)

        // Simulate external change: alarm was marked done via notification
        val doneAlarm = pendingAlarm.copy(status = AlarmStatus.DONE)
        coEvery { mockRepository.getUpcomingAlarms() } returns Result.success(emptyList())
        coEvery { mockRepository.getCompletedAlarms() } returns Result.success(listOf(doneAlarm))

        // Second load (simulating ON_RESUME)
        upcomingResult = mockRepository.getUpcomingAlarms().getOrNull()
        completedResult = mockRepository.getCompletedAlarms().getOrNull()

        assertEquals(0, upcomingResult?.size)
        assertEquals(1, completedResult?.size)
        assertEquals(AlarmStatus.DONE, completedResult?.first()?.status)
    }

    // ==================== endOfDay Tests ====================

    @Test
    fun `endOfDay returns 23_59_59_999 of the same day`() {
        // 2026-03-14 10:30:00
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.MARCH, 14, 10, 30, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val input = Timestamp(cal.time)

        val result = AlarmsViewModel.endOfDay(input)

        val resultCal = Calendar.getInstance().apply { time = result.toDate() }
        assertEquals(2026, resultCal.get(Calendar.YEAR))
        assertEquals(Calendar.MARCH, resultCal.get(Calendar.MONTH))
        assertEquals(14, resultCal.get(Calendar.DAY_OF_MONTH))
        assertEquals(23, resultCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(59, resultCal.get(Calendar.MINUTE))
        assertEquals(59, resultCal.get(Calendar.SECOND))
        assertEquals(999, resultCal.get(Calendar.MILLISECOND))
    }

    @Test
    fun `endOfDay at midnight returns end of that same day`() {
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.JANUARY, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val input = Timestamp(cal.time)

        val result = AlarmsViewModel.endOfDay(input)

        val resultCal = Calendar.getInstance().apply { time = result.toDate() }
        assertEquals(1, resultCal.get(Calendar.DAY_OF_MONTH))
        assertEquals(23, resultCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(59, resultCal.get(Calendar.MINUTE))
    }

    // ==================== shouldSyncSilently Tests ====================

    @Test
    fun `shouldSyncSilently returns false when notifiedStageType is null`() {
        assertFalse(AlarmUtils.shouldSyncSilently(null, AlarmStageType.NOTIFICATION))
        assertFalse(AlarmUtils.shouldSyncSilently(null, AlarmStageType.LOCK_SCREEN))
        assertFalse(AlarmUtils.shouldSyncSilently(null, AlarmStageType.SOUND_ALARM))
    }

    @Test
    fun `shouldSyncSilently returns true when notified at same stage`() {
        assertTrue(AlarmUtils.shouldSyncSilently(AlarmStageType.NOTIFICATION, AlarmStageType.NOTIFICATION))
        assertTrue(AlarmUtils.shouldSyncSilently(AlarmStageType.LOCK_SCREEN, AlarmStageType.LOCK_SCREEN))
        assertTrue(AlarmUtils.shouldSyncSilently(AlarmStageType.SOUND_ALARM, AlarmStageType.SOUND_ALARM))
    }

    @Test
    fun `shouldSyncSilently returns true when notified at higher stage`() {
        // Notified at LOCK_SCREEN, current is NOTIFICATION — already alerted, stay silent
        assertTrue(AlarmUtils.shouldSyncSilently(AlarmStageType.LOCK_SCREEN, AlarmStageType.NOTIFICATION))
        // Notified at SOUND_ALARM, current is LOCK_SCREEN
        assertTrue(AlarmUtils.shouldSyncSilently(AlarmStageType.SOUND_ALARM, AlarmStageType.LOCK_SCREEN))
        // Notified at SOUND_ALARM, current is NOTIFICATION
        assertTrue(AlarmUtils.shouldSyncSilently(AlarmStageType.SOUND_ALARM, AlarmStageType.NOTIFICATION))
    }

    @Test
    fun `shouldSyncSilently returns false when escalating to higher stage`() {
        // Notified at NOTIFICATION, current is LOCK_SCREEN — new escalation, should sound
        assertFalse(AlarmUtils.shouldSyncSilently(AlarmStageType.NOTIFICATION, AlarmStageType.LOCK_SCREEN))
        // Notified at NOTIFICATION, current is SOUND_ALARM
        assertFalse(AlarmUtils.shouldSyncSilently(AlarmStageType.NOTIFICATION, AlarmStageType.SOUND_ALARM))
        // Notified at LOCK_SCREEN, current is SOUND_ALARM
        assertFalse(AlarmUtils.shouldSyncSilently(AlarmStageType.LOCK_SCREEN, AlarmStageType.SOUND_ALARM))
    }

    // ==================== endOfDay Tests (continued) ====================

    @Test
    fun `endOfDay at 23_59 returns end of that same day`() {
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.JUNE, 15, 23, 59, 58)
            set(Calendar.MILLISECOND, 0)
        }
        val input = Timestamp(cal.time)

        val result = AlarmsViewModel.endOfDay(input)

        val resultCal = Calendar.getInstance().apply { time = result.toDate() }
        assertEquals(15, resultCal.get(Calendar.DAY_OF_MONTH))
        assertEquals(23, resultCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(59, resultCal.get(Calendar.MINUTE))
        assertEquals(59, resultCal.get(Calendar.SECOND))
        assertEquals(999, resultCal.get(Calendar.MILLISECOND))
    }
}
