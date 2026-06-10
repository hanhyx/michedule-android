package com.ljh.michedule.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.ljh.michedule.data.PrefsManager
import com.ljh.michedule.data.db.ShiftEntity
import com.ljh.michedule.model.ShiftType
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

object ShiftAlarmManager {

    private const val TAG = "ShiftAlarmManager"

    fun scheduleAlarm(context: Context, date: LocalDate, shift: ShiftType, hoursBeforeWork: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val workStartHour = when (shift) {
            ShiftType.DAY -> 8
            ShiftType.NIGHT -> 18
            ShiftType.NIGHT_EARLY -> 16
            ShiftType.ALBA -> 9
            ShiftType.OFF -> return
        }
        val workStartMinute = when (shift) {
            ShiftType.DAY -> 30
            ShiftType.NIGHT -> 0
            ShiftType.NIGHT_EARLY -> 30
            ShiftType.ALBA -> 0
            ShiftType.OFF -> return
        }

        val alarmTime = LocalDateTime.of(date.year, date.month, date.dayOfMonth, workStartHour, workStartMinute)
            .minusHours(hoursBeforeWork.toLong())

        if (alarmTime.isBefore(LocalDateTime.now())) return

        val millis = alarmTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(AlarmReceiver.EXTRA_SHIFT_LABEL, shift.label)
            putExtra(AlarmReceiver.EXTRA_SHIFT_TIME, "${workStartHour}:${String.format("%02d", workStartMinute)}")
        }

        val requestCode = (date.toEpochDay() * 10 + shift.ordinal).toInt()
        val pendingIntent = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pendingIntent)
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pendingIntent)
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pendingIntent)
            }
            Log.d(TAG, "Alarm set for $shift on $date at $alarmTime")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set alarm", e)
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pendingIntent)
        }
    }

    fun scheduleUpcomingAlarms(context: Context, shifts: Map<String, ShiftEntity>, hoursBeforeWork: Int) {
        val today = LocalDate.now()
        for (i in 0..7) {
            val date = today.plusDays(i.toLong())
            val shiftEntity = shifts[date.toString()] ?: continue
            val shiftType = ShiftType.fromString(shiftEntity.type) ?: continue
            if (shiftType == ShiftType.OFF) continue
            scheduleAlarm(context, date, shiftType, hoursBeforeWork)
        }
    }
}
