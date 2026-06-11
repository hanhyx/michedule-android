package com.ljh.michedule

import android.app.Application
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.ljh.michedule.data.PrefsManager
import com.ljh.michedule.data.ShiftTypeManager
import com.ljh.michedule.data.parseTimeRanges
import com.ljh.michedule.data.db.AppDatabase
import com.ljh.michedule.data.repository.ScheduleRepository
import com.ljh.michedule.data.sync.SupabaseSync
import com.ljh.michedule.model.ShiftType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MicheduleApp : Application() {

    lateinit var database: AppDatabase
        private set
    lateinit var repository: ScheduleRepository
        private set
    lateinit var prefsManager: PrefsManager
        private set
    lateinit var shiftTypeManager: ShiftTypeManager
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

        appScope.launch { prefsManager.ensureMyCode() }
        startSync()
        loadCustomTimeRanges()
        initFcmToken()

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

    fun triggerUpload() {
        appScope.launch {
            if (prefsManager.syncPaused.first()) return@launch
            supabaseSync?.uploadCurrentData()
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

    private suspend fun clearJuneSeed() {
        val juneDates = (1..30).map { "2026-06-%02d".format(it) }
        juneDates.forEach { date ->
            repository.clearShift(java.time.LocalDate.parse(date))
        }
    }
}
