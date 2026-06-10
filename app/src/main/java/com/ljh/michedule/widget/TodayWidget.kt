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

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(darkBg, darkBg))
            .cornerRadius(20.dp)
            .clickable(actionStartActivity<MainActivity>())
            .padding(12.dp)
    ) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            Text(
                text = today.format(DateTimeFormatter.ofPattern("M월 d일 EEEE")),
                style = TextStyle(
                    color = ColorProvider(purple, purple),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            )

            Spacer(modifier = GlanceModifier.height(6.dp))

            WidgetShiftRow("나", myInfo, muted, albaColor)

            Spacer(modifier = GlanceModifier.height(4.dp))

            WidgetShiftRow("상대", partnerInfo, muted, albaColor)
        }
    }
}

@Composable
private fun WidgetShiftRow(
    label: String,
    info: WidgetShiftInfo,
    muted: Color,
    albaColor: Color
) {
    val shift = info.type
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = shift?.emoji ?: if (label == "나") "📋" else "👤",
            style = TextStyle(fontSize = 18.sp)
        )
        Spacer(modifier = GlanceModifier.width(6.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = label,
                style = TextStyle(color = ColorProvider(muted, muted), fontSize = 9.sp)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = shift?.label ?: "미설정",
                    style = TextStyle(
                        color = ColorProvider(shift?.color ?: muted, shift?.color ?: muted),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                if (info.hasAlba) {
                    Spacer(modifier = GlanceModifier.width(4.dp))
                    Text(
                        text = "+알바",
                        style = TextStyle(
                            color = ColorProvider(albaColor, albaColor),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }
        if (shift != null) {
            Text(
                text = shift.timeRange,
                style = TextStyle(
                    color = ColorProvider(
                        shift.color.copy(alpha = 0.6f),
                        shift.color.copy(alpha = 0.6f)
                    ),
                    fontSize = 9.sp
                )
            )
        }
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}
