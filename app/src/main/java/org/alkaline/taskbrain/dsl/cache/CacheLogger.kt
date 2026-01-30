package org.alkaline.taskbrain.dsl.cache

import android.util.Log

/**
 * Logs cache statistics periodically.
 *
 * Phase 5: Cache architecture.
 *
 * Logs cache usage every [logIntervalMs] milliseconds when [logStats] is called.
 * Intended to be called from a periodic task or coroutine.
 */
class CacheLogger(
    private val cacheManager: DirectiveCacheManager,
    private val logIntervalMs: Long = DEFAULT_LOG_INTERVAL_MS,
    private val tag: String = DEFAULT_TAG
) {
    private var lastLogTime: Long = 0

    /**
     * Log cache statistics if enough time has passed since the last log.
     *
     * Call this periodically (e.g., from a timer or when opening notes).
     * It will only actually log if [logIntervalMs] has passed since the last log.
     *
     * @param forceLog If true, log regardless of time elapsed
     */
    fun logStats(forceLog: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!forceLog && (now - lastLogTime) < logIntervalMs) {
            return
        }

        lastLogTime = now
        val stats = cacheManager.stats()
        Log.i(tag, stats.toLogString())
    }

    /**
     * Log cache statistics with additional context.
     *
     * @param context Additional context to include in the log
     */
    fun logStatsWithContext(context: String, forceLog: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!forceLog && (now - lastLogTime) < logIntervalMs) {
            return
        }

        lastLogTime = now
        val stats = cacheManager.stats()
        Log.i(tag, "[$context] ${stats.toLogString()}")
    }

    companion object {
        const val DEFAULT_LOG_INTERVAL_MS = 10 * 60 * 1000L  // 10 minutes
        const val DEFAULT_TAG = "DirectiveCache"
    }
}

/**
 * Extension function for CombinedCacheStats to format as detailed log lines.
 */
fun CombinedCacheStats.toDetailedLogString(): String {
    return buildString {
        appendLine("=== Directive Cache Statistics ===")
        appendLine("Global Cache:")
        appendLine("  Size: ${global.size}/${global.maxSize}")
        appendLine("  Utilization: ${global.utilizationPercent}%")
        appendLine("Per-Note Cache:")
        appendLine("  Notes tracked: ${perNote.noteCount}/${perNote.maxNotes}")
        appendLine("  Total entries: ${perNote.totalEntries}")
        appendLine("  Max per note: ${perNote.maxEntriesPerNote}")
        appendLine("  Utilization: ${perNote.utilizationPercent}%")
        append("================================")
    }
}
