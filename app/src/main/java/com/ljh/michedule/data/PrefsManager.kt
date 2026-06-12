package com.ljh.michedule.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "michedule_prefs")

class PrefsManager(private val context: Context) {

    companion object {
        const val DEFAULT_SUPABASE_URL = "https://ylnhoawekholqlumgytl.supabase.co"
        const val DEFAULT_SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InlsbmhvYXdla2hvbHFsdW1neXRsIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODA5ODQxOTIsImV4cCI6MjA5NjU2MDE5Mn0.w-V40lcq0_iBma2o7x01Z9ZjPB2UAoDw2YzhCl0tS90"

        private val KEY_MY_CODE = stringPreferencesKey("my_code")
        private val KEY_MY_NAME = stringPreferencesKey("my_name")
        private val KEY_SUPABASE_URL = stringPreferencesKey("supabase_url")
        private val KEY_SUPABASE_KEY = stringPreferencesKey("supabase_key")
        private val KEY_PARTNER_CODE = stringPreferencesKey("partner_code")
        private val KEY_PARTNER_NAME = stringPreferencesKey("partner_name")
        private val KEY_PATTERN = stringPreferencesKey("shift_pattern")
        private val KEY_PATTERN_START = stringPreferencesKey("pattern_start_date")
        private val KEY_ALARM_ENABLED = booleanPreferencesKey("alarm_enabled")
        private val KEY_ALARM_HOURS_BEFORE = intPreferencesKey("alarm_hours_before")
        private val KEY_CALENDAR_LOCKED = booleanPreferencesKey("calendar_locked")
        private val KEY_CUSTOM_TIME_RANGES = stringPreferencesKey("custom_time_ranges")
        private val KEY_ALARM_DISABLED_TYPES = stringPreferencesKey("alarm_disabled_types")
        private val KEY_SYNC_PAUSED = booleanPreferencesKey("sync_paused")
        private val KEY_CONNECTION_MUTUAL = booleanPreferencesKey("connection_mutual")
        private val KEY_FCM_TOKEN = stringPreferencesKey("fcm_token")
        private val KEY_MY_PHOTO_URI = stringPreferencesKey("my_photo_uri")
        private val KEY_PARTNER_PHOTO_URI = stringPreferencesKey("partner_photo_uri")
        private val KEY_CHAT_LAST_READ_AT = longPreferencesKey("chat_last_read_at")
        private val KEY_PUSH_CHAT_ENABLED = booleanPreferencesKey("push_chat_enabled")
        private val KEY_PUSH_DATE_PLAN_ENABLED = booleanPreferencesKey("push_date_plan_enabled")
        private val KEY_PUSH_DATE_PLAN_RESPONSE_ENABLED = booleanPreferencesKey("push_date_plan_response_enabled")
        private val KEY_PUSH_SCHEDULE_CHANGE_ENABLED = booleanPreferencesKey("push_schedule_change_enabled")

        private fun generateCode(): String {
            val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
            return (1..6).map { chars.random() }.joinToString("")
        }
    }

