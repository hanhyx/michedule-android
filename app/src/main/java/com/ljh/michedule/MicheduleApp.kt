package com.ljh.michedule

import android.app.Application
import com.ljh.michedule.data.PrefsManager
import com.ljh.michedule.data.db.AppDatabase
import com.ljh.michedule.data.db.ShiftEntity
import com.ljh.michedule.data.repository.ScheduleRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MicheduleApp : Application() {

    lateinit var database: AppDatabase
        private set
    lateinit var repository: ScheduleRepository
        private set
    lateinit var prefsManager: PrefsManager
        private set

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getInstance(this)
        repository = ScheduleRepository(database)
        prefsManager = PrefsManager(this)

        val prefs = getSharedPreferences("michedule_init", MODE_PRIVATE)
        if (!prefs.getBoolean("seeded_june_2026", false)) {
            CoroutineScope(Dispatchers.IO).launch {
                seedJune2026()
                prefs.edit().putBoolean("seeded_june_2026", true).apply()
            }
        }
    }

    private suspend fun seedJune2026() {
        val schedule = mapOf(
            "2026-06-09" to "night",
            "2026-06-10" to "off",
            "2026-06-11" to "day",
            "2026-06-12" to "day",
            "2026-06-13" to "day",
            "2026-06-14" to "off",
            "2026-06-15" to "night",
            "2026-06-16" to "off",
            "2026-06-17" to "night",
            "2026-06-18" to "off",
            "2026-06-19" to "day",
            "2026-06-20" to "day",
            "2026-06-21" to "off",
            "2026-06-22" to "day",
            "2026-06-23" to "day",
            "2026-06-24" to "night",
            "2026-06-25" to "off",
            "2026-06-26" to "night",
            "2026-06-27" to "off",
            "2026-06-28" to "off",
            "2026-06-29" to "day",
            "2026-06-30" to "day",
        )
        val entities = schedule.map { (date, type) -> ShiftEntity(date = date, type = type) }
        repository.bulkSetShifts(entities)
    }
}
