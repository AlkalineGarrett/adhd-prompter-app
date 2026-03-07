package org.alkaline.taskbrain.ui.schedules

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.alkaline.taskbrain.dsl.directives.EnrichedExecution
import org.alkaline.taskbrain.dsl.directives.Schedule
import org.alkaline.taskbrain.dsl.directives.ScheduleManager

/**
 * UI state for the Schedules screen.
 */
data class SchedulesUiState(
    /** Executions from the last 24 hours (completed or failed), enriched with schedule data. */
    val last24Hours: List<EnrichedExecution> = emptyList(),
    /** Schedules due in the next 24 hours. */
    val next24Hours: List<Schedule> = emptyList(),
    /** Missed executions pending user review, enriched with schedule data. */
    val missed: List<EnrichedExecution> = emptyList(),
    /** IDs of missed executions selected for manual run (all selected by default). */
    val selectedMissedIds: Set<String> = emptySet(),
    /** Whether data is being loaded. */
    val isLoading: Boolean = false,
    /** Whether selected missed schedules are being executed. */
    val isRunning: Boolean = false,
    /** Error message, if any. */
    val error: String? = null
)

/**
 * ViewModel for the Schedules screen.
 *
 * Manages state for three tabs:
 * - Last 24 Hours: History of executed schedules
 * - Next 24 Hours: Upcoming schedules
 * - Missed: Schedules that were too late to auto-execute
 */
class SchedulesViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(SchedulesUiState())
    val uiState: StateFlow<SchedulesUiState> = _uiState.asStateFlow()

    init {
        loadAllTabs()
    }

    /**
     * Load data for all three tabs.
     * Note: Does not clear existing errors - use clearError() explicitly.
     */
    fun loadAllTabs() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            try {
                val last24Hours = ScheduleManager.getEnrichedExecutionsLast24Hours()
                val next24Hours = ScheduleManager.getSchedulesForNext24Hours()
                val missed = ScheduleManager.getEnrichedMissedExecutions()

                // Preserve existing selections for items that still exist
                val existingMissedIds = missed.map { it.id }.toSet()
                val preservedSelections = _uiState.value.selectedMissedIds.intersect(existingMissedIds)
                // If no preserved selections (first load or all cleared), select all by default
                val newSelections = if (preservedSelections.isEmpty() && missed.isNotEmpty()) {
                    existingMissedIds
                } else {
                    preservedSelections
                }

                _uiState.update {
                    it.copy(
                        last24Hours = last24Hours,
                        next24Hours = next24Hours,
                        missed = missed,
                        selectedMissedIds = newSelections,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading schedules", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load schedules"
                    )
                }
            }
        }
    }

    /**
     * Toggle selection of a missed execution.
     */
    fun toggleSelection(executionId: String) {
        _uiState.update { state ->
            val newSelection = if (executionId in state.selectedMissedIds) {
                state.selectedMissedIds - executionId
            } else {
                state.selectedMissedIds + executionId
            }
            state.copy(selectedMissedIds = newSelection)
        }
    }

    /**
     * Select all missed executions.
     */
    fun selectAll() {
        _uiState.update { state ->
            state.copy(selectedMissedIds = state.missed.map { it.id }.toSet())
        }
    }

    /**
     * Deselect all missed executions.
     */
    fun deselectAll() {
        _uiState.update { it.copy(selectedMissedIds = emptySet()) }
    }

    /**
     * Run all selected missed executions.
     */
    fun runSelectedMissed() {
        val state = _uiState.value
        if (state.selectedMissedIds.isEmpty() || state.isRunning) return

        viewModelScope.launch {
            _uiState.update { it.copy(isRunning = true) }

            var hasError = false
            var lastError: String? = null

            for (executionId in state.selectedMissedIds) {
                val result = ScheduleManager.executeScheduleNow(executionId)
                if (result.isFailure) {
                    hasError = true
                    lastError = result.exceptionOrNull()?.message
                    Log.e(TAG, "Failed to execute schedule: $executionId", result.exceptionOrNull())
                }
            }

            // Reload all tabs to reflect the changes
            try {
                val last24Hours = ScheduleManager.getEnrichedExecutionsLast24Hours()
                val next24Hours = ScheduleManager.getSchedulesForNext24Hours()
                val missed = ScheduleManager.getEnrichedMissedExecutions()

                // Preserve selections for items that still exist
                val existingMissedIds = missed.map { it.id }.toSet()
                val preservedSelections = state.selectedMissedIds.intersect(existingMissedIds)

                _uiState.update {
                    it.copy(
                        last24Hours = last24Hours,
                        next24Hours = next24Hours,
                        missed = missed,
                        selectedMissedIds = preservedSelections,
                        isRunning = false,
                        error = if (hasError) lastError else it.error
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error reloading after run", e)
                _uiState.update {
                    it.copy(
                        isRunning = false,
                        error = e.message ?: "Failed to reload schedules"
                    )
                }
            }
        }
    }

    /**
     * Dismiss all selected missed executions without running them.
     */
    fun dismissSelectedMissed() {
        val state = _uiState.value
        if (state.selectedMissedIds.isEmpty() || state.isRunning) return

        viewModelScope.launch {
            _uiState.update { it.copy(isRunning = true) }

            var hasError = false
            var lastError: String? = null

            for (executionId in state.selectedMissedIds) {
                val result = ScheduleManager.dismissMissedExecution(executionId)
                if (result.isFailure) {
                    hasError = true
                    lastError = result.exceptionOrNull()?.message
                    Log.e(TAG, "Failed to dismiss execution: $executionId", result.exceptionOrNull())
                }
            }

            // Reload to reflect the changes
            try {
                val last24Hours = ScheduleManager.getEnrichedExecutionsLast24Hours()
                val next24Hours = ScheduleManager.getSchedulesForNext24Hours()
                val missed = ScheduleManager.getEnrichedMissedExecutions()

                // Preserve selections for items that still exist
                val existingMissedIds = missed.map { it.id }.toSet()
                val preservedSelections = state.selectedMissedIds.intersect(existingMissedIds)

                _uiState.update {
                    it.copy(
                        last24Hours = last24Hours,
                        next24Hours = next24Hours,
                        missed = missed,
                        selectedMissedIds = preservedSelections,
                        isRunning = false,
                        error = if (hasError) lastError else it.error
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error reloading after dismiss", e)
                _uiState.update {
                    it.copy(
                        isRunning = false,
                        error = e.message ?: "Failed to reload schedules"
                    )
                }
            }
        }
    }

    /**
     * Clear the error state.
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    companion object {
        private const val TAG = "SchedulesViewModel"
    }
}
