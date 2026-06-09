package com.ljh.michedule.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "michedule_prefs")

class PrefsManager(private val context: Context) {

    companion object {
        private val KEY_DEVICE_ID = stringPreferencesKey("device_id")
        private val KEY_MY_NAME = stringPreferencesKey("my_name")
        private val KEY_SUPABASE_URL = stringPreferencesKey("supabase_url")
        private val KEY_SUPABASE_KEY = stringPreferencesKey("supabase_key")
        private val KEY_ROOM_CODE = stringPreferencesKey("room_code")
        private val KEY_PATTERN = stringPreferencesKey("shift_pattern")
        private val KEY_PATTERN_START = stringPreferencesKey("pattern_start_date")
        private val KEY_ALARM_ENABLED = booleanPreferencesKey("alarm_enabled")
        private val KEY_ALARM_HOURS_BEFORE = intPreferencesKey("alarm_hours_before")
    }

    val deviceId: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_DEVICE_ID] ?: UUID.randomUUID().toString().take(8).also { id ->
            context.dataStore.edit { it[KEY_DEVICE_ID] = id }
        }
    }

    val myName: Flow<String> = context.dataStore.data.map { it[KEY_MY_NAME] ?: "" }
    val supabaseUrl: Flow<String> = context.dataStore.data.map { it[KEY_SUPABASE_URL] ?: "" }
    val supabaseKey: Flow<String> = context.dataStore.data.map { it[KEY_SUPABASE_KEY] ?: "" }
    val roomCode: Flow<String> = context.dataStore.data.map { it[KEY_ROOM_CODE] ?: "" }
    val shiftPattern: Flow<String> = context.dataStore.data.map { it[KEY_PATTERN] ?: "" }
    val patternStartDate: Flow<String> = context.dataStore.data.map { it[KEY_PATTERN_START] ?: "" }
    val alarmEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_ALARM_ENABLED] ?: false }
    val alarmHoursBefore: Flow<Int> = context.dataStore.data.map { it[KEY_ALARM_HOURS_BEFORE] ?: 2 }

    suspend fun setMyName(name: String) {
        context.dataStore.edit { it[KEY_MY_NAME] = name }
    }

    suspend fun setSupabaseUrl(url: String) {
        context.dataStore.edit { it[KEY_SUPABASE_URL] = url }
    }

    suspend fun setSupabaseKey(key: String) {
        context.dataStore.edit { it[KEY_SUPABASE_KEY] = key }
    }

    suspend fun setRoomCode(code: String) {
        context.dataStore.edit { it[KEY_ROOM_CODE] = code }
    }

    suspend fun setShiftPattern(pattern: String) {
        context.dataStore.edit { it[KEY_PATTERN] = pattern }
    }

    suspend fun setPatternStartDate(date: String) {
        context.dataStore.edit { it[KEY_PATTERN_START] = date }
    }

    suspend fun ensureDeviceId(): String {
        var id = ""
        context.dataStore.edit { prefs ->
            id = prefs[KEY_DEVICE_ID] ?: UUID.randomUUID().toString().take(8).also { newId ->
                prefs[KEY_DEVICE_ID] = newId
            }
        }
        return id
    }

    suspend fun setAlarmEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_ALARM_ENABLED] = enabled }
    }

    suspend fun setAlarmHoursBefore(hours: Int) {
        context.dataStore.edit { it[KEY_ALARM_HOURS_BEFORE] = hours }
    }

    suspend fun clearSync() {
        context.dataStore.edit {
            it.remove(KEY_ROOM_CODE)
        }
    }
}
