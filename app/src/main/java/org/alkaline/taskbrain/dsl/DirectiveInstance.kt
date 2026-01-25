package org.alkaline.taskbrain.dsl

import java.util.UUID

/**
 * Represents a directive instance with a stable UUID identity.
 *
 * UUIDs persist across text edits, allowing cached results to be maintained
 * even when the directive's position changes (e.g., when text is inserted before it).
 *
 * @property uuid Unique identifier for this directive instance
 * @property lineIndex Current line index (0-based)
 * @property startOffset Character offset within the line content where directive starts
 * @property sourceText The directive source text including brackets (e.g., "[now]")
 */
data class DirectiveInstance(
    val uuid: String,
    val lineIndex: Int,
    val startOffset: Int,
    val sourceText: String
) {
    companion object {
        /**
         * Creates a new directive instance with a fresh UUID.
         */
        fun create(lineIndex: Int, startOffset: Int, sourceText: String): DirectiveInstance {
            return DirectiveInstance(
                uuid = UUID.randomUUID().toString(),
                lineIndex = lineIndex,
                startOffset = startOffset,
                sourceText = sourceText
            )
        }
    }
}

/**
 * Matches new directive locations to existing instances, preserving UUIDs where possible.
 *
 * Matching algorithm (in priority order):
 * 1. Exact match: same line, same offset, same text -> reuse UUID
 * 2. Same line shift: same line, same text, different offset -> reuse UUID (text shifted)
 * 3. Line move: different line, same text, unique match -> reuse UUID (line was moved)
 * 4. No match: generate new UUID
 *
 * @param existingInstances Current directive instances with their UUIDs
 * @param newDirectives Newly parsed directives from content
 * @return Updated list of directive instances with stable UUIDs
 */
fun matchDirectiveInstances(
    existingInstances: List<DirectiveInstance>,
    newDirectives: List<ParsedDirectiveLocation>
): List<DirectiveInstance> {
    if (newDirectives.isEmpty()) return emptyList()
    if (existingInstances.isEmpty()) {
        // All new - generate fresh UUIDs
        return newDirectives.map { DirectiveInstance.create(it.lineIndex, it.startOffset, it.sourceText) }
    }

    val result = mutableListOf<DirectiveInstance>()
    val usedExisting = mutableSetOf<String>() // UUIDs that have been matched
    val unmatchedNew = newDirectives.toMutableList()

    // Pass 1: Exact match (same line, same offset, same text)
    val exactMatches = mutableListOf<Pair<ParsedDirectiveLocation, DirectiveInstance>>()
    for (newDir in unmatchedNew.toList()) {
        val exact = existingInstances.find { existing ->
            existing.uuid !in usedExisting &&
            existing.lineIndex == newDir.lineIndex &&
            existing.startOffset == newDir.startOffset &&
            existing.sourceText == newDir.sourceText
        }
        if (exact != null) {
            exactMatches.add(newDir to exact)
            usedExisting.add(exact.uuid)
            unmatchedNew.remove(newDir)
        }
    }
    result.addAll(exactMatches.map { (newDir, existing) ->
        existing.copy(lineIndex = newDir.lineIndex, startOffset = newDir.startOffset)
    })

    // Pass 2: Same line, same text, different offset (text shifted on same line)
    val sameLineMatches = mutableListOf<Pair<ParsedDirectiveLocation, DirectiveInstance>>()
    for (newDir in unmatchedNew.toList()) {
        val sameLine = existingInstances.find { existing ->
            existing.uuid !in usedExisting &&
            existing.lineIndex == newDir.lineIndex &&
            existing.sourceText == newDir.sourceText
        }
        if (sameLine != null) {
            sameLineMatches.add(newDir to sameLine)
            usedExisting.add(sameLine.uuid)
            unmatchedNew.remove(newDir)
        }
    }
    result.addAll(sameLineMatches.map { (newDir, existing) ->
        existing.copy(lineIndex = newDir.lineIndex, startOffset = newDir.startOffset)
    })

    // Pass 3: Different line, same text, unique candidate (line was moved)
    for (newDir in unmatchedNew.toList()) {
        val candidates = existingInstances.filter { existing ->
            existing.uuid !in usedExisting &&
            existing.sourceText == newDir.sourceText
        }
        // Only match if there's exactly one candidate (avoid ambiguity)
        if (candidates.size == 1) {
            val match = candidates.first()
            result.add(match.copy(lineIndex = newDir.lineIndex, startOffset = newDir.startOffset))
            usedExisting.add(match.uuid)
            unmatchedNew.remove(newDir)
        }
    }

    // Pass 4: Remaining unmatched get new UUIDs
    result.addAll(unmatchedNew.map {
        DirectiveInstance.create(it.lineIndex, it.startOffset, it.sourceText)
    })

    return result
}

/**
 * Simple data class for a parsed directive location (before UUID assignment).
 */
data class ParsedDirectiveLocation(
    val lineIndex: Int,
    val startOffset: Int,
    val sourceText: String
)

/**
 * Parses all directives from content and returns their locations.
 */
fun parseAllDirectiveLocations(content: String): List<ParsedDirectiveLocation> {
    val result = mutableListOf<ParsedDirectiveLocation>()
    content.lines().forEachIndexed { lineIndex, lineContent ->
        val directives = DirectiveFinder.findDirectives(lineContent)
        for (directive in directives) {
            result.add(ParsedDirectiveLocation(lineIndex, directive.startOffset, directive.sourceText))
        }
    }
    return result
}
