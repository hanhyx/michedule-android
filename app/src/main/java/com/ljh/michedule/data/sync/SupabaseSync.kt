package com.ljh.michedule.data.sync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.ljh.michedule.data.PrefsManager
import com.ljh.michedule.data.db.DatePlanEntity
import com.ljh.michedule.data.db.FriendShiftEntity
import com.ljh.michedule.data.db.ShiftEntity
import com.ljh.michedule.data.repository.ScheduleRepository
import com.ljh.michedule.model.ShiftType
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.realtime.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

private const val TAG = "SupabaseSync"
private const val TABLE = "schedules"

@Serializable
data class ScheduleRow(
    val room_code: String,
    val device_id: String,
    val user_name: String = "",
    val shifts: JsonObject = JsonObject(emptyMap()),
    val memos: JsonObject = JsonObject(emptyMap()),
    val albas: JsonObject = JsonObject(emptyMap()),
    val moods: JsonObject = JsonObject(emptyMap()),
    val todo_counts: JsonObject = JsonObject(emptyMap()),
    val date_plans: JsonObject = JsonObject(emptyMap()),
    val updated_at: String? = null
)

class SupabaseSync(
    private val repo: ScheduleRepository,
    private val prefsManager: PrefsManager,
    private val appContext: Context? = null
) {
    private var client: SupabaseClient? = null
    private var channel: RealtimeChannel? = null
    private var syncJob: Job? = null

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _friendName = MutableStateFlow("")
    val friendName: StateFlow<String> = _friendName.asStateFlow()

    private var lastKnownFriendShifts: Map<String, String> = emptyMap()
    private var isFirstSync = true

    fun start(scope: CoroutineScope) {
        syncJob?.cancel()
        syncJob = scope.launch {
            val url = prefsManager.supabaseUrl.first()
            val key = prefsManager.supabaseKey.first()
            val roomCode = prefsManager.roomCode.first()

            if (url.isBlank() || key.isBlank() || roomCode.isBlank()) {
                Log.d(TAG, "Sync not configured, skipping")
                return@launch
            }

            try {
                client = createSupabaseClient(url, key) {
                    install(Postgrest)
                    install(Realtime)
                }

                subscribeToChanges(roomCode)
                _connected.value = true
                Log.d(TAG, "Connected to room: $roomCode")

                uploadCurrentData(roomCode)
                handleRemoteChange(roomCode)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to connect", e)
                _connected.value = false
            }
        }
    }

    fun stop() {
        syncJob?.cancel()
        syncJob = null
        channel = null
        client = null
        _connected.value = false
    }

    private suspend fun subscribeToChanges(roomCode: String) {
        val supabase = client ?: return
        channel = supabase.realtime.channel("schedules-$roomCode")

        channel?.let { ch ->
            val changeFlow = ch.postgresChangeFlow<PostgresAction>(schema = "public") {
                table = TABLE
            }

            ch.subscribe()

            CoroutineScope(Dispatchers.IO).launch {
                changeFlow.collect { action ->
                    when (action) {
                        is PostgresAction.Insert -> handleRemoteChange(roomCode)
                        is PostgresAction.Update -> handleRemoteChange(roomCode)
                        else -> {}
                    }
                }
            }
        }
    }

    private suspend fun handleRemoteChange(roomCode: String) {
        try {
            val deviceId = prefsManager.ensureDeviceId()
            val rows = client?.from(TABLE)
                ?.select { filter { eq("room_code", roomCode) } }
                ?.decodeList<ScheduleRow>()
                ?: return

            // room 안에서 나의 row가 아닌 것 중 가장 최근 것을 상대로 판단
            val otherRows = rows.filter { it.device_id != deviceId }
            val friendRow = otherRows.firstOrNull() ?: return
            _friendName.value = friendRow.user_name

            val newShiftsMap = mutableMapOf<String, String>()
            val friendShifts = mutableListOf<FriendShiftEntity>()
            val albaSet = friendRow.albas.mapNotNull { (date, value) ->
                if ((value as? JsonPrimitive)?.booleanOrNull == true) date else null
            }.toSet()
            val memoMap = friendRow.memos.mapNotNull { (date, value) ->
                val m = (value as? JsonPrimitive)?.content
                if (!m.isNullOrBlank()) date to m else null
            }.toMap()
            val moodMap = friendRow.moods.mapNotNull { (date, value) ->
                val m = (value as? JsonPrimitive)?.content
                if (!m.isNullOrBlank()) date to m else null
            }.toMap()
            val todoCountMap = friendRow.todo_counts.mapNotNull { (date, value) ->
                val c = (value as? JsonPrimitive)?.intOrNull
                if (c != null && c > 0) date to c else null
            }.toMap()

            friendRow.shifts.forEach { (date, value) ->
                val type = (value as? JsonPrimitive)?.content ?: return@forEach
                newShiftsMap[date] = type
                friendShifts.add(
                    FriendShiftEntity(
                        date = date,
                        type = type,
                        friendName = friendRow.user_name,
                        hasAlba = date in albaSet,
                        memo = memoMap[date],
                        mood = moodMap[date],
                        todoCount = todoCountMap[date] ?: 0
                    )
                )
            }

            // 상대의 date_plans를 로컬에 동기화
            val remotePlans = friendRow.date_plans.mapNotNull { (date, value) ->
                val obj = value as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
                val memo = (obj["memo"] as? JsonPrimitive)?.content ?: ""
                val by = (obj["by"] as? JsonPrimitive)?.content ?: friendRow.user_name
                DatePlanEntity(date = date, memo = memo, createdBy = by)
            }
            if (remotePlans.isNotEmpty()) {
                repo.syncDatePlans(remotePlans)
            }

            if (!isFirstSync && appContext != null) {
                val changes = detectChanges(lastKnownFriendShifts, newShiftsMap)
                if (changes.isNotEmpty()) {
                    sendPartnerChangeNotification(
                        appContext,
                        friendRow.user_name.ifBlank { "상대" },
                        changes
                    )
                }
            }
            isFirstSync = false
            lastKnownFriendShifts = newShiftsMap

            repo.syncFriendShifts(friendShifts)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to process remote change", e)
        }
    }

    private fun detectChanges(
        old: Map<String, String>,
        new: Map<String, String>
    ): List<Triple<String, String?, String?>> {
        val changes = mutableListOf<Triple<String, String?, String?>>()
        val allDates = (old.keys + new.keys).sorted()
        allDates.forEach { date ->
            val oldType = old[date]
            val newType = new[date]
            if (oldType != newType) {
                changes.add(Triple(date, oldType, newType))
            }
        }
        return changes
    }

    private fun sendPartnerChangeNotification(
        context: Context,
        partnerName: String,
        changes: List<Triple<String, String?, String?>>
    ) {
        val channelId = "michedule_partner_change"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "상대 일정 변경", NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "상대방의 일정이 변경되었을 때 알림" }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }

        val summary = changes.take(5).joinToString("\n") { (date, old, new) ->
            val d = date.substring(5).replace("-", "/")
            val oldLabel = old?.let { ShiftType.fromString(it)?.label } ?: "없음"
            val newLabel = new?.let { ShiftType.fromString(it)?.label } ?: "삭제"
            "$d: $oldLabel → $newLabel"
        }
        val extra = if (changes.size > 5) "\n외 ${changes.size - 5}건" else ""

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("📅 ${partnerName}님 일정 변경")
            .setContentText("${changes.size}건의 일정이 변경되었습니다")
            .setStyle(NotificationCompat.BigTextStyle().bigText(summary + extra))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(9999, notification)
    }

    suspend fun uploadCurrentData(roomCode: String? = null) {
        try {
            val code = roomCode ?: prefsManager.roomCode.first()
            if (code.isBlank()) return

            val deviceId = prefsManager.ensureDeviceId()
            val myName = prefsManager.myName.first()

            // room 안에 3개 이상 row가 있으면 orphan 정리 (앱 재설치로 device_id 변경된 경우)
            try {
                val existing = client?.from(TABLE)
                    ?.select { filter { eq("room_code", code) } }
                    ?.decodeList<ScheduleRow>() ?: emptyList()

                if (existing.size >= 2) {
                    val myOldRows = existing.filter { it.device_id != deviceId }
                    // room에 이미 2명이 있고, 그 중 현재 device_id가 없으면 → 재설치 case
                    // 이름이 같은 기존 row가 있으면 그것을 삭제 (내가 재설치한 것)
                    if (existing.none { it.device_id == deviceId } && myOldRows.size >= 2) {
                        val orphan = myOldRows.firstOrNull { it.user_name == myName }
                            ?: myOldRows.last()
                        client?.from(TABLE)?.delete {
                            filter {
                                eq("room_code", code)
                                eq("device_id", orphan.device_id)
                            }
                        }
                        Log.d(TAG, "Cleaned orphan row: ${orphan.device_id}")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Orphan cleanup skipped", e)
            }

            val allShifts = repo.getAllShifts()
            val shiftsJson = buildJsonObject {
                allShifts.forEach { s ->
                    if (s.type.isNotBlank()) put(s.date, s.type)
                }
            }
            val memosJson = buildJsonObject {
                allShifts.forEach { s ->
                    if (!s.memo.isNullOrBlank()) put(s.date, s.memo)
                }
            }
            val albasJson = buildJsonObject {
                allShifts.forEach { s ->
                    if (s.hasAlba) put(s.date, true)
                }
            }

            val allMoods = repo.getAllMoods()
            val moodsJson = buildJsonObject {
                allMoods.forEach { m ->
                    put(m.date, m.emoji)
                }
            }

            val allTodos = repo.getAllTodos()
            val todoCounts = allTodos.groupBy { it.date }.mapValues { it.value.size }
            val todoCountsJson = buildJsonObject {
                todoCounts.forEach { (date, count) ->
                    put(date, count)
                }
            }

            val allDatePlans = repo.getAllDatePlans()
            val datePlansJson = buildJsonObject {
                allDatePlans.forEach { plan ->
                    put(plan.date, buildJsonObject {
                        put("memo", plan.memo)
                        put("by", plan.createdBy)
                    })
                }
            }

            val row = ScheduleRow(
                room_code = code,
                device_id = deviceId,
                user_name = myName,
                shifts = shiftsJson,
                memos = memosJson,
                albas = albasJson,
                moods = moodsJson,
                todo_counts = todoCountsJson,
                date_plans = datePlansJson
            )

            client?.from(TABLE)?.upsert(row)
            Log.d(TAG, "Uploaded data for room $code")
        } catch (e: Exception) {
            Log.e(TAG, "Upload failed", e)
        }
    }
}
