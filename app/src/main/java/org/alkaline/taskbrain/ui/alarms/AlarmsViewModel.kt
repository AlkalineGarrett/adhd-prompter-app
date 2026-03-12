package org.alkaline.taskbrain.ui.alarms

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import com.google.firebase.Timestamp
import java.util.Calendar
import org.alkaline.taskbrain.data.Alarm
import org.alkaline.taskbrain.data.AlarmRepository
import org.alkaline.taskbrain.data.AlarmUpdateEvent
import org.alkaline.taskbrain.service.AlarmStateManager

class AlarmsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AlarmRepository()
    private val alarmStateManager = AlarmStateManager(application)

    private val _pastDueAlarms = MutableLiveData<List<Alarm>>(emptyList())
    val pastDueAlarms: LiveData<List<Alarm>> = _pastDueAlarms

    private val _upcomingAlarms = MutableLiveData<List<Alarm>>(emptyList())
    val upcomingAlarms: LiveData<List<Alarm>> = _upcomingAlarms

    private val _laterAlarms = MutableLiveData<List<Alarm>>(emptyList())
    val laterAlarms: LiveData<List<Alarm>> = _laterAlarms

    private val _completedAlarms = MutableLiveData<List<Alarm>>(emptyList())
    val completedAlarms: LiveData<List<Alarm>> = _completedAlarms

    private val _cancelledAlarms = MutableLiveData<List<Alarm>>(emptyList())
    val cancelledAlarms: LiveData<List<Alarm>> = _cancelledAlarms

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<Throwable?>(null)
    val error: LiveData<Throwable?> = _error

    init {
        loadAlarms()

        // Observe alarm updates from external sources (e.g., notification actions)
        AlarmUpdateEvent.updates
            .onEach { loadAlarms() }
            .launchIn(viewModelScope)
    }

    fun loadAlarms() {
        _isLoading.value = true
        _error.value = null

        viewModelScope.launch {
            // Load all alarm categories
            val pendingResult = repository.getPendingAlarms()
            val completedResult = repository.getCompletedAlarms()
            val cancelledResult = repository.getCancelledAlarms()

            var firstError: Throwable? = null

            pendingResult.fold(
                onSuccess = { alarms ->
                    val now = Timestamp.now()
                    val endOfToday = endOfDay(now)

                    val pastDue = mutableListOf<Alarm>()
                    val upcoming = mutableListOf<Alarm>()
                    val later = mutableListOf<Alarm>()

                    for (alarm in alarms) {
                        val earliest = alarm.earliestThresholdTime
                        when {
                            isPastDue(alarm, now) -> pastDue.add(alarm)
                            earliest != null && earliest <= endOfToday -> upcoming.add(alarm)
                            else -> later.add(alarm)
                        }
                    }

                    _pastDueAlarms.value = pastDue.sortedBy { it.latestThresholdTime?.toDate()?.time }
                    _upcomingAlarms.value = upcoming.sortedBy { it.earliestThresholdTime?.toDate()?.time }
                    _laterAlarms.value = later.sortedBy { it.earliestThresholdTime?.toDate()?.time }
                },
                onFailure = {
                    Log.e(TAG, "Error loading pending alarms", it)
                    if (firstError == null) firstError = it
                }
            )

            completedResult.fold(
                onSuccess = { _completedAlarms.value = it },
                onFailure = {
                    Log.e(TAG, "Error loading completed alarms", it)
                    if (firstError == null) firstError = it
                }
            )

            cancelledResult.fold(
                onSuccess = { _cancelledAlarms.value = it },
                onFailure = {
                    Log.e(TAG, "Error loading cancelled alarms", it)
                    if (firstError == null) firstError = it
                }
            )

            firstError?.let { _error.value = it }
            _isLoading.value = false
        }
    }

    fun markDone(alarmId: String) = executeAlarmOperation { alarmStateManager.markDone(alarmId) }

    fun markCancelled(alarmId: String) = executeAlarmOperation { alarmStateManager.markCancelled(alarmId) }

    fun deleteAlarm(alarmId: String) = executeAlarmOperation { alarmStateManager.delete(alarmId) }

    fun reactivateAlarm(alarmId: String) = executeAlarmOperation { alarmStateManager.reactivate(alarmId) }

    fun updateAlarm(
        alarm: Alarm,
        upcomingTime: com.google.firebase.Timestamp?,
        notifyTime: com.google.firebase.Timestamp?,
        urgentTime: com.google.firebase.Timestamp?,
        alarmTime: com.google.firebase.Timestamp?
    ) = executeAlarmOperation {
        alarmStateManager.update(alarm, upcomingTime, notifyTime, urgentTime, alarmTime).also { result ->
            result.getOrNull()?.let { scheduleResult ->
                if (!scheduleResult.success) {
                    Log.w(TAG, "Alarm scheduling warning: ${scheduleResult.message}")
                }
            }
        }
    }

    private fun executeAlarmOperation(operation: suspend () -> Result<*>) {
        viewModelScope.launch {
            operation().fold(
                onSuccess = { loadAlarms() },
                onFailure = { _error.value = it }
            )
        }
    }

    fun clearError() {
        _error.value = null
    }

    companion object {
        private const val TAG = "AlarmsViewModel"

        /**
         * An alarm is past due when its latest configured threshold time is in the past.
         */
        internal fun isPastDue(alarm: Alarm, now: Timestamp): Boolean {
            val latest = alarm.latestThresholdTime ?: return false
            return latest < now
        }

        /**
         * Returns a Timestamp for the end of the day (23:59:59.999) of the given timestamp.
         */
        internal fun endOfDay(timestamp: Timestamp): Timestamp {
            val cal = Calendar.getInstance().apply {
                time = timestamp.toDate()
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            }
            return Timestamp(cal.time)
        }
    }
}
