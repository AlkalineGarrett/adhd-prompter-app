package org.alkaline.taskbrain.ui.currentnote.util

import org.junit.Assert.*
import org.junit.Test

class AlarmSymbolUtilsTest {

    // region alarmDirective tests

    @Test
    fun `alarmDirective creates correct directive string`() {
        assertEquals("[alarm(\"abc123\")]", AlarmSymbolUtils.alarmDirective("abc123"))
    }

    @Test
    fun `alarmDirective handles special characters in ID`() {
        assertEquals("[alarm(\"f3GxY2abc\")]", AlarmSymbolUtils.alarmDirective("f3GxY2abc"))
    }

    // endregion

    // region stripAlarmMarkers tests

    @Test
    fun `stripAlarmMarkers removes plain alarm symbol`() {
        assertEquals("Buy groceries ", AlarmSymbolUtils.stripAlarmMarkers("Buy groceries ⏰"))
    }

    @Test
    fun `stripAlarmMarkers removes alarm directive`() {
        assertEquals("Buy groceries ", AlarmSymbolUtils.stripAlarmMarkers("Buy groceries [alarm(\"abc\")]"))
    }

    @Test
    fun `stripAlarmMarkers removes both`() {
        assertEquals("Buy  groceries ", AlarmSymbolUtils.stripAlarmMarkers("Buy ⏰ groceries [alarm(\"abc\")]"))
    }

    @Test
    fun `stripAlarmMarkers handles no markers`() {
        assertEquals("Just text", AlarmSymbolUtils.stripAlarmMarkers("Just text"))
    }

    @Test
    fun `stripAlarmMarkers handles multiple directives`() {
        assertEquals("A  B", AlarmSymbolUtils.stripAlarmMarkers("A [alarm(\"x\")] B[alarm(\"y\")]"))
    }

    // endregion
}