    val myCode: Flow<String> = context.dataStore.data.map { it[KEY_MY_CODE] ?: "" }
    val myName: Flow<String> = context.dataStore.data.map { it[KEY_MY_NAME] ?: "" }
    val supabaseUrl: Flow<String> = context.dataStore.data.map { it[KEY_SUPABASE_URL] ?: DEFAULT_SUPABASE_URL }
    val supabaseKey: Flow<String> = context.dataStore.data.map { it[KEY_SUPABASE_KEY] ?: DEFAULT_SUPABASE_KEY }
    val partnerCode: Flow<String> = context.dataStore.data.map { it[KEY_PARTNER_CODE] ?: "" }
    val partnerName: Flow<String> = context.dataStore.data.map { it[KEY_PARTNER_NAME] ?: "" }
    val shiftPattern: Flow<String> = context.dataStore.data.map { it[KEY_PATTERN] ?: "" }
    val patternStartDate: Flow<String> = context.dataStore.data.map { it[KEY_PATTERN_START] ?: "" }
    val alarmEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_ALARM_ENABLED] ?: false }
    val alarmHoursBefore: Flow<Int> = context.dataStore.data.map { it[KEY_ALARM_HOURS_BEFORE] ?: 2 }
    val calendarLocked: Flow<Boolean> = context.dataStore.data.map { it[KEY_CALENDAR_LOCKED] ?: false }
    val syncPaused: Flow<Boolean> = context.dataStore.data.map { it[KEY_SYNC_PAUSED] ?: false }
    val connectionMutual: Flow<Boolean> = context.dataStore.data.map { it[KEY_CONNECTION_MUTUAL] ?: false }
    val fcmToken: Flow<String> = context.dataStore.data.map { it[KEY_FCM_TOKEN] ?: "" }
    val myPhotoUri: Flow<String> = context.dataStore.data.map { it[KEY_MY_PHOTO_URI] ?: "" }
    val partnerPhotoUri: Flow<String> = context.dataStore.data.map { it[KEY_PARTNER_PHOTO_URI] ?: "" }
    val chatLastReadAt: Flow<Long> = context.dataStore.data.map { it[KEY_CHAT_LAST_READ_AT] ?: 0L }
    val pushChatEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_PUSH_CHAT_ENABLED] ?: true }
    val pushDatePlanEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_PUSH_DATE_PLAN_ENABLED] ?: true }
    val pushDatePlanResponseEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_PUSH_DATE_PLAN_RESPONSE_ENABLED] ?: true }
    val pushScheduleChangeEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_PUSH_SCHEDULE_CHANGE_ENABLED] ?: false }

    suspend fun ensureMyCode(): String {
        var code = ""
        context.dataStore.edit { prefs ->
            code = prefs[KEY_MY_CODE] ?: generateCode().also { newCode ->
                prefs[KEY_MY_CODE] = newCode
            }
        }
        return code
    }

    suspend fun setMyCode(code: String) {
        context.dataStore.edit { it[KEY_MY_CODE] = code }
    }


    suspend fun setMyName(name: String) {
        context.dataStore.edit { it[KEY_MY_NAME] = name }
    }

    suspend fun setSupabaseUrl(url: String) {
        context.dataStore.edit { it[KEY_SUPABASE_URL] = url }
    }

    suspend fun setSupabaseKey(key: String) {
        context.dataStore.edit { it[KEY_SUPABASE_KEY] = key }
    }

    suspend fun setPartnerCode(code: String) {
        context.dataStore.edit { it[KEY_PARTNER_CODE] = code }
    }

    suspend fun setPartnerName(name: String) {
        context.dataStore.edit { it[KEY_PARTNER_NAME] = name }
    }

    suspend fun setShiftPattern(pattern: String) {
        context.dataStore.edit { it[KEY_PATTERN] = pattern }
    }

    suspend fun setPatternStartDate(date: String) {
        context.dataStore.edit { it[KEY_PATTERN_START] = date }
    }

    suspend fun setAlarmEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_ALARM_ENABLED] = enabled }
    }

    suspend fun setAlarmHoursBefore(hours: Int) {
        context.dataStore.edit { it[KEY_ALARM_HOURS_BEFORE] = hours }
    }

    suspend fun setCalendarLocked(locked: Boolean) {
        context.dataStore.edit { it[KEY_CALENDAR_LOCKED] = locked }
    }

    suspend fun setSyncPaused(paused: Boolean) {
        context.dataStore.edit { it[KEY_SYNC_PAUSED] = paused }
    }

    suspend fun setConnectionMutual(mutual: Boolean) {
        context.dataStore.edit { it[KEY_CONNECTION_MUTUAL] = mutual }
    }

    suspend fun setFcmToken(token: String) {
        context.dataStore.edit { it[KEY_FCM_TOKEN] = token }
    }

    suspend fun setMyPhotoUri(uri: String) {
        context.dataStore.edit { it[KEY_MY_PHOTO_URI] = uri }
    }

    suspend fun setPartnerPhotoUri(uri: String) {
        context.dataStore.edit { it[KEY_PARTNER_PHOTO_URI] = uri }
    }

    suspend fun setChatLastReadAt(timestamp: Long) {
        context.dataStore.edit { it[KEY_CHAT_LAST_READ_AT] = timestamp }
    }

    suspend fun setPushChatEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_PUSH_CHAT_ENABLED] = enabled }
    }

    suspend fun setPushDatePlanEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_PUSH_DATE_PLAN_ENABLED] = enabled }
    }

    suspend fun setPushDatePlanResponseEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_PUSH_DATE_PLAN_RESPONSE_ENABLED] = enabled }
    }

    suspend fun setPushScheduleChangeEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_PUSH_SCHEDULE_CHANGE_ENABLED] = enabled }
    }

    suspend fun clearPartner() {
        context.dataStore.edit {
            it.remove(KEY_PARTNER_CODE)
            it.remove(KEY_PARTNER_NAME)
            it.remove(KEY_SYNC_PAUSED)
            it.remove(KEY_CONNECTION_MUTUAL)
            it.remove(KEY_PARTNER_PHOTO_URI)
        }
    }

    val customTimeRanges: Flow<String> = context.dataStore.data.map { it[KEY_CUSTOM_TIME_RANGES] ?: "" }
    val alarmDisabledTypes: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        (prefs[KEY_ALARM_DISABLED_TYPES] ?: "").split(",").filter { it.isNotBlank() }.toSet()
    }

    suspend fun setCustomTimeRange(shiftCode: String, timeRange: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[KEY_CUSTOM_TIME_RANGES] ?: ""
            val map = parseTimeRanges(current).toMutableMap()
            map[shiftCode] = timeRange
            prefs[KEY_CUSTOM_TIME_RANGES] = serializeTimeRanges(map)
        }
    }

    suspend fun clearCustomTimeRange(shiftCode: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[KEY_CUSTOM_TIME_RANGES] ?: ""
            val map = parseTimeRanges(current).toMutableMap()
            map.remove(shiftCode)
            prefs[KEY_CUSTOM_TIME_RANGES] = serializeTimeRanges(map)
        }
    }

    suspend fun setAlarmDisabledTypes(types: Set<String>) {
        context.dataStore.edit { it[KEY_ALARM_DISABLED_TYPES] = types.joinToString(",") }
    }

    suspend fun toggleAlarmForType(typeCode: String, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            val current = (prefs[KEY_ALARM_DISABLED_TYPES] ?: "").split(",").filter { it.isNotBlank() }.toMutableSet()
            if (enabled) current.remove(typeCode) else current.add(typeCode)
            prefs[KEY_ALARM_DISABLED_TYPES] = current.joinToString(",")
        }
    }
}

fun parseTimeRanges(data: String): Map<String, String> {
    if (data.isBlank()) return emptyMap()
    return data.split(";").mapNotNull {
        val parts = it.split("=", limit = 2)
        if (parts.size == 2) parts[0] to parts[1] else null
    }.toMap()
}

private fun serializeTimeRanges(map: Map<String, String>): String {
    return map.entries.joinToString(";") { "${it.key}=${it.value}" }
}
