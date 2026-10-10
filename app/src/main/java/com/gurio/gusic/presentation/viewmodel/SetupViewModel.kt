package com.gurio.gusic.presentation.viewmodel

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gurio.gusic.R
import com.gurio.gusic.data.backup.BackupManager
import com.gurio.gusic.data.backup.model.BackupTransferProgressUpdate
import com.gurio.gusic.data.backup.model.BackupOperationType
import com.gurio.gusic.data.backup.model.BackupSection
import com.gurio.gusic.data.backup.model.RestorePlan
import com.gurio.gusic.data.backup.model.RestoreResult
import com.gurio.gusic.data.preferences.AppThemeMode
import com.gurio.gusic.data.preferences.ThemePreferencesRepository
import com.gurio.gusic.data.preferences.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SetupUiState(
    val notificationsPermissionGranted: Boolean = false,
    val navBarStyle: String = "default",
    val navBarCornerRadius: Int = 28,
    val alarmsPermissionGranted: Boolean = false,
    val appThemeMode: String = AppThemeMode.FOLLOW_SYSTEM,
    val isInspectingBackup: Boolean = false,
    val isRestoringBackup: Boolean = false,
    val restorePlan: RestorePlan? = null,
    val backupTransferProgress: BackupTransferProgressUpdate? = null
) {
    val allPermissionsGranted: Boolean
        get() = true
}

sealed interface SetupEvent {
    data class Message(val value: String) : SetupEvent
    data class RestoreCompleted(val message: String) : SetupEvent
}

@HiltViewModel
class SetupViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val themePreferencesRepository: ThemePreferencesRepository,
    private val backupManager: BackupManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(SetupUiState())
    val uiState = _uiState.asStateFlow()
    private val _events = Channel<SetupEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            if (!userPreferencesRepository.initialSetupDoneFlow.first()) {
                themePreferencesRepository.initializeAppThemeMode(AppThemeMode.FOLLOW_SYSTEM)
            }
        }

        viewModelScope.launch {
            combine<Any?, SetupPrefsUpdate>(
                userPreferencesRepository.navBarStyleFlow,
                userPreferencesRepository.navBarCornerRadiusFlow,
                themePreferencesRepository.appThemeModeFlow
            ) { values ->
                SetupPrefsUpdate(
                    style = values[0] as String,
                    radius = values[1] as Int,
                    appThemeMode = values[2] as String
                )
            }.collect { update ->
                _uiState.update { state ->
                    state.copy(
                        navBarStyle = update.style,
                        navBarCornerRadius = update.radius,
                        appThemeMode = update.appThemeMode
                    )
                }
            }
        }
    }

    private data class SetupPrefsUpdate(
        val style: String,
        val radius: Int,
        val appThemeMode: String
    )

    fun checkPermissions(context: Context) {
        val notificationsPermissionGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        val alarmsPermissionGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }

        _uiState.update {
            it.copy(
                notificationsPermissionGranted = notificationsPermissionGranted,
                alarmsPermissionGranted = alarmsPermissionGranted
            )
        }
    }

    fun setNavBarStyle(style: String) {
        viewModelScope.launch {
            userPreferencesRepository.setNavBarStyle(style)
        }
    }

    fun setNavBarCornerRadius(radius: Int) {
        viewModelScope.launch {
            userPreferencesRepository.setNavBarCornerRadius(radius)
        }
    }

    fun setAppThemeMode(mode: String) {
        viewModelScope.launch {
            themePreferencesRepository.setAppThemeMode(mode)
        }
    }

    fun setSetupComplete() {
        viewModelScope.launch {
            completeSetup()
        }
    }

    fun inspectBackupFile(uri: Uri) {
        if (_uiState.value.isInspectingBackup || _uiState.value.isRestoringBackup) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isInspectingBackup = true,
                    restorePlan = null,
                    backupTransferProgress = null
                )
            }
            val result = backupManager.inspectBackup(uri)
            result.fold(
                onSuccess = { plan ->
                    _uiState.update {
                        it.copy(
                            isInspectingBackup = false,
                            restorePlan = plan
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update { it.copy(isInspectingBackup = false) }
                    _events.send(
                        SetupEvent.Message(
                            context.getString(
                                R.string.backup_invalid_format,
                                error.localizedMessage ?: context.getString(R.string.error_unknown),
                            )
                        )
                    )
                }
            )
        }
    }

    fun updateRestorePlanSelection(selectedModules: Set<BackupSection>) {
        _uiState.update { state ->
            state.restorePlan?.let { plan ->
                state.copy(restorePlan = plan.copy(selectedModules = selectedModules))
            } ?: state
        }
    }

    fun clearRestorePlan() {
        _uiState.update {
            it.copy(
                restorePlan = null,
                isInspectingBackup = false,
                isRestoringBackup = false,
                backupTransferProgress = null
            )
        }
    }

    fun restoreFromPlan(uri: Uri) {
        val plan = _uiState.value.restorePlan ?: return
        if (plan.selectedModules.isEmpty() || _uiState.value.isRestoringBackup) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isRestoringBackup = true,
                    backupTransferProgress = BackupTransferProgressUpdate(
                        operation = BackupOperationType.IMPORT,
                        step = 0,
                        totalSteps = 1,
                        title = context.getString(R.string.backup_progress_preparing_restore),
                        detail = context.getString(R.string.backup_progress_starting_task),
                    )
                )
            }

            val result = backupManager.restore(uri, plan) { progress ->
                _uiState.update { state -> state.copy(backupTransferProgress = progress) }
            }

            when (result) {
                is RestoreResult.Success -> {
                    _events.send(SetupEvent.RestoreCompleted(context.getString(R.string.restore_completed_success)))
                }
                is RestoreResult.PartialFailure -> {
                    val canFinishSetup = result.succeeded.isNotEmpty() || !result.rolledBack
                    if (canFinishSetup) {
                        _events.send(
                            SetupEvent.RestoreCompleted(
                                context.getString(R.string.restore_completed_partial_issues),
                            )
                        )
                    } else {
                        _events.send(
                            SetupEvent.Message(
                                context.getString(
                                    R.string.restore_could_not_complete,
                                    result.failed.values.joinToString(),
                                ),
                            )
                        )
                    }
                }
                is RestoreResult.TotalFailure -> {
                    _events.send(SetupEvent.Message(context.getString(R.string.restore_failed_format, result.error)))
                }
            }

            _uiState.update {
                it.copy(
                    isRestoringBackup = false,
                    restorePlan = null,
                    backupTransferProgress = null
                )
            }
        }
    }

    private suspend fun completeSetup() {
        userPreferencesRepository.setInitialSetupDone(true)
    }
}
