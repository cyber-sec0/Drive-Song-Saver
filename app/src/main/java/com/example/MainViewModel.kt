package com.example

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.TextUtils
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import androidx.lifecycle.viewModelScope

class MainViewModel(
    private val contentRepository: SettingsRepository,
    private val mediaLogDao: MediaLogDao
) : ViewModel() {
    val isOverlayEnabled: Flow<Boolean> = contentRepository.isOverlayEnabled
    val overlaySize: Flow<Float> = contentRepository.overlaySize
    val overlayTransparency: Flow<Float> = contentRepository.overlayTransparency

    val mediaLogs = mediaLogDao.getAllLogs().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    suspend fun setOverlayEnabled(enabled: Boolean) {
        contentRepository.setOverlayEnabled(enabled)
    }

    suspend fun setOverlaySize(size: Float) {
        contentRepository.setOverlaySize(size)
    }

    suspend fun setOverlayTransparency(transparency: Float) {
        contentRepository.setOverlayTransparency(transparency)
    }

    suspend fun clearLogs() {
        mediaLogDao.clearLogs()
    }

    suspend fun deleteLog(log: MediaLogItem) {
        mediaLogDao.deleteLog(log)
    }

    suspend fun insertLog(log: MediaLogItem) {
        mediaLogDao.insertLog(log)
    }
}

class MainViewModelFactory(
    private val repository: SettingsRepository,
    private val mediaLogDao: MediaLogDao
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return MainViewModel(repository, mediaLogDao) as T
    }
}

object PermissionChecker {

    fun isAccessibilityServiceEnabled(context: Context, service: Class<*>): Boolean {
        val enabledServices = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServices)
        while (colonSplitter.hasNext()) {
            if (colonSplitter.next().equals(ComponentName(context, service).flattenToString(), ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    fun isNotificationServiceEnabled(context: Context): Boolean {
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        return flat?.contains(context.packageName) == true
    }
    
    fun canDrawOverlays(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }
}
