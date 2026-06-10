package com.ljh.michedule

import android.app.Application
import com.ljh.michedule.data.PrefsManager
import com.ljh.michedule.data.parseTimeRanges
import com.ljh.michedule.data.db.AppDatabase
import com.ljh.michedule.data.db.ShiftEntity
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
    var supabaseSync: SupabaseSync? = null
        private set

    private val appScope = CoroutineScope(Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getInstance(this)
        repository = ScheduleRepository(database)
        prefsManager = PrefsManager(this)

        startSync()
        loadCustomTimeRanges()

        val prefs = getSharedPreferences("michedule_init", MODE_PRIVATE)
        if (!prefs.getBoolean("cleared_june_seed_v1", false)) {
            CoroutineScope(Dispatchers.IO).launch {
                clearJuneSeed()
                prefs.edit().putBoolean("cleared_june_seed_v1", true).apply()
            }
        }
    }

    fun startSync() {
        supabaseSync?.stop()
        val sync = SupabaseSync(repository, prefsManager, this)
        supabaseSync = sync
        sync.start(appScope)
    }

    fun triggerUpload() {
        appScope.launch {
            supabaseSync?.uploadCurrentData()
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
