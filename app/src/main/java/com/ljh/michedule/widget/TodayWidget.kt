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
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class WidgetShiftInfo(
    val type: ShiftType?,
    val hasAlba: Boolean = false
)

class TodayWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val today = LocalDate.now()
        val todayStr = today.toString()
        val db = AppDatabase.getInstance(context)

        val (myInfo, partnerInfo) = withContext(Dispatchers.IO) {
            val myEntity = db.shiftDao().getShift(todayStr)
            val my = WidgetShiftInfo(
                type = myEntity?.let { ShiftType.fromString(it.type) },
                hasAlba = myEntity?.hasAlba ?: false
            )

            val partner = try {
                val friends = db.friendShiftDao()
                    .getShiftsInRange(todayStr, todayStr)
                    .firstOrNull()
                val fe = friends?.firstOrNull()
                WidgetShiftInfo(
                    type = fe?.let { ShiftType.fromString(it.type) },
                    hasAlba = fe?.hasAlba ?: false
                )
            } catch (_: Exception) { WidgetShiftInfo(null) }

            my to partner
        }

        provideContent {
            TodayWidgetContent(today, myInfo, partnerInfo)
        }
    }
}

@Composable
private fun TodayWidgetContent(
    today: LocalDate,
    myInfo: WidgetShiftInfo,
    partnerInfo: WidgetShiftInfo
) {
    val darkBg = Color(0xFF0F0F1A)
    val muted = Color(0xFF6B7280)
    val purple = Color(0xFFA78BFA)
    val albaColor = Color(0xFFF97316)
    val divider = Color(0xFF2A2A3D)
    val myShift = myInfo.type
    val partnerShift = partnerInfo.type

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(darkBg, darkBg))
            .cornerRadius(16.dp)
            .clickable(actionStartActivity<MainActivity>())
            .padding(12.dp)
    ) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            // 날짜 헤더
            Text(
                text = today.format(DateTimeFormatter.ofPattern("M월 d일 (E)")),
                style = TextStyle(color = ColorProvider(purple, purple), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            )

            Spacer(modifier = GlanceModifier.height(8.dp))

            // 내 일정 (메인)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = myShift?.emoji ?: "📋",
                    style = TextStyle(fontSize = 22.sp)
                )
                Spacer(modifier = GlanceModifier.width(8.dp))
                Column {
                    Text(
                        text = myShift?.label ?: "미설정",
                        style = TextStyle(
                            color = ColorProvider(myShift?.color ?: muted, myShift?.color ?: muted),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    if (myShift != null) {
                        Text(
                            text = myShift.timeRange,
                            style = TextStyle(
                                color = ColorProvider(myShift.color.copy(alpha = 0.6f), myShift.color.copy(alpha = 0.6f)),
                                fontSize = 10.sp
                            )
                        )
                    }
                }
            }

            // 알바 표시
            if (myInfo.hasAlba) {
                Spacer(modifier = GlanceModifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(modifier = GlanceModifier.width(30.dp))
                    Text(
                        text = "+ 알바 ${ShiftType.ALBA.timeRange}",
                        style = TextStyle(
                            color = ColorProvider(albaColor, albaColor),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }

            Spacer(modifier = GlanceModifier.defaultWeight())

            // 구분선
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(ColorProvider(divider, divider))
            ) {}
            Spacer(modifier = GlanceModifier.height(6.dp))

            // 상대 일정 (작게 한 줄)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("👤", style = TextStyle(fontSize = 12.sp))
                Spacer(modifier = GlanceModifier.width(6.dp))
                Text(
                    text = "상대",
                    style = TextStyle(color = ColorProvider(muted, muted), fontSize = 10.sp)
                )
                Spacer(modifier = GlanceModifier.width(6.dp))
                Text(
                    text = partnerShift?.label ?: "미설정",
                    style = TextStyle(
                        color = ColorProvider(partnerShift?.color ?: muted.copy(alpha = 0.5f), partnerShift?.color ?: muted.copy(alpha = 0.5f)),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                if (partnerInfo.hasAlba) {
                    Spacer(modifier = GlanceModifier.width(4.dp))
                    Text(
                        text = "+알바",
                        style = TextStyle(
                            color = ColorProvider(albaColor.copy(alpha = 0.7f), albaColor.copy(alpha = 0.7f)),
                            fontSize = 10.sp
                        )
                    )
                }
            }
        }
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}
