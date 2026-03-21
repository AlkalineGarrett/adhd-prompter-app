package org.alkaline.taskbrain.ui.currentnote

import com.google.firebase.Timestamp
import kotlinx.coroutines.test.runTest
import org.alkaline.taskbrain.data.Alarm
import org.alkaline.taskbrain.data.AlarmMarkers
import org.alkaline.taskbrain.data.AlarmStatus
import org.alkaline.taskbrain.data.NoteLine
import org.alkaline.taskbrain.dsl.directives.DirectiveInstance
import org.alkaline.taskbrain.dsl.directives.DirectiveResult
import org.alkaline.taskbrain.ui.currentnote.util.SymbolBadge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.alkaline.taskbrain.dsl.runtime.values.UndefinedVal
import org.junit.Test
import java.util.Date

class ViewModelPureLogicTest {

    // ==================== findAlarmNoteIdUpdates ====================

    @Test
    fun `findAlarmNoteIdUpdates returns empty when no directives`() = runTest {
        val lines = listOf(
            NoteLine("Plain text", "note1"),
            NoteLine("No alarms", "note2")
        )
        val updates = findAlarmNoteIdUpdates(lines) { null }
        assertTrue(updates.isEmpty())
    }

    @Test
    fun `findAlarmNoteIdUpdates detects stale noteId`() = runTest {
        val lines = listOf(
            NoteLine("Task [alarm(\"alarm1\")]", "note2")
        )
        val updates = findAlarmNoteIdUpdates(lines) { alarmId ->
            if (alarmId == "alarm1") "note1" else null // alarm currently on note1
        }
        assertEquals(1, updates.size)
        assertEquals("alarm1", updates[0].alarmId)
        assertEquals("note2", updates[0].lineNoteId) // should be updated to note2
    }

    @Test
    fun `findAlarmNoteIdUpdates skips when noteId matches`() = runTest {
        val lines = listOf(
            NoteLine("Task [alarm(\"alarm1\")]", "note1")
        )
        val updates = findAlarmNoteIdUpdates(lines) { "note1" }
        assertTrue(updates.isEmpty())
    }

    @Test
    fun `findAlarmNoteIdUpdates skips lines without noteId`() = runTest {
        val lines = listOf(
            NoteLine("Task [alarm(\"alarm1\")]", null)
        )
        val updates = findAlarmNoteIdUpdates(lines) { "note1" }
        assertTrue(updates.isEmpty())
    }

    @Test
    fun `findAlarmNoteIdUpdates handles multiple directives on one line`() = runTest {
        val lines = listOf(
            NoteLine("Task [alarm(\"a1\")] [alarm(\"a2\")]", "note1")
        )
        val alarmNoteIds = mapOf("a1" to "note1", "a2" to "oldNote")
        val updates = findAlarmNoteIdUpdates(lines) { alarmNoteIds[it] }

        assertEquals(1, updates.size) // only a2 needs update
        assertEquals("a2", updates[0].alarmId)
    }

    // ==================== mapResultsByPosition ====================

    // ==================== extractAlarmIds ====================

    @Test
    fun `extractAlarmIds returns empty for lines without directives`() {
        val lines = listOf(
            NoteLine("Plain text", "note1"),
            NoteLine("No alarms here", "note2")
        )
        assertTrue(extractAlarmIds(lines).isEmpty())
    }

    @Test
    fun `extractAlarmIds extracts single alarm ID`() {
        val lines = listOf(
            NoteLine("Task [alarm(\"abc123\")]", "note1")
        )
        assertEquals(listOf("abc123"), extractAlarmIds(lines))
    }

    @Test
    fun `extractAlarmIds extracts multiple alarm IDs from one line`() {
        val lines = listOf(
            NoteLine("Task [alarm(\"a1\")] [alarm(\"a2\")]", "note1")
        )
        assertEquals(listOf("a1", "a2"), extractAlarmIds(lines))
    }

    @Test
    fun `extractAlarmIds extracts across multiple lines`() {
        val lines = listOf(
            NoteLine("Line 1 [alarm(\"a1\")]", "note1"),
            NoteLine("Line 2 [alarm(\"a2\")]", "note2")
        )
        assertEquals(listOf("a1", "a2"), extractAlarmIds(lines))
    }

    @Test
    fun `extractAlarmIds deduplicates`() {
        val lines = listOf(
            NoteLine("Line 1 [alarm(\"a1\")]", "note1"),
            NoteLine("Line 2 [alarm(\"a1\")]", "note2")
        )
        assertEquals(listOf("a1"), extractAlarmIds(lines))
    }

