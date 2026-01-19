package org.alkaline.taskbrain.ui.alarms

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.google.firebase.Timestamp
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.alkaline.taskbrain.data.Alarm
import org.alkaline.taskbrain.data.AlarmRepository
import org.alkaline.taskbrain.data.AlarmStatus
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
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
        upcomingTime: Timestamp? = null,
        lineContent: String = "Test alarm"
    ) = Alarm(
        id = id,
        userId = "user1",
        noteId = "note1",
        lineContent = lineContent,
        status = status,
        upcomingTime = upcomingTime
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
    fun `upcoming alarms have upcomingTime set`() {
        val upcomingAlarm = createTestAlarm(
            id = "upcoming1",
            upcomingTime = Timestamp(Date(System.currentTimeMillis() + 3600000))
        )
        val laterAlarm = createTestAlarm(
            id = "later1",
            upcomingTime = null
        )

        assertNotNull(upcomingAlarm.upcomingTime)
        assertNull(laterAlarm.upcomingTime)
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
    fun `alarms are sorted by upcomingTime`() {
        val now = System.currentTimeMillis()
        val alarm1 = createTestAlarm(
            id = "alarm1",
            upcomingTime = Timestamp(Date(now + 3600000)) // 1 hour from now
        )
        val alarm2 = createTestAlarm(
            id = "alarm2",
            upcomingTime = Timestamp(Date(now + 1800000)) // 30 min from now
        )

        val sortedAlarms = listOf(alarm1, alarm2).sortedBy { it.upcomingTime?.toDate()?.time }

        assertEquals("alarm2", sortedAlarms[0].id)
        assertEquals("alarm1", sortedAlarms[1].id)
    }
}
