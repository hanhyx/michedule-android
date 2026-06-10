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
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

class WeekWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val today = LocalDate.now()
        val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
        val weekEnd = weekStart.plusDays(6)
        val weekStartStr = weekStart.toString()
        val weekEndStr = weekEnd.toString()
        val db = AppDatabase.getInstance(context)

        val (myShifts, partnerShifts) = withContext(Dispatchers.IO) {
            val mine = db.shiftDao().getAllShifts()
                .filter { it.date in weekStartStr..weekEndStr }
                .associate { it.date to ShiftType.fromString(it.type) }

            val partner = try {
                val friends = db.friendShiftDao()
                    .getShiftsInRange(weekStartStr, weekEndStr)
                    .firstOrNull() ?: emptyList()
                friends.associate { it.date to ShiftType.fromString(it.type) }
            } catch (_: Exception) {
                emptyMap<String, ShiftType?>()
            }

            mine to partner
        }

        provideContent {
            WeekWidgetContent(today, weekStart, myShifts, partnerShifts)
        }
    }
}

@Composable
private fun WeekWidgetContent(
    today: LocalDate,
    weekStart: LocalDate,
    myShifts: Map<String, ShiftType?>,
    partnerShifts: Map<String, ShiftType?>
) {
    val darkBg = Color(0xFF0F0F1A)
    val purple = Color(0xFFA78BFA)
    val muted = Color(0xFF6B7280)
    val textPrimary = Color(0xFFE8E8ED)
    val sundayRed = Color(0xFFF87171)
    val saturdayBlue = Color(0xFF60A5FA)
    val todayBg = Color(0xFF2D2640)
    val dayNames = listOf("일", "월", "화", "수", "목", "금", "토")

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(darkBg, darkBg))
            .cornerRadius(20.dp)
            .clickable(actionStartActivity<MainActivity>())
            .padding(horizontal = 4.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            for (i in 0..6) {
                val date = weekStart.plusDays(i.toLong())
                val dateStr = date.toString()
                val myShift = myShifts[dateStr]
                val partnerShift = partnerShifts[dateStr]
                val isToday = date == today

                val dayColor = when {
                    isToday -> purple
                    i == 0 -> sundayRed
                    i == 6 -> saturdayBlue
                    else -> muted
                }

                val colModifier = if (isToday) {
                    GlanceModifier
                        .defaultWeight()
                        .background(ColorProvider(todayBg, todayBg))
                        .cornerRadius(10.dp)
                        .padding(vertical = 4.dp, horizontal = 1.dp)
                } else {
                    GlanceModifier
                        .defaultWeight()
                        .padding(vertical = 4.dp, horizontal = 1.dp)
                }

                Column(
                    modifier = colModifier,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = dayNames[i],
                        style = TextStyle(
                            fontSize = 9.sp,
                            color = ColorProvider(dayColor, dayColor)
                        )
                    )
                    Text(
                        text = "${date.dayOfMonth}",
                        style = TextStyle(
                            fontSize = 11.sp,
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                            color = ColorProvider(
                                if (isToday) purple else textPrimary,
                                if (isToday) purple else textPrimary
                            )
                        )
                    )

                    Spacer(modifier = GlanceModifier.height(3.dp))

                    Text(
                        text = myShift?.shortLabel ?: "─",
                        style = TextStyle(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = ColorProvider(
                                myShift?.color ?: muted.copy(alpha = 0.3f),
                                myShift?.color ?: muted.copy(alpha = 0.3f)
                            )
                        )
                    )

                    Spacer(modifier = GlanceModifier.height(1.dp))

                    Text(
                        text = partnerShift?.shortLabel ?: "",
                        style = TextStyle(
                            fontSize = 9.sp,
                            color = ColorProvider(
                                partnerShift?.color?.copy(alpha = 0.7f) ?: Color.Transparent,
                                partnerShift?.color?.copy(alpha = 0.7f) ?: Color.Transparent
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
