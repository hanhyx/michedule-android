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
            val typeConfigs = try { db.shiftTypeConfigDao().getAll().associateBy { it.id } } catch (_: Exception) { emptyMap() }
            val mine = db.shiftDao().getAllShifts()
                .filter { it.date in weekStartStr..weekEndStr }
                .associate { it.date to WidgetShiftInfo(ShiftType.fromString(it.type), typeConfigs[it.type], it.hasAlba) }

            val partner = try {
                val friends = db.friendShiftDao()
                    .getShiftsInRange(weekStartStr, weekEndStr)
                    .firstOrNull() ?: emptyList()
                friends.associate { it.date to WidgetShiftInfo(ShiftType.fromString(it.type), typeConfigs[it.type], it.hasAlba) }
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
    val divider = Color(0xFF2A2A3D)
    val dayNames = listOf("일", "월", "화", "수", "목", "금", "토")

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(darkBg, darkBg))
            .cornerRadius(16.dp)
            .clickable(actionStartActivity<MainActivity>())
            .padding(horizontal = 4.dp, vertical = 6.dp)
    ) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            // 요일 헤더 Row
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                for (i in 0..6) {
                    val date = weekStart.plusDays(i.toLong())
                    val isToday = date == today
                    val dayColor = when {
                        isToday -> purple
                        i == 0 -> sundayRed
                        i == 6 -> saturdayBlue
                        else -> muted
                    }
                    Column(
                        modifier = GlanceModifier.defaultWeight(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(dayNames[i], style = TextStyle(fontSize = 9.sp, color = ColorProvider(dayColor, dayColor)))
                        Text(
                            "${date.dayOfMonth}",
                            style = TextStyle(
                                fontSize = 11.sp,
                                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                                color = ColorProvider(if (isToday) purple else textPrimary, if (isToday) purple else textPrimary)
                            )
                        )
                    }
                }
            }

            Spacer(modifier = GlanceModifier.height(4.dp))

            // 내 일정 Row (크게)
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                for (i in 0..6) {
                    val date = weekStart.plusDays(i.toLong())
                    val dateStr = date.toString()
                    val myInfo = myShifts[dateStr]
                    val isToday = date == today

                    val colMod = if (isToday) {
                        GlanceModifier.defaultWeight().background(ColorProvider(todayBg, todayBg)).cornerRadius(6.dp).padding(vertical = 2.dp)
                    } else {
                        GlanceModifier.defaultWeight().padding(vertical = 2.dp)
                    }

                    Column(modifier = colMod, horizontalAlignment = Alignment.CenterHorizontally) {
                        val myLabel = myInfo?.config?.shortLabel ?: myInfo?.type?.shortLabel ?: "─"
                        val myColor = myInfo?.config?.color ?: myInfo?.type?.color ?: muted.copy(alpha = 0.3f)
                        Text(
                            text = myLabel,
                            style = TextStyle(
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = ColorProvider(myColor, myColor)
                            )
                        )
                        if (myInfo?.hasAlba == true) {
                            Text("+알", style = TextStyle(fontSize = 8.sp, fontWeight = FontWeight.Bold, color = ColorProvider(albaColor, albaColor)))
                        }
                    }
                }
            }

            Spacer(modifier = GlanceModifier.height(2.dp))

            // 구분선
            Box(modifier = GlanceModifier.fillMaxWidth().height(1.dp).background(ColorProvider(divider, divider))) {}

            Spacer(modifier = GlanceModifier.height(2.dp))

            // 상대 일정 Row (작게)
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                for (i in 0..6) {
                    val date = weekStart.plusDays(i.toLong())
                    val dateStr = date.toString()
                    val partnerInfo = partnerShifts[dateStr]

                    Column(
                        modifier = GlanceModifier.defaultWeight(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val pLabel = partnerInfo?.config?.shortLabel ?: partnerInfo?.type?.shortLabel ?: ""
                        val pColor = (partnerInfo?.config?.color ?: partnerInfo?.type?.color)?.copy(alpha = 0.6f) ?: Color.Transparent
                        Text(
                            text = pLabel,
                            style = TextStyle(
                                fontSize = 10.sp,
                                color = ColorProvider(pColor, pColor)
                            )
                        )
                        if (partnerInfo?.hasAlba == true) {
                            Text("+알", style = TextStyle(fontSize = 7.sp, color = ColorProvider(albaColor.copy(alpha = 0.6f), albaColor.copy(alpha = 0.6f))))
                        }
                    }
                }
            }
        }
    }
}

class WeekWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeekWidget()
}
