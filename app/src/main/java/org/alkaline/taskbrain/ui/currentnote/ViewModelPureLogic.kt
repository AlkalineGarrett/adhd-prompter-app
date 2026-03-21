package org.alkaline.taskbrain.ui.currentnote

import com.google.firebase.Timestamp
import org.alkaline.taskbrain.data.Alarm
import org.alkaline.taskbrain.data.AlarmMarkers
import org.alkaline.taskbrain.data.NoteLine
import org.alkaline.taskbrain.dsl.directives.DirectiveFinder
import org.alkaline.taskbrain.dsl.directives.DirectiveInstance
import org.alkaline.taskbrain.dsl.directives.DirectiveResult
import org.alkaline.taskbrain.ui.currentnote.util.AlarmOverlayMapping
import org.alkaline.taskbrain.ui.currentnote.util.AlarmSymbolUtils
import org.alkaline.taskbrain.ui.currentnote.util.SymbolBadge
import org.alkaline.taskbrain.ui.currentnote.util.SymbolOverlay

/**
 * Pure logic extracted from CurrentNoteViewModel for testability.
 * These functions have no Android or ViewModel dependencies.
 */

// ==================== Alarm noteId sync ====================

/**
 * Determines which alarm-to-noteId updates are needed after a save.
 * Returns pairs of (alarmId, newNoteId) for alarms whose noteId is stale.
 */
internal data class AlarmNoteIdUpdate(val alarmId: String, val lineNoteId: String)

internal suspend fun findAlarmNoteIdUpdates(
    trackedLines: List<NoteLine>,
    getAlarmNoteId: suspend (alarmId: String) -> String?
): List<AlarmNoteIdUpdate> {
    val updates = mutableListOf<AlarmNoteIdUpdate>()
    for (line in trackedLines) {
        val lineNoteId = line.noteId ?: continue
        val alarmIds = AlarmSymbolUtils.ALARM_DIRECTIVE_REGEX
            .findAll(line.content)
            .map { it.groupValues[1] }
            .toList()
        for (alarmId in alarmIds) {
            val currentNoteId = getAlarmNoteId(alarmId) ?: continue
            if (currentNoteId != lineNoteId) {
                updates.add(AlarmNoteIdUpdate(alarmId, lineNoteId))
            }
        }
    }
    return updates
}

// ==================== Alarm ID extraction ====================

/**
 * Extracts all distinct alarm IDs from directive markers in tracked lines.
 */
internal fun extractAlarmIds(trackedLines: List<NoteLine>): List<String> =
    trackedLines
        .flatMap { line ->
            AlarmMarkers.ALARM_DIRECTIVE_REGEX.findAll(line.content)
                .map { it.groupValues[1] }
                .toList()
        }
        .distinct()

// ==================== Symbol overlay computation ====================

/**
 * Computes [SymbolOverlay] list for alarm directives in a line.
 * Returns one overlay per directive, in left-to-right order.
 */
internal fun computeSymbolOverlays(
    lineContent: String,
    alarmCache: Map<String, Alarm>,
    now: Timestamp
): List<SymbolOverlay> {
    val alarmIds = AlarmMarkers.ALARM_DIRECTIVE_REGEX.findAll(lineContent)
        .map { it.groupValues[1] }
        .toList()
    if (alarmIds.isEmpty()) return emptyList()

    return alarmIds.map { alarmId ->
        val alarm = alarmCache[alarmId]
        if (alarm != null) {
            AlarmOverlayMapping.alarmToOverlay(alarm, now)
        } else {
            SymbolOverlay(symbol = AlarmMarkers.ALARM_SYMBOL, badge = SymbolBadge.None)
        }
    }
}

// ==================== Directive position ====================

/**
 * A position identifier for a directive: line index and start offset within line content.
 * Used for undo/redo to restore expanded state by matching positions.
 */
data class DirectivePosition(val lineIndex: Int, val startOffset: Int)

// ==================== Directive result mapping ====================

/**
 * Converts UUID-keyed directive results to position-keyed results for UI display.
 */
internal fun mapResultsByPosition(
    instances: List<DirectiveInstance>,
    uuidResults: Map<String, DirectiveResult>
): Map<String, DirectiveResult> {
    val positionResults = mutableMapOf<String, DirectiveResult>()
    for (instance in instances) {
        val result = uuidResults[instance.uuid] ?: continue
        val positionKey = DirectiveFinder.directiveKey(instance.noteId, instance.lineIndex, instance.startOffset)
        positionResults[positionKey] = result
    }
    return positionResults
}

/**
 * Finds positions of all expanded (non-collapsed) directives.
 */
internal fun findExpandedPositions(
    instances: List<DirectiveInstance>,
    results: Map<String, DirectiveResult>
): Set<DirectivePosition> {
    val expandedUuids = results.filter { !it.value.collapsed }.keys
    if (expandedUuids.isEmpty()) return emptySet()

    return instances
        .filter { it.uuid in expandedUuids }
        .map { DirectivePosition(it.lineIndex, it.startOffset) }
        .toSet()
}

/**
 * Merges fresh directive results with existing collapsed state.
 * Preserves the collapsed flag from [currentResults] for each UUID.
 */
internal fun mergeDirectiveResults(
    freshResults: Map<String, DirectiveResult>,
    currentResults: Map<String, DirectiveResult>?
): Map<String, DirectiveResult> {
    return freshResults.mapValues { (uuid, result) ->
        val existingCollapsed = currentResults?.get(uuid)?.collapsed ?: true
        result.copy(collapsed = existingCollapsed)
    }
}

// ==================== Note ID resolution ====================

/**
 * Resolves noteId conflicts at save time.
 *
 * When multiple lines claim the same noteId (e.g., after a split), the line with
 * the most content (excluding prefix) keeps the noteId. Others get null, causing
 * a new Firestore document to be created.
 *
 * For lines with multiple noteIds (from merges), the first (primary) noteId is used.
 */
internal fun resolveNoteIds(
    contentLines: List<String>,
    lineNoteIds: List<List<String>>
): List<NoteLine> {
    data class LineCandidate(val index: Int, val noteId: String, val contentLength: Int)

    val candidates = contentLines.mapIndexedNotNull { index, text ->
        val primaryId = lineNoteIds.getOrElse(index) { emptyList() }.firstOrNull()
            ?: return@mapIndexedNotNull null
        val contentLength = LineState.extractPrefix(text).let { prefix ->
            text.substring(prefix.length).length
        }
        LineCandidate(index, primaryId, contentLength)
    }

    val noteIdWinner = candidates
        .groupBy { it.noteId }
        .mapValues { (_, candidates) -> candidates.maxByOrNull { it.contentLength }!!.index }

    return contentLines.mapIndexed { index, text ->
        val ids = lineNoteIds.getOrElse(index) { emptyList() }
        val primaryId = ids.firstOrNull()
        val resolvedId = if (primaryId != null && noteIdWinner[primaryId] == index) {
            primaryId
        } else {
            null
        }
        NoteLine(text, resolvedId)
    }
}
