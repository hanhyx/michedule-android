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
import com.ljh.michedule.widget.WidgetShiftInfo
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
                .associate { it.date to WidgetShiftInfo(ShiftType.fromString(it.type), it.hasAlba) }

            val partner = try {
                val friends = db.friendShiftDao()
                    .getShiftsInRange(weekStartStr, weekEndStr)
                    .firstOrNull() ?: emptyList()
                friends.associate { it.date to WidgetShiftInfo(ShiftType.fromString(it.type), it.hasAlba) }
            } catch (_: Exception) {
                emptyMap<String, WidgetShiftInfo>()
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
    myShifts: Map<String, WidgetShiftInfo>,
    partnerShifts: Map<String, WidgetShiftInfo>
) {
    val darkBg = Color(0xFF0F0F1A)
    val purple = Color(0xFFA78BFA)
    val muted = Color(0xFF6B7280)
    val textPrimary = Color(0xFFE8E8ED)
    val sundayRed = Color(0xFFF87171)
    val saturdayBlue = Color(0xFF60A5FA)
    val todayBg = Color(0xFF2D2640)
    val albaColor = Color(0xFFF97316)
    val dayNames = listOf("일", "월", "화", "수", "목", "금", "토")

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(darkBg, darkBg))
            .cornerRadius(16.dp)
            .clickable(actionStartActivity<MainActivity>())
            .padding(horizontal = 2.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            for (i in 0..6) {
                val date = weekStart.plusDays(i.toLong())
                val dateStr = date.toString()
                val myInfo = myShifts[dateStr]
                val partnerInfo = partnerShifts[dateStr]
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
                        .cornerRadius(8.dp)
                        .padding(vertical = 2.dp)
                } else {
                    GlanceModifier
                        .defaultWeight()
                        .padding(vertical = 2.dp)
                }

                Column(
                    modifier = colModifier,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 요일 + 날짜 한 줄
                    Text(
                        text = "${dayNames[i]}${date.dayOfMonth}",
                        style = TextStyle(
                            fontSize = 9.sp,
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                            color = ColorProvider(dayColor, dayColor)
                        )
                    )

                    Spacer(modifier = GlanceModifier.height(1.dp))

                    // 내 일정 (크게)
                    Text(
                        text = myInfo?.type?.shortLabel ?: "─",
                        style = TextStyle(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = ColorProvider(
                                myInfo?.type?.color ?: muted.copy(alpha = 0.3f),
                                myInfo?.type?.color ?: muted.copy(alpha = 0.3f)
                            )
                        )
                    )
                    if (myInfo?.hasAlba == true) {
                        Text("알", style = TextStyle(fontSize = 7.sp, fontWeight = FontWeight.Bold, color = ColorProvider(albaColor, albaColor)))
                    }

                    // 상대 일정 (작게)
                    val pLabel = partnerInfo?.type?.shortLabel
                    if (pLabel != null) {
                        Text(
                            text = pLabel,
                            style = TextStyle(
                                fontSize = 8.sp,
                                color = ColorProvider(
                                    partnerInfo.type?.color?.copy(alpha = 0.5f) ?: Color.Transparent,
                                    partnerInfo.type?.color?.copy(alpha = 0.5f) ?: Color.Transparent
                                )
                            )
                        )
                    }
                }
            }
        }
    }
}

class WeekWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeekWidget()
}
