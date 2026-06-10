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
private const val TABLE = "user_schedules"

@Serializable
data class ScheduleRow(
    val user_code: String,
    val user_name: String = "",
    val partner_code: String? = null,
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

    private var lastKnownFriendShifts: Map<String, String> = emptyMap()
    private var isFirstSync = true

    fun start(scope: CoroutineScope) {
        syncJob?.cancel()
        syncJob = scope.launch {
            val url = prefsManager.supabaseUrl.first()
            val key = prefsManager.supabaseKey.first()
            val myCode = prefsManager.ensureMyCode()
            val partnerCode = prefsManager.partnerCode.first()

            if (url.isBlank() || key.isBlank() || myCode.isBlank()) {
                Log.d(TAG, "Sync not configured, skipping")
                return@launch
            }

            try {
                client = createSupabaseClient(url, key) {
                    install(Postgrest)
                    install(Realtime)
                }

                uploadCurrentData()
                _connected.value = true
                Log.d(TAG, "Connected as: $myCode")

                if (partnerCode.isNotBlank()) {
                    subscribeToPartner(partnerCode)
                    handleRemoteChange(partnerCode)
                }
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

    private suspend fun subscribeToPartner(partnerCode: String) {
        val supabase = client ?: return
        channel = supabase.realtime.channel("partner-$partnerCode")

        channel?.let { ch ->
            val changeFlow = ch.postgresChangeFlow<PostgresAction>(schema = "public") {
                table = TABLE
            }

            ch.subscribe()

            CoroutineScope(Dispatchers.IO).launch {
                changeFlow.collect { action ->
                    when (action) {
                        is PostgresAction.Insert -> handleRemoteChange(partnerCode)
                        is PostgresAction.Update -> handleRemoteChange(partnerCode)
                        else -> {}
                    }
                }
            }
        }
    }

    private suspend fun handleRemoteChange(partnerCode: String) {
        try {
            val friendRow = client?.from(TABLE)
                ?.select { filter { eq("user_code", partnerCode) } }
                ?.decodeList<ScheduleRow>()
                ?.firstOrNull() ?: return

            if (friendRow.user_name.isNotBlank()) {
                prefsManager.setPartnerName(friendRow.user_name)
            }

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
            .setContentTitle("${partnerName}님 일정 변경")
            .setContentText("${changes.size}건의 일정이 변경되었습니다")
            .setStyle(NotificationCompat.BigTextStyle().bigText(summary + extra))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(9999, notification)
    }

    suspend fun uploadCurrentData() {
        try {
            val myCode = prefsManager.ensureMyCode()
            if (myCode.isBlank()) return

            val myName = prefsManager.myName.first()
            val partnerCode = prefsManager.partnerCode.first()

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
                user_code = myCode,
                user_name = myName,
                partner_code = partnerCode.ifBlank { null },
                shifts = shiftsJson,
                memos = memosJson,
                albas = albasJson,
                moods = moodsJson,
                todo_counts = todoCountsJson,
                date_plans = datePlansJson
            )

            client?.from(TABLE)?.upsert(row)
            Log.d(TAG, "Uploaded data for $myCode")
        } catch (e: Exception) {
            Log.e(TAG, "Upload failed", e)
        }
    }
}
