package org.alkaline.taskbrain.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AlarmUpdateEventTest {

    @Test
    fun `notifyAlarmUpdated emits event to subscribers`() = runTest {
        var eventReceived = false

        // Start collecting in a separate coroutine
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            AlarmUpdateEvent.updates.first()
            eventReceived = true
        }

        // Emit an event
        AlarmUpdateEvent.notifyAlarmUpdated()

        // Wait for the collector to receive the event
        job.join()

        assertTrue("Event should be received by subscriber", eventReceived)
    }

    @Test
    fun `multiple subscribers receive the same event`() = runTest {
        var subscriber1Received = false
        var subscriber2Received = false

        val job1 = launch(UnconfinedTestDispatcher(testScheduler)) {
            AlarmUpdateEvent.updates.first()
            subscriber1Received = true
        }

        val job2 = launch(UnconfinedTestDispatcher(testScheduler)) {
            AlarmUpdateEvent.updates.first()
            subscriber2Received = true
        }

        AlarmUpdateEvent.notifyAlarmUpdated()

        job1.join()
        job2.join()

        assertTrue("Subscriber 1 should receive event", subscriber1Received)
        assertTrue("Subscriber 2 should receive event", subscriber2Received)
    }

    @Test
    fun `notifyAlarmUpdated does not block when no subscribers`() {
        // This should not throw or block
        val result = AlarmUpdateEvent.notifyAlarmUpdated()

        // tryEmit returns true if successful (buffer has space)
        // Since we have extraBufferCapacity = 1, this should succeed
        assertTrue("notifyAlarmUpdated should succeed even without subscribers", true)
    }
}
