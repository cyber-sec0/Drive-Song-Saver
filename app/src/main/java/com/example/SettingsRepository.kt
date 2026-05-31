package com.example

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private val OVERLAY_ENABLED = booleanPreferencesKey("overlay_enabled")
    private val OVERLAY_SIZE = floatPreferencesKey("overlay_size")
    private val OVERLAY_TRANSPARENCY = floatPreferencesKey("overlay_transparency")
    private val OVERLAY_X = intPreferencesKey("overlay_x")
    private val OVERLAY_Y = intPreferencesKey("overlay_y")

    val isOverlayEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[OVERLAY_ENABLED] ?: true
    }

    val overlaySize: Flow<Float> = context.dataStore.data.map { preferences ->
        preferences[OVERLAY_SIZE] ?: 1f
    }

    val overlayTransparency: Flow<Float> = context.dataStore.data.map { preferences ->
        preferences[OVERLAY_TRANSPARENCY] ?: 1f // 1f is fully opaque
    }

    val overlayX: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[OVERLAY_X] ?: 100
    }

    val overlayY: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[OVERLAY_Y] ?: 200
    }

    suspend fun setOverlayEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[OVERLAY_ENABLED] = enabled
        }
    }

    suspend fun setOverlaySize(size: Float) {
        context.dataStore.edit { preferences ->
            preferences[OVERLAY_SIZE] = size
        }
    }

    suspend fun setOverlayTransparency(transparency: Float) {
        context.dataStore.edit { preferences ->
            preferences[OVERLAY_TRANSPARENCY] = transparency
        }
    }

    suspend fun setOverlayPosition(x: Int, y: Int) {
        context.dataStore.edit { preferences ->
            preferences[OVERLAY_X] = x
            preferences[OVERLAY_Y] = y
        }
    }
}
