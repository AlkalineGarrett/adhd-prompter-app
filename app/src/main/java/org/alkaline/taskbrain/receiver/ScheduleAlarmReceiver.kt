package org.alkaline.taskbrain.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.alkaline.taskbrain.data.NoteRepository
import org.alkaline.taskbrain.dsl.directives.ScheduleExecutionRepository
import org.alkaline.taskbrain.dsl.directives.ScheduleRepository
import org.alkaline.taskbrain.dsl.language.Lexer
import org.alkaline.taskbrain.dsl.language.Parser
import org.alkaline.taskbrain.dsl.runtime.Environment
import org.alkaline.taskbrain.dsl.runtime.Executor
import org.alkaline.taskbrain.dsl.runtime.NoteContext
import org.alkaline.taskbrain.dsl.runtime.NoteRepositoryOperations
import org.alkaline.taskbrain.dsl.runtime.values.ScheduleVal
import org.alkaline.taskbrain.dsl.runtime.values.UndefinedVal

/**
 * BroadcastReceiver for precise schedule alarms.
 *
 * When a precise schedule's alarm fires, this receiver executes the schedule action
 * and records the execution result.
 */
class ScheduleAlarmReceiver : BroadcastReceiver() {

    private val scheduleRepository = ScheduleRepository()
    private val executionRepository = ScheduleExecutionRepository()
    private val noteRepository = NoteRepository()

    override fun onReceive(context: Context, intent: Intent) {
        val scheduleId = intent.getStringExtra(EXTRA_SCHEDULE_ID) ?: run {
            Log.e(TAG, "No schedule ID in alarm intent")
            return
        }

        Log.d(TAG, "Precise alarm triggered for schedule: $scheduleId")

        // Use goAsync() to allow coroutine execution
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                executeSchedule(scheduleId)
            } catch (e: Exception) {
                Log.e(TAG, "Error executing precise schedule: $scheduleId", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun executeSchedule(scheduleId: String) {
        val auth = FirebaseAuth.getInstance()
        val userId = auth.currentUser?.uid ?: run {
            Log.w(TAG, "User not signed in, cannot execute schedule")
            return
        }

        // Get the schedule
        val schedule = scheduleRepository.getSchedule(scheduleId).getOrNull() ?: run {
            Log.e(TAG, "Schedule not found: $scheduleId")
            return
        }

        val scheduledFor = schedule.nextExecution ?: run {
            Log.e(TAG, "Schedule has no nextExecution: $scheduleId")
            return
        }

        // Check if we're within the late threshold (15 minutes)
        val now = System.currentTimeMillis()
        val scheduledTime = scheduledFor.toDate().time
        val delayMs = now - scheduledTime

        if (delayMs > LATE_THRESHOLD_MS) {
            // Too late - mark as missed
            Log.d(TAG, "Precise schedule $scheduleId is ${delayMs / 60000} min late, marking as missed")
            executionRepository.recordMissed(scheduleId, scheduledFor)
            scheduleRepository.advanceNextExecution(scheduleId)
            // Reschedule the next alarm
            rescheduleAlarm(schedule.id)
            return
        }

        // Execute the schedule
        try {
            // Load notes for the execution context
            val notesResult = noteRepository.loadAllUserNotes()
            val notes = notesResult.getOrNull() ?: emptyList()

            // Load the note containing this schedule for currentNote context
            val currentNote = if (schedule.noteId.isNotBlank()) {
                noteRepository.loadNoteById(schedule.noteId).getOrNull()
            } else {
                null
            }

            // Create note operations for mutations
            val db = FirebaseFirestore.getInstance()
            val noteOperations = NoteRepositoryOperations(db, userId)

            // Parse the directive to get the ScheduleVal
            val tokens = Lexer(schedule.directiveSource).tokenize()
            val directive = Parser(tokens, schedule.directiveSource).parseDirective()

            // Create execution environment
            val context = NoteContext(
                notes = notes,
                currentNote = currentNote,
                noteOperations = noteOperations
            )
            val env = Environment(context)

            // Execute the directive to get the ScheduleVal
            val executor = Executor()
            val result = executor.execute(directive, env)

            if (result !is ScheduleVal) {
                throw IllegalStateException("Directive did not produce ScheduleVal: ${result::class.simpleName}")
            }

            // Execute the action lambda
            val action = result.action
            val actionResult = executor.invokeLambda(action, listOf(UndefinedVal))
            Log.d(TAG, "Precise schedule action executed: ${actionResult.typeName}")

            // Record successful execution
            executionRepository.recordExecution(
                scheduleId = scheduleId,
                scheduledFor = scheduledFor,
                success = true,
                manualRun = false
            )
            scheduleRepository.markExecuted(scheduleId, success = true)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to execute precise schedule: $scheduleId", e)
            executionRepository.recordExecution(
                scheduleId = scheduleId,
                scheduledFor = scheduledFor,
                success = false,
                error = e.message,
                manualRun = false
            )
            scheduleRepository.markExecuted(scheduleId, success = false, error = e.message)
        }

        // Reschedule the next alarm
        rescheduleAlarm(scheduleId)
    }

    private suspend fun rescheduleAlarm(scheduleId: String) {
        // The schedule's nextExecution has been updated by markExecuted/advanceNextExecution
        // ScheduleManager will handle rescheduling when the app is next active
        // For now, we rely on ScheduleManager.schedulePreciseAlarm being called
        // This could be enhanced to directly reschedule here
        Log.d(TAG, "Schedule $scheduleId needs rescheduling (handled by ScheduleManager)")
    }

    companion object {
        private const val TAG = "ScheduleAlarmReceiver"
        const val ACTION_SCHEDULE_ALARM = "org.alkaline.taskbrain.ACTION_SCHEDULE_ALARM"
        const val EXTRA_SCHEDULE_ID = "schedule_id"

        /**
         * Maximum delay in milliseconds before a schedule is marked as missed.
         */
        private const val LATE_THRESHOLD_MS = 15 * 60 * 1000L
    }
}
