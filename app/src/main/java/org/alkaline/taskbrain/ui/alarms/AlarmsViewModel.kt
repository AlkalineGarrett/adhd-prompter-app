package org.alkaline.taskbrain.ui.alarms

import android.app.Application
import android.app.NotificationManager
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.alkaline.taskbrain.data.Alarm
import org.alkaline.taskbrain.data.AlarmRepository
import org.alkaline.taskbrain.data.AlarmUpdateEvent
import org.alkaline.taskbrain.service.AlarmScheduler
import org.alkaline.taskbrain.service.AlarmUtils
import org.alkaline.taskbrain.service.LockScreenWallpaperManager

class AlarmsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AlarmRepository()
    private val alarmScheduler = AlarmScheduler(application)
    private val notificationManager = application.getSystemService(NotificationManager::class.java)
    private val lockScreenWallpaperManager = LockScreenWallpaperManager(application)

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
            // Load all alarm categories in parallel
            val upcomingResult = repository.getUpcomingAlarms()
            val laterResult = repository.getLaterAlarms()
            val completedResult = repository.getCompletedAlarms()
            val cancelledResult = repository.getCancelledAlarms()

            // Track first error encountered to show to user
            var firstError: Throwable? = null

            upcomingResult.fold(
                onSuccess = { _upcomingAlarms.value = it },
                onFailure = {
                    Log.e(TAG, "Error loading upcoming alarms", it)
                    if (firstError == null) firstError = it
                }
            )

            laterResult.fold(
                onSuccess = { _laterAlarms.value = it },
                onFailure = {
                    Log.e(TAG, "Error loading later alarms", it)
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

            // Show error dialog if any load failed
            firstError?.let { _error.value = it }

            _isLoading.value = false
        }
    }

    fun markDone(alarmId: String) {
        viewModelScope.launch {
            repository.markDone(alarmId).fold(
                onSuccess = {
                    Log.d(TAG, "Alarm marked done: $alarmId")
                    // Cancel any scheduled triggers
                    alarmScheduler.cancelAlarm(alarmId)
                    // Restore lock screen wallpaper if this alarm set it
                    lockScreenWallpaperManager.restoreWallpaper(alarmId)
                    // Dismiss any existing notification
                    dismissNotification(alarmId)
                    loadAlarms()
                },
                onFailure = {
                    Log.e(TAG, "Error marking alarm done", it)
                    _error.value = it
                }
            )
        }
    }

    fun markCancelled(alarmId: String) {
        viewModelScope.launch {
            repository.markCancelled(alarmId).fold(
                onSuccess = {
                    Log.d(TAG, "Alarm marked cancelled: $alarmId")
                    // Cancel any scheduled triggers
                    alarmScheduler.cancelAlarm(alarmId)
                    // Restore lock screen wallpaper if this alarm set it
                    lockScreenWallpaperManager.restoreWallpaper(alarmId)
                    // Dismiss any existing notification
                    dismissNotification(alarmId)
                    loadAlarms()
                },
                onFailure = {
                    Log.e(TAG, "Error marking alarm cancelled", it)
                    _error.value = it
                }
            )
        }
    }

    private fun dismissNotification(alarmId: String) {
        notificationManager?.cancel(AlarmUtils.getNotificationId(alarmId))
    }

    fun reactivateAlarm(alarmId: String) {
        viewModelScope.launch {
            repository.reactivateAlarm(alarmId).fold(
                onSuccess = {
                    Log.d(TAG, "Alarm reactivated: $alarmId")
                    loadAlarms()
                },
                onFailure = {
                    Log.e(TAG, "Error reactivating alarm", it)
                    _error.value = it
                }
            )
        }
    }

    fun clearError() {
        _error.value = null
    }

    companion object {
        private const val TAG = "AlarmsViewModel"
    }
}
