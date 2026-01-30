package org.alkaline.taskbrain.dsl.cache

import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Schedules cache invalidations based on refresh triggers.
 *
 * Phase 10: Time trigger scheduler.
 *
 * This scheduler:
 * 1. Tracks refresh directives and their computed triggers
 * 2. Monitors time and fires callbacks when triggers match
 * 3. Integrates with CachedDirectiveExecutor to invalidate stale caches
 *
 * Usage:
 * ```kotlin
 * val scheduler = RefreshScheduler { cacheKey, noteId ->
 *     cacheManager.clear(cacheKey, noteId)
 * }
 *
 * // Register a refresh directive
 * scheduler.register(
 *     cacheKey = "directive-hash",
 *     noteId = "note-123",
 *     triggers = listOf(DailyTimeTrigger(LocalTime.of(12, 0)))
 * )
 *
 * // Start monitoring
 * scheduler.start()
 *
 * // Stop when done
 * scheduler.stop()
 * ```
 */
class RefreshScheduler(
    private val onTrigger: (cacheKey: String, noteId: String?) -> Unit,
    private val checkIntervalSeconds: Long = 60
) {
    /**
     * Registered refresh directive with its triggers.
     */
    data class RegisteredRefresh(
        val cacheKey: String,
        val noteId: String?,
        val triggers: List<TimeTrigger>
    )

    /** Registered refresh directives by cache key */
    private val registrations = ConcurrentHashMap<String, RegisteredRefresh>()

    /** Scheduler for periodic checks */
    private var scheduler: ScheduledExecutorService? = null
    private var scheduledTask: ScheduledFuture<*>? = null

    /** Last check time for tracking what's been processed */
    @Volatile
    private var lastCheckTime: LocalDateTime = LocalDateTime.now()

    /** Whether the scheduler is running */
    @Volatile
    private var isRunning = false

    /**
     * Register a refresh directive for scheduling.
     *
     * @param cacheKey The cache key for the directive
     * @param noteId The note containing the directive (null for global)
     * @param triggers The computed triggers from RefreshTriggerAnalyzer
     */
    fun register(cacheKey: String, noteId: String?, triggers: List<TimeTrigger>) {
        if (triggers.isEmpty()) return

        registrations[cacheKey] = RegisteredRefresh(cacheKey, noteId, triggers)
    }

    /**
     * Unregister a refresh directive.
     */
    fun unregister(cacheKey: String) {
        registrations.remove(cacheKey)
    }

    /**
     * Unregister all directives for a note.
     */
    fun unregisterNote(noteId: String) {
        registrations.entries.removeIf { it.value.noteId == noteId }
    }

    /**
     * Clear all registrations.
     */
    fun clearAll() {
        registrations.clear()
    }

    /**
     * Start the scheduler.
     * Begins periodic checks for triggers.
     */
    fun start() {
        if (isRunning) return

        isRunning = true
        lastCheckTime = LocalDateTime.now()

        scheduler = Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "RefreshScheduler").apply { isDaemon = true }
        }

        scheduledTask = scheduler?.scheduleAtFixedRate(
            { checkTriggers() },
            checkIntervalSeconds,
            checkIntervalSeconds,
            TimeUnit.SECONDS
        )
    }

    /**
     * Stop the scheduler.
     */
    fun stop() {
        isRunning = false
        scheduledTask?.cancel(false)
        scheduledTask = null
        scheduler?.shutdown()
        scheduler = null
    }

    /**
     * Check all triggers and fire invalidations for those that triggered.
     */
    private fun checkTriggers() {
        if (!isRunning) return

        val now = LocalDateTime.now()
        val checkFrom = lastCheckTime
        lastCheckTime = now

        // Find all registrations with triggers that fired since last check
        for (registration in registrations.values) {
            val triggered = registration.triggers.any { trigger ->
                shouldHaveTriggered(trigger, checkFrom, now)
            }

            if (triggered) {
                try {
                    onTrigger(registration.cacheKey, registration.noteId)
                } catch (e: Exception) {
                    // Log and continue - don't let one failure stop others
                }

                // For one-time triggers, unregister after firing
                val hasRecurring = registration.triggers.any { it.isRecurring }
                if (!hasRecurring) {
                    // All triggers are one-time and have fired
                    val allFired = registration.triggers.all { trigger ->
                        trigger.nextTriggerAfter(now) == null
                    }
                    if (allFired) {
                        registrations.remove(registration.cacheKey)
                    }
                }
            }
        }
    }

    /**
     * Check if a trigger should have fired between two times.
     */
    private fun shouldHaveTriggered(
        trigger: TimeTrigger,
        from: LocalDateTime,
        to: LocalDateTime
    ): Boolean {
        val nextTrigger = trigger.nextTriggerAfter(from) ?: return false
        return !nextTrigger.isAfter(to)
    }

    /**
     * Force an immediate check (useful for testing).
     */
    fun checkNow() {
        checkTriggers()
    }

    /**
     * Get the next trigger time across all registrations.
     * Returns null if no future triggers.
     */
    fun nextTriggerTime(): LocalDateTime? {
        val now = LocalDateTime.now()
        return registrations.values
            .flatMap { it.triggers }
            .mapNotNull { it.nextTriggerAfter(now) }
            .minOrNull()
    }

    /**
     * Get the number of registered refresh directives.
     */
    fun registrationCount(): Int = registrations.size

    /**
     * Get all registered cache keys.
     */
    fun registeredKeys(): Set<String> = registrations.keys.toSet()
}

