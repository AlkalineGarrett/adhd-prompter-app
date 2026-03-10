package org.alkaline.taskbrain.ui.currentnote

import com.google.firebase.Timestamp
import org.alkaline.taskbrain.data.Alarm
import org.alkaline.taskbrain.data.AlarmStatus
import org.alkaline.taskbrain.ui.currentnote.util.AlarmOverlayMapping
import org.alkaline.taskbrain.ui.currentnote.util.AlarmSymbolUtils
import org.alkaline.taskbrain.ui.currentnote.util.SymbolBadge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class AlarmToSymbolStateTest {

    private fun makeAlarm(
        status: AlarmStatus = AlarmStatus.PENDING,
        upcomingTime: Timestamp? = null,
        notifyTime: Timestamp? = null,
        urgentTime: Timestamp? = null,
        alarmTime: Timestamp? = null
    ) = Alarm(
        id = "test",
        userId = "user",
        noteId = "note",
        lineContent = "test line",
        status = status,
        upcomingTime = upcomingTime,
        notifyTime = notifyTime,
        urgentTime = urgentTime,
        alarmTime = alarmTime
    )

    private val now = Timestamp.now()
    private val pastTime = Timestamp(Date(now.toDate().time - 60_000))
    private val futureTime = Timestamp(Date(now.toDate().time + 60_000))

    @Test
    fun `DONE alarm returns Corner badge`() {
        val alarm = makeAlarm(status = AlarmStatus.DONE, alarmTime = pastTime)
        val overlay = AlarmOverlayMapping.alarmToOverlay(alarm, now)
        assertEquals(AlarmSymbolUtils.ALARM_SYMBOL, overlay.symbol)
        assertTrue(overlay.badge is SymbolBadge.Corner)
        assertEquals("✓", (overlay.badge as SymbolBadge.Corner).text)
    }

    @Test
    fun `CANCELLED alarm returns Corner badge`() {
        val alarm = makeAlarm(status = AlarmStatus.CANCELLED, alarmTime = pastTime)
        val overlay = AlarmOverlayMapping.alarmToOverlay(alarm, now)
        assertEquals(AlarmSymbolUtils.ALARM_SYMBOL, overlay.symbol)
        assertTrue(overlay.badge is SymbolBadge.Corner)
        assertEquals("✕", (overlay.badge as SymbolBadge.Corner).text)
    }

    @Test
    fun `PENDING alarm with all thresholds in past returns Centered badge`() {
        val alarm = makeAlarm(
            status = AlarmStatus.PENDING,
            upcomingTime = pastTime,
            notifyTime = pastTime,
            alarmTime = pastTime
        )
        val overlay = AlarmOverlayMapping.alarmToOverlay(alarm, now)
        assertTrue(overlay.badge is SymbolBadge.Centered)
        assertEquals("!", (overlay.badge as SymbolBadge.Centered).text)
    }

    @Test
    fun `PENDING alarm with future threshold returns None badge`() {
        val alarm = makeAlarm(
            status = AlarmStatus.PENDING,
            upcomingTime = pastTime,
            alarmTime = futureTime
        )
        val overlay = AlarmOverlayMapping.alarmToOverlay(alarm, now)
        assertTrue(overlay.badge is SymbolBadge.None)
    }

    @Test
    fun `PENDING alarm with no thresholds returns None badge`() {
        val alarm = makeAlarm(status = AlarmStatus.PENDING)
        val overlay = AlarmOverlayMapping.alarmToOverlay(alarm, now)
        assertTrue(overlay.badge is SymbolBadge.None)
    }

    @Test
    fun `DONE status takes precedence over past due thresholds`() {
        val alarm = makeAlarm(
            status = AlarmStatus.DONE,
            upcomingTime = pastTime,
            alarmTime = pastTime
        )
        val overlay = AlarmOverlayMapping.alarmToOverlay(alarm, now)
        assertTrue(overlay.badge is SymbolBadge.Corner)
        assertEquals("✓", (overlay.badge as SymbolBadge.Corner).text)
    }

    @Test
    fun `CANCELLED status takes precedence over past due thresholds`() {
        val alarm = makeAlarm(
            status = AlarmStatus.CANCELLED,
            upcomingTime = pastTime,
            alarmTime = pastTime
        )
        val overlay = AlarmOverlayMapping.alarmToOverlay(alarm, now)
        assertTrue(overlay.badge is SymbolBadge.Corner)
        assertEquals("✕", (overlay.badge as SymbolBadge.Corner).text)
    }

    @Test
    fun `all overlays use alarm symbol`() {
        val alarms = listOf(
            makeAlarm(status = AlarmStatus.DONE),
            makeAlarm(status = AlarmStatus.CANCELLED),
            makeAlarm(status = AlarmStatus.PENDING, alarmTime = pastTime),
            makeAlarm(status = AlarmStatus.PENDING)
        )
        alarms.forEach { alarm ->
            assertEquals(AlarmSymbolUtils.ALARM_SYMBOL, AlarmOverlayMapping.alarmToOverlay(alarm, now).symbol)
        }
    }
}
