package com.ljh.michedule.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.color.ColorProvider
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.ljh.michedule.MainActivity
import com.ljh.michedule.data.db.AppDatabase
import com.ljh.michedule.model.ShiftType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

class WeekWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val today = LocalDate.now()
        val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
        val db = AppDatabase.getInstance(context)

        val weekShifts = withContext(Dispatchers.IO) {
            val weekEnd = weekStart.plusDays(6)
            val shifts = db.shiftDao().getAllShifts()
            shifts.filter { s ->
                s.date >= weekStart.toString() && s.date <= weekEnd.toString()
            }.associate { it.date to ShiftType.fromString(it.type) }
        }

        provideContent {
            WeekWidgetContent(today, weekStart, weekShifts)
        }
    }
}

@Composable
private fun WeekWidgetContent(
    today: LocalDate,
    weekStart: LocalDate,
    shifts: Map<String, ShiftType?>
) {
    val darkBg = Color(0xFF16161E)
    val purple = Color(0xFFA78BFA)
    val muted = Color(0xFF6B7280)
    val textPrimary = Color(0xFFE8E8ED)
    val dayNames = listOf("일", "월", "화", "수", "목", "금", "토")

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(darkBg, darkBg))
            .cornerRadius(16.dp)
            .clickable(actionStartActivity<MainActivity>())
            .padding(horizontal = 8.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            for (i in 0..6) {
                val date = weekStart.plusDays(i.toLong())
                val dateStr = date.toString()
                val shift = shifts[dateStr]
                val isToday = date == today

                Column(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .padding(2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = dayNames[i],
                        style = TextStyle(
                            fontSize = 10.sp,
                            color = ColorProvider(
                                if (isToday) purple else muted,
                                if (isToday) purple else muted
                            )
                        )
                    )
                    Text(
                        text = "${date.dayOfMonth}",
                        style = TextStyle(
                            fontSize = 12.sp,
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                            color = ColorProvider(
                                if (isToday) purple else textPrimary,
                                if (isToday) purple else textPrimary
                            )
                        )
                    )
                    Text(
                        text = shift?.shortLabel ?: "-",
                        style = TextStyle(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = ColorProvider(
                                shift?.color ?: muted,
                                shift?.color ?: muted
                            )
                        )
                    )
                }
            }
        }
    }
}

class WeekWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeekWidget()
}
