package com.ljh.michedule.data.sync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.ljh.michedule.data.PrefsManager
import com.ljh.michedule.data.ShiftTypeManager
import com.ljh.michedule.data.db.DatePlanEntity
import com.ljh.michedule.data.db.FriendShiftEntity
import com.ljh.michedule.data.db.ShiftTypeConfig
import com.ljh.michedule.data.repository.ScheduleRepository
import com.ljh.michedule.model.ShiftType
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.realtime.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.*
import io.ktor.http.*
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
    val shift_types: JsonObject = JsonObject(emptyMap()),
    val fcm_token: String? = null,
    val updated_at: String? = null
)

class SupabaseSync(
    private val repo: ScheduleRepository,
    private val prefsManager: PrefsManager,
    private val appContext: Context? = null,
    private val shiftTypeManager: ShiftTypeManager? = null
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

            val myCode = prefsManager.ensureMyCode()
            val isMutual = friendRow.partner_code == myCode
            val wasMutual = prefsManager.connectionMutual.first()

            if (!isMutual) {
                Log.d(TAG, "Connection not mutual: partner's partner_code=${friendRow.partner_code}, my code=$myCode")
                prefsManager.setConnectionMutual(false)
                repo.clearAllFriendData()

                if (wasMutual) {
                    Log.d(TAG, "Partner disconnected — auto-clearing connection on our side")
                    prefsManager.clearPartner()
                    uploadCurrentData()
                }
                return
            }

            if (friendRow.user_name.isNotBlank()) {
                prefsManager.setPartnerName(friendRow.user_name)
            }
            prefsManager.setConnectionMutual(true)

            Log.d(TAG, "Mutual connection confirmed with $partnerCode")

            if (friendRow.shift_types.isNotEmpty()) {
                val partnerTypes = mutableMapOf<String, ShiftTypeConfig>()
                friendRow.shift_types.forEach { (id, value) ->
                    val obj = value as? JsonObject ?: return@forEach
                    val label = (obj["label"] as? JsonPrimitive)?.content ?: return@forEach
                    val short = (obj["short"] as? JsonPrimitive)?.content ?: label.take(1)
                    val emoji = (obj["emoji"] as? JsonPrimitive)?.content ?: "📋"
                    val color = (obj["color"] as? JsonPrimitive)?.content ?: "#FF808080"
                    val bg = (obj["bg"] as? JsonPrimitive)?.content ?: "#33808080"
                    partnerTypes[id] = ShiftTypeConfig(
                        id = id, label = label, shortLabel = short, emoji = emoji,
                        colorHex = color, bgColorHex = bg, defaultTimeRange = "",
                        sortOrder = 0, inCycle = false, isBuiltIn = false
                    )
                }
                shiftTypeManager?.setPartnerTypes(partnerTypes)
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
            val oldLabel = old?.let { shiftTypeManager?.getById(it)?.label ?: ShiftType.fromString(it)?.label ?: it } ?: "없음"
            val newLabel = new?.let { shiftTypeManager?.getById(it)?.label ?: ShiftType.fromString(it)?.label ?: it } ?: "삭제"
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

    suspend fun sendDatePlanPush(date: String, memo: String) {
        try {
            val partnerCode = prefsManager.partnerCode.first()
            if (partnerCode.isBlank()) return
            val isMutual = prefsManager.connectionMutual.first()
            if (!isMutual) return

            val partnerRow = client?.from(TABLE)
                ?.select { filter { eq("user_code", partnerCode) } }
                ?.decodeList<ScheduleRow>()
                ?.firstOrNull()

            val partnerToken = partnerRow?.fcm_token
            if (partnerToken.isNullOrBlank()) {
                Log.d(TAG, "Partner has no FCM token, skip push")
                return
            }

            val myName = prefsManager.myName.first().ifBlank { "상대방" }
            val url = prefsManager.supabaseUrl.first()
            val key = prefsManager.supabaseKey.first()

            val functionUrl = url.replace(".supabase.co", ".supabase.co/functions/v1/send-push")
            val payload = buildJsonObject {
                put("fcm_token", partnerToken)
                put("title", "💕 ${myName}님의 만나요!")
                put("body", if (memo.isNotBlank()) "${date}에 만나요! 📝 $memo" else "${date}에 만나자고 해요! 💕")
                put("data", buildJsonObject {
                    put("type", "date_plan")
                    put("sender_name", myName)
                    put("date", date)
                    put("memo", memo)
                })
            }

            val httpClient = HttpClient(OkHttp)
            val response = httpClient.request(functionUrl) {
                method = HttpMethod.Post
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer $key")
                setBody(payload.toString())
            }
            Log.d(TAG, "Push sent, status=${response.status}")
            httpClient.close()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send date plan push", e)
        }
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

            val shiftTypesJson = buildJsonObject {
                shiftTypeManager?.allTypes?.value?.forEach { config ->
                    put(config.id, buildJsonObject {
                        put("label", config.label)
                        put("short", config.shortLabel)
                        put("emoji", config.emoji)
                        put("color", config.colorHex)
                        put("bg", config.bgColorHex)
                    })
                }
            }

            val fcmToken = prefsManager.fcmToken.first().ifBlank { null }

            val row = ScheduleRow(
                user_code = myCode,
                user_name = myName,
                partner_code = partnerCode.ifBlank { null },
                shifts = shiftsJson,
                memos = memosJson,
                albas = albasJson,
                moods = moodsJson,
                todo_counts = todoCountsJson,
                date_plans = datePlansJson,
                shift_types = shiftTypesJson,
                fcm_token = fcmToken
            )

            client?.from(TABLE)?.upsert(row)
            Log.d(TAG, "Uploaded data for $myCode")
        } catch (e: Exception) {
            Log.e(TAG, "Upload failed", e)
        }
    }
}
