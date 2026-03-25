package org.alkaline.taskbrain.dsl.cache

import android.util.Log

/**
 * Logs cache statistics periodically.
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

    fun logStats(forceLog: Boolean = false) {
        logIfDue(forceLog) { stats -> formatStats(stats) }
    }

    fun logStatsWithContext(context: String, forceLog: Boolean = false) {
        logIfDue(forceLog) { stats -> "[$context] ${formatStats(stats)}" }
    }

    private inline fun logIfDue(forceLog: Boolean, message: (PerNoteCacheStats) -> String) {
        val now = System.currentTimeMillis()
        if (!forceLog && (now - lastLogTime) < logIntervalMs) return
        lastLogTime = now
        Log.i(tag, message(cacheManager.stats()))
    }

    private fun formatStats(stats: PerNoteCacheStats): String =
        "Per-note: ${stats.utilizationPercent}% used (${stats.totalEntries} entries across ${stats.noteCount} notes)"

    companion object {
        const val DEFAULT_LOG_INTERVAL_MS = 10 * 60 * 1000L  // 10 minutes
        const val DEFAULT_TAG = "DirectiveCache"
    }
}