    @Test
    fun `extractAlarmIds ignores lines with only plain alarm symbols`() {
        val lines = listOf(
            NoteLine("Task ⏰", "note1")
        )
        assertTrue(extractAlarmIds(lines).isEmpty())
    }

    // ==================== computeSymbolOverlays ====================

    private fun alarm(
        id: String,
        status: AlarmStatus = AlarmStatus.PENDING,
        dueTime: Timestamp? = null
    ) = Alarm(id = id, status = status, dueTime = dueTime)

    @Test
    fun `computeSymbolOverlays returns empty for line without directives`() {
        assertTrue(computeSymbolOverlays("Plain text", emptyMap(), Timestamp.now()).isEmpty())
    }

    @Test
    fun `computeSymbolOverlays returns overlay for cached alarm`() {
        val cache = mapOf("a1" to alarm("a1", AlarmStatus.DONE))
        val overlays = computeSymbolOverlays("Task [alarm(\"a1\")]", cache, Timestamp.now())

        assertEquals(1, overlays.size)
        assertEquals(AlarmMarkers.ALARM_SYMBOL, overlays[0].symbol)
        assertTrue(overlays[0].badge is SymbolBadge.Corner) // done = checkmark corner badge
    }

    @Test
    fun `computeSymbolOverlays returns None badge for missing alarm`() {
        val overlays = computeSymbolOverlays("Task [alarm(\"missing\")]", emptyMap(), Timestamp.now())

        assertEquals(1, overlays.size)
        assertEquals(SymbolBadge.None, overlays[0].badge)
    }

    @Test
    fun `computeSymbolOverlays preserves left-to-right order`() {
        val cache = mapOf(
            "a1" to alarm("a1", AlarmStatus.DONE),
            "a2" to alarm("a2", AlarmStatus.CANCELLED)
        )
        val overlays = computeSymbolOverlays(
            "Task [alarm(\"a1\")] [alarm(\"a2\")]", cache, Timestamp.now()
        )

        assertEquals(2, overlays.size)
        // First is DONE (checkmark), second is CANCELLED (X)
        val firstBadge = overlays[0].badge as SymbolBadge.Corner
        val secondBadge = overlays[1].badge as SymbolBadge.Corner
        assertEquals("✓", firstBadge.text)
        assertEquals("✕", secondBadge.text)
    }

    @Test
    fun `computeSymbolOverlays shows past due badge for overdue alarm`() {
        val pastDue = Timestamp(Date(System.currentTimeMillis() - 3600_000)) // 1 hour ago
        val cache = mapOf(
            "a1" to Alarm(
                id = "a1",
                status = AlarmStatus.PENDING,
                dueTime = pastDue
            )
        )
        val overlays = computeSymbolOverlays("Task [alarm(\"a1\")]", cache, Timestamp.now())

        assertEquals(1, overlays.size)
        assertTrue(overlays[0].badge is SymbolBadge.Centered) // past due = centered "!"
    }

    // ==================== mapResultsByPosition ====================

    private fun directiveInstance(uuid: String, lineIndex: Int, startOffset: Int, noteId: String? = null) =
        DirectiveInstance(uuid, lineIndex, startOffset, "[test]", noteId)

    @Test
    fun `mapResultsByPosition maps UUIDs to position keys`() {
        val instances = listOf(
            directiveInstance("uuid1", 0, 5),
            directiveInstance("uuid2", 2, 10)
        )
        val results = mapOf(
            "uuid1" to DirectiveResult.success(UndefinedVal),
            "uuid2" to DirectiveResult.success(UndefinedVal)
        )
        val effectiveIds = listOf("line-0", "line-1", "line-2")

        val mapped = mapResultsByPosition(instances, results, effectiveIds)
        assertEquals(2, mapped.size)
        assertTrue(mapped.containsKey("line-0:5"))
        assertTrue(mapped.containsKey("line-2:10"))
    }

    @Test
    fun `mapResultsByPosition skips instances without results`() {
        val instances = listOf(
            directiveInstance("uuid1", 0, 0),
            directiveInstance("uuid2", 1, 0)
        )
        val results = mapOf(
            "uuid1" to DirectiveResult.success(UndefinedVal)
        )
        val effectiveIds = listOf("line-0", "line-1")

        val mapped = mapResultsByPosition(instances, results, effectiveIds)
        assertEquals(1, mapped.size)
        assertTrue(mapped.containsKey("line-0:0"))
    }