/**
 * Integration helper that connects RefreshScheduler to CachedDirectiveExecutor.
 */
class RefreshAwareExecutor(
    private val executor: CachedDirectiveExecutor,
    private val scheduler: RefreshScheduler
) {
    /**
     * Execute a directive and register any refresh triggers.
     */
    fun executeWithRefreshTracking(
        sourceText: String,
        notes: List<org.alkaline.taskbrain.data.Note>,
        currentNote: org.alkaline.taskbrain.data.Note?,
        noteOperations: org.alkaline.taskbrain.dsl.runtime.NoteOperations? = null,
        viewStack: List<String> = emptyList()
    ): CachedDirectiveExecutor.CachedExecutionResult {
        val result = executor.execute(sourceText, notes, currentNote, noteOperations, viewStack)

        // If this is a refresh directive, analyze and register triggers
        if (sourceText.contains("refresh[")) {
            registerRefreshTriggers(sourceText, currentNote?.id)
        }

        return result
    }

    private fun registerRefreshTriggers(sourceText: String, noteId: String?) {
        try {
            val tokens = org.alkaline.taskbrain.dsl.language.Lexer(sourceText).tokenize()
            val directive = org.alkaline.taskbrain.dsl.language.Parser(tokens, sourceText).parseDirective()

            // Find RefreshExpr in the AST
            val refreshExpr = findRefreshExpr(directive.expression)
            if (refreshExpr != null) {
                val analysis = RefreshTriggerAnalyzer.analyze(refreshExpr)
                if (analysis.success && analysis.triggers.isNotEmpty()) {
                    val cacheKey = org.alkaline.taskbrain.dsl.directives.DirectiveResult.hashDirective(sourceText)
                    scheduler.register(cacheKey, noteId, analysis.triggers)
                }
            }
        } catch (e: Exception) {
            // Ignore analysis errors - directive will still execute
        }
    }

    private fun findRefreshExpr(expr: org.alkaline.taskbrain.dsl.language.Expression): org.alkaline.taskbrain.dsl.language.RefreshExpr? {
        return when (expr) {
            is org.alkaline.taskbrain.dsl.language.RefreshExpr -> expr
            is org.alkaline.taskbrain.dsl.language.StatementList -> {
                expr.statements.asSequence()
                    .mapNotNull { findRefreshExpr(it) }
                    .firstOrNull()
            }
            is org.alkaline.taskbrain.dsl.language.Assignment -> findRefreshExpr(expr.value)
            else -> null
        }
    }
}
