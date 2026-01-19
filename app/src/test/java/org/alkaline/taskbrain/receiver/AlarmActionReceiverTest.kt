package org.alkaline.taskbrain.receiver

import org.junit.Assert.*
import org.junit.Test

class AlarmActionReceiverTest {

    // region Action Constants Tests

    @Test
    fun `ACTION_MARK_DONE has expected value`() {
        assertEquals("org.alkaline.taskbrain.ACTION_MARK_DONE", AlarmActionReceiver.ACTION_MARK_DONE)
    }

    @Test
    fun `ACTION_SNOOZE has expected value`() {
        assertEquals("org.alkaline.taskbrain.ACTION_SNOOZE", AlarmActionReceiver.ACTION_SNOOZE)
    }

    @Test
    fun `ACTION_CANCEL has expected value`() {
        assertEquals("org.alkaline.taskbrain.ACTION_CANCEL", AlarmActionReceiver.ACTION_CANCEL)
    }

    @Test
    fun `EXTRA_ALARM_ID has expected value`() {
        assertEquals("alarm_id", AlarmActionReceiver.EXTRA_ALARM_ID)
    }

    @Test
    fun `all actions are unique`() {
        val actions = listOf(
            AlarmActionReceiver.ACTION_MARK_DONE,
            AlarmActionReceiver.ACTION_SNOOZE,
            AlarmActionReceiver.ACTION_CANCEL
        )

        // Verify all actions are distinct
        assertEquals(actions.size, actions.distinct().size)
    }

    // endregion
}