    @Test
    fun `mapResultsByPosition returns empty for empty inputs`() {
        val mapped = mapResultsByPosition(emptyList(), emptyMap(), emptyList())
        assertTrue(mapped.isEmpty())
    }

    @Test
    fun `mapResultsByPosition uses effectiveIds for keys`() {
        val instances = listOf(
            directiveInstance("uuid1", 0, 5, noteId = "note1"),
            directiveInstance("uuid2", 2, 10, noteId = "note2")
        )
        val results = mapOf(
            "uuid1" to DirectiveResult.success(UndefinedVal),
            "uuid2" to DirectiveResult.success(UndefinedVal)
        )
        val effectiveIds = listOf("eid-0", "eid-1", "eid-2")

        val mapped = mapResultsByPosition(instances, results, effectiveIds)
        assertEquals(2, mapped.size)
        // Keys come from effectiveIds, not from noteId on the instance
        assertTrue(mapped.containsKey("eid-0:5"))
        assertTrue(mapped.containsKey("eid-2:10"))
    }

    @Test
    fun `mapResultsByPosition skips instances with out-of-bounds lineIndex`() {
        val instances = listOf(
            directiveInstance("uuid1", 0, 5),
            directiveInstance("uuid2", 5, 10) // lineIndex 5 out of bounds
        )
        val results = mapOf(
            "uuid1" to DirectiveResult.success(UndefinedVal),
            "uuid2" to DirectiveResult.success(UndefinedVal)
        )
        val effectiveIds = listOf("line-0", "line-1")

        val mapped = mapResultsByPosition(instances, results, effectiveIds)
        assertEquals(1, mapped.size)
        assertTrue(mapped.containsKey("line-0:5"))
    }

    // ==================== findExpandedPositions ====================

    @Test
    fun `findExpandedPositions returns positions of non-collapsed directives`() {
        val instances = listOf(
            directiveInstance("uuid1", 0, 5),
            directiveInstance("uuid2", 2, 10)
        )
        val results = mapOf(
            "uuid1" to DirectiveResult.success(UndefinedVal).copy(collapsed = false),
            "uuid2" to DirectiveResult.success(UndefinedVal).copy(collapsed = true)
        )

        val positions = findExpandedPositions(instances, results)
        assertEquals(1, positions.size)
        assertTrue(positions.contains(DirectivePosition(0, 5)))
    }

    @Test
    fun `findExpandedPositions returns empty when all collapsed`() {
        val instances = listOf(directiveInstance("uuid1", 0, 0))
        val results = mapOf(
            "uuid1" to DirectiveResult.success(UndefinedVal).copy(collapsed = true)
        )

        val positions = findExpandedPositions(instances, results)
        assertTrue(positions.isEmpty())
    }

    @Test
    fun `findExpandedPositions returns empty for empty results`() {
        val positions = findExpandedPositions(emptyList(), emptyMap())
        assertTrue(positions.isEmpty())
    }

    // ==================== mergeDirectiveResults ====================

    @Test
    fun `mergeDirectiveResults preserves existing collapsed state`() {
        val fresh = mapOf(
            "uuid1" to DirectiveResult.success(UndefinedVal),
            "uuid2" to DirectiveResult.success(UndefinedVal)
        )
        val current = mapOf(
            "uuid1" to DirectiveResult.success(UndefinedVal).copy(collapsed = false),
            "uuid2" to DirectiveResult.success(UndefinedVal).copy(collapsed = true)
        )

        val merged = mergeDirectiveResults(fresh, current)
        assertEquals(false, merged["uuid1"]!!.collapsed)
        assertEquals(true, merged["uuid2"]!!.collapsed)
    }

    @Test
    fun `mergeDirectiveResults defaults to collapsed when no existing state`() {
        val fresh = mapOf("uuid1" to DirectiveResult.success(UndefinedVal))
        val merged = mergeDirectiveResults(fresh, null)
        assertEquals(true, merged["uuid1"]!!.collapsed)
    }

    @Test
    fun `mergeDirectiveResults defaults to collapsed for new UUIDs`() {
        val fresh = mapOf("uuid1" to DirectiveResult.success(UndefinedVal))
        val current = mapOf("other" to DirectiveResult.success(UndefinedVal).copy(collapsed = false))

        val merged = mergeDirectiveResults(fresh, current)
        assertEquals(true, merged["uuid1"]!!.collapsed) // not in current, defaults to true
    }
}
