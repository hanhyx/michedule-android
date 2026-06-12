package com.ljh.michedule

import android.app.Application
import android.net.Uri
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.ljh.michedule.data.PrefsManager
import com.ljh.michedule.data.ShiftTypeManager
import com.ljh.michedule.data.parseTimeRanges
import com.ljh.michedule.data.db.AppDatabase
import com.ljh.michedule.data.repository.ChatRepository
import com.ljh.michedule.data.repository.ScheduleRepository
import com.ljh.michedule.data.sync.SupabaseSync
import com.ljh.michedule.model.ShiftType
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MicheduleApp : Application() {

    @Volatile
    var isChatScreenActive = false

    lateinit var database: AppDatabase
        private set
    lateinit var repository: ScheduleRepository
        private set
    lateinit var prefsManager: PrefsManager
        private set
    lateinit var shiftTypeManager: ShiftTypeManager
        private set
    lateinit var chatRepository: ChatRepository
        private set
    var supabaseSync: SupabaseSync? = null
        private set

    private val appScope = CoroutineScope(Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getInstance(this)
        repository = ScheduleRepository(database)
        prefsManager = PrefsManager(this)
        shiftTypeManager = ShiftTypeManager(database.shiftTypeConfigDao(), appScope)
        chatRepository = ChatRepository(database.chatMessageDao(), prefsManager, this)

        appScope.launch {
            setupDebugAccountIfNeeded()
            prefsManager.ensureMyCode()
        }
        startSync()
        loadCustomTimeRanges()
        initFcmToken()
        migrateCreatedByToCode()

        val prefs = getSharedPreferences("michedule_init", MODE_PRIVATE)
        if (!prefs.getBoolean("cleared_june_seed_v1", false)) {
            CoroutineScope(Dispatchers.IO).launch {
                clearJuneSeed()
                prefs.edit().putBoolean("cleared_june_seed_v1", true).apply()
            }
        }
    }

    fun startSync() {
        appScope.launch {
            val paused = prefsManager.syncPaused.first()
            if (paused) {
                supabaseSync?.stop()
                return@launch
            }
            supabaseSync?.stop()
            val sync = SupabaseSync(repository, prefsManager, this@MicheduleApp, shiftTypeManager)
            supabaseSync = sync
            sync.start(appScope)
        }
    }

    fun stopSync() {
        supabaseSync?.stop()
        supabaseSync = null
    }

    fun connectPartner(partnerCode: String) {
        appScope.launch {
            repository.clearAllFriendData()
            prefsManager.setPartnerCode(partnerCode)
            prefsManager.setPartnerName(partnerCode)
            prefsManager.setConnectionMutual(false)
            startSync()
        }
    }

    fun disconnectPartner() {
        appScope.launch {
            supabaseSync?.stop()
            supabaseSync = null
            prefsManager.clearPartner()
            repository.clearAllFriendData()
            startSync()
        }
    }

    fun updateMyName(newName: String) {
        appScope.launch {
            prefsManager.setMyName(newName)
            triggerUpload()
        }
    }

    fun triggerUpload() {
        appScope.launch {
            if (prefsManager.syncPaused.first()) return@launch
            supabaseSync?.uploadCurrentData()
        }
    }

    fun saveMyProfilePhoto(sourceUri: Uri) {
        appScope.launch {
            try {
                val file = java.io.File(filesDir, "my_profile.jpg")
                contentResolver.openInputStream(sourceUri)?.use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                }
                val localUri = Uri.fromFile(file).toString()
                prefsManager.setMyPhotoUri(localUri)

                val myCode = prefsManager.ensureMyCode()
                val url = prefsManager.supabaseUrl.first()
                val key = prefsManager.supabaseKey.first()
                val storagePath = "profiles/$myCode.jpg"
                val uploadUrl = "$url/storage/v1/object/chat-images/$storagePath"

                val bytes = file.readBytes()
                val httpClient = HttpClient(OkHttp)
                httpClient.request(uploadUrl) {
                    method = HttpMethod.Post
                    header("Authorization", "Bearer $key")
                    header("apikey", key)
                    contentType(ContentType.Image.JPEG)
                    setBody(bytes)
                }
                httpClient.close()

                val publicUrl = "$url/storage/v1/object/public/chat-images/$storagePath"
                prefsManager.setMyPhotoUri(publicUrl)
                triggerUpload()
            } catch (e: Exception) {
                Log.e("MicheduleApp", "Failed to save profile photo", e)
            }
        }
    }

    private fun initFcmToken() {
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            Log.d("FCM", "Token: ${token.take(20)}...")
            appScope.launch {
                prefsManager.setFcmToken(token)
            }
        }
    }

    fun sendDatePlanPush(date: String, memo: String) {
        appScope.launch {
            supabaseSync?.sendDatePlanPush(date, memo)
        }
    }

    fun sendDatePlanResponsePush(date: String, response: String) {
        appScope.launch {
            supabaseSync?.sendDatePlanResponsePush(date, response)
        }
    }

    private fun loadCustomTimeRanges() {
        appScope.launch {
            prefsManager.customTimeRanges.collect { data ->
                val map = parseTimeRanges(data)
                val result = mutableMapOf<ShiftType, String>()
                map.forEach { (code, range) ->
                    ShiftType.fromString(code)?.let { result[it] = range }
                }
                ShiftType.customTimeRanges = result
            }
        }
    }

    private suspend fun setupDebugAccountIfNeeded() {
        if (!BuildConfig.DEBUG) return
        val currentCode = prefsManager.ensureMyCode()
        if (currentCode == "I33J1S") return
        val prefs = getSharedPreferences("michedule_init", MODE_PRIVATE)
        if (prefs.getBoolean("debug_account_set_v1", false)) return
        prefsManager.setMyCode("I33J1S")
        prefsManager.setMyName("송도여신")
        prefsManager.setPartnerCode("0BBT88")
        prefsManager.setPartnerName("부천왕자")
        prefsManager.setConnectionMutual(true)
        prefs.edit().putBoolean("debug_account_set_v1", true).apply()
        Log.d("MicheduleApp", "Debug account set to 송도여신 (I33J1S)")
    }

    private fun migrateCreatedByToCode() {
        val prefs = getSharedPreferences("michedule_init", MODE_PRIVATE)
        if (prefs.getBoolean("migrated_createdby_v2", false)) return
        appScope.launch {
            try {
                val myCode = prefsManager.ensureMyCode()
                val partnerCode = prefsManager.partnerCode.first()
                val partnerName = prefsManager.partnerName.first()
                if (myCode.isNotBlank()) {
                    repository.migrateCreatedByToCode(myCode, partnerCode, partnerName)
                    prefs.edit().putBoolean("migrated_createdby_v2", true).apply()
                    Log.d("MicheduleApp", "Migrated date_plans createdBy to user_code (v2)")
                }
            } catch (e: Exception) {
                Log.e("MicheduleApp", "createdBy migration failed", e)
            }
        }
    }

    private suspend fun clearJuneSeed() {
        val juneDates = (1..30).map { "2026-06-%02d".format(it) }
        juneDates.forEach { date ->
            repository.clearShift(java.time.LocalDate.parse(date))
        }
    }
}
