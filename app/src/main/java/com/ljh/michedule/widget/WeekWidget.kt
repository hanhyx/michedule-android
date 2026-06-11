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
import com.ljh.michedule.data.db.ShiftTypeConfig
import com.ljh.michedule.model.ShiftType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext

class WeekWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val today = LocalDate.now()
        val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
        val weekEnd = weekStart.plusDays(6)
        val weekStartStr = weekStart.toString()
        val weekEndStr = weekEnd.toString()
        val db = AppDatabase.getInstance(context)

        val (myShifts, partnerShifts) = withContext(Dispatchers.IO) {
            val myTypeConfigs = try {
                db.shiftTypeConfigDao().getAll().associateBy { it.id }
            } catch (_: Exception) { emptyMap() }
            val partnerTypeConfigs = try {
                db.shiftTypeConfigDao().getPartner().associateBy { it.id }
            } catch (_: Exception) { emptyMap() }
            val typeConfigs = ShiftTypeConfig.DEFAULTS.associateBy { it.id } + myTypeConfigs
            val pTypeMap = ShiftTypeConfig.DEFAULTS.associateBy { it.id } + partnerTypeConfigs

            val mine = db.shiftDao().getAllShifts()
                .filter { it.date in weekStartStr..weekEndStr }
                .associate {
                    it.date to WidgetShiftInfo(
                        ShiftType.fromString(it.type),
                        typeConfigs[it.type],
                        it.hasAlba,
                        it.extraShifts
                    )
                }

            val partner = try {
                val friends = db.friendShiftDao()
                    .getShiftsInRange(weekStartStr, weekEndStr)
                    .firstOrNull() ?: emptyList()
                friends.associate {
                    it.date to WidgetShiftInfo(
                        ShiftType.fromString(it.type),
                        pTypeMap[it.type],
                        it.hasAlba,
                        it.extraShifts
                    )
                }
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
    val darkBg = Color(0xFF13131E)
    val cardBg = Color(0xFF1C1C2E)
    val accent = Color(0xFFB794F6)
    val muted = Color(0xFF4B5563)
    val textPrimary = Color(0xFFE8E8ED)
    val sundayRed = Color(0xFFF87171)
    val saturdayBlue = Color(0xFF60A5FA)
    val todayBg = Color(0xFF2D2640)
    val albaColor = Color(0xFFFB923C)
    val divider = Color(0xFF2E2E42)
    val dayNames = listOf("일", "월", "화", "수", "목", "금", "토")

    val hasPartner = partnerShifts.isNotEmpty()

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(darkBg, darkBg))
            .cornerRadius(20.dp)
            .clickable(actionStartActivity<MainActivity>())
            .padding(horizontal = 6.dp, vertical = 8.dp)
    ) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            // 요일 헤더
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                for (i in 0..6) {
                    val dayColor = when (i) {
                        0 -> sundayRed
                        6 -> saturdayBlue
                        else -> muted
                    }
                    Box(
                        modifier = GlanceModifier.defaultWeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            dayNames[i],
                            style = TextStyle(
                                fontSize = 9.sp,
                                color = ColorProvider(dayColor, dayColor)
                            )
                        )
                    }
                }
            }

            Spacer(modifier = GlanceModifier.height(2.dp))

            // 날짜 행
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                for (i in 0..6) {
                    val date = weekStart.plusDays(i.toLong())
                    val isToday = date == today
                    val dateColor = when {
                        isToday -> accent
                        i == 0 -> sundayRed.copy(alpha = 0.8f)
                        i == 6 -> saturdayBlue.copy(alpha = 0.8f)
                        else -> textPrimary
                    }
                    Box(
                        modifier = GlanceModifier.defaultWeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "${date.dayOfMonth}",
                            style = TextStyle(
                                fontSize = 11.sp,
                                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                                color = ColorProvider(dateColor, dateColor)
                            )
                        )
                    }
                }
            }

            Spacer(modifier = GlanceModifier.height(4.dp))

            // 내 일정 카드 행
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                for (i in 0..6) {
                    val date = weekStart.plusDays(i.toLong())
                    val dateStr = date.toString()
                    val myInfo = myShifts[dateStr]
                    val isToday = date == today

                    val myLabel = myInfo?.config?.shortLabel ?: myInfo?.type?.shortLabel ?: ""
                    val myColor = myInfo?.config?.color ?: myInfo?.type?.color ?: muted
                    val myEmoji = myInfo?.config?.emoji ?: myInfo?.type?.emoji ?: ""

                    val cellBg = when {
                        isToday -> todayBg
                        myLabel.isNotBlank() -> cardBg
                        else -> Color.Transparent
                    }

                    Box(
                        modifier = GlanceModifier
                            .defaultWeight()
                            .cornerRadius(8.dp)
                            .background(ColorProvider(cellBg, cellBg))
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            if (myEmoji.isNotBlank()) {
                                Text(myEmoji, style = TextStyle(fontSize = 12.sp))
                            }
                            Text(
                                text = myLabel.ifBlank { "─" },
                                style = TextStyle(
                                    fontSize = if (myLabel.isNotBlank()) 14.sp else 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ColorProvider(
                                        if (myLabel.isNotBlank()) myColor else muted.copy(alpha = 0.3f),
                                        if (myLabel.isNotBlank()) myColor else muted.copy(alpha = 0.3f)
                                    )
                                )
                            )
                            val wExtras = myInfo?.getExtraShiftList() ?: emptyList()
                            val wDisplay = wExtras.ifEmpty { if (myInfo?.hasAlba == true) listOf("alba") else emptyList() }
                            if (wDisplay.isNotEmpty()) {
                                Text(
                                    "💼",
                                    style = TextStyle(fontSize = 8.sp)
                                )
                            }
                        }
                    }
                }
            }

            // 상대 영역
            if (hasPartner) {
                Spacer(modifier = GlanceModifier.height(4.dp))
                Box(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(ColorProvider(divider, divider))
                ) {}
                Spacer(modifier = GlanceModifier.height(3.dp))

                Row(modifier = GlanceModifier.fillMaxWidth()) {
                    for (i in 0..6) {
                        val date = weekStart.plusDays(i.toLong())
                        val dateStr = date.toString()
                        val partnerInfo = partnerShifts[dateStr]
                        val pLabel = partnerInfo?.config?.shortLabel
                            ?: partnerInfo?.type?.shortLabel ?: ""
                        val pColor = (partnerInfo?.config?.color ?: partnerInfo?.type?.color)
                            ?.copy(alpha = 0.7f) ?: Color.Transparent

                        val pExtras = partnerInfo?.getExtraShiftList() ?: emptyList()
                        val pHasExtra = pExtras.isNotEmpty() || (partnerInfo?.hasAlba == true)

                        Box(
                            modifier = GlanceModifier.defaultWeight(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                if (pLabel.isNotBlank()) {
                                    Box(
                                        modifier = GlanceModifier
                                            .cornerRadius(4.dp)
                                            .background(ColorProvider(pColor.copy(alpha = 0.15f), pColor.copy(alpha = 0.15f)))
                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                                    ) {
                                        Text(
                                            pLabel,
                                            style = TextStyle(
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = ColorProvider(pColor, pColor)
                                            )
                                        )
                                    }
                                }
                                if (pHasExtra) {
                                    Text(
                                        "💼",
                                        style = TextStyle(fontSize = 8.sp)
                                    )
                                }
                            }
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
