package com.ljh.michedule

import android.app.Application
import com.ljh.michedule.data.PrefsManager
import com.ljh.michedule.data.db.AppDatabase
import com.ljh.michedule.data.repository.ScheduleRepository

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
    }
}
