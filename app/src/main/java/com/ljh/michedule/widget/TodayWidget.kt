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

class TodayWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val today = LocalDate.now()
        val todayStr = today.toString()
        val db = AppDatabase.getInstance(context)

        val (myShift, partnerShift) = withContext(Dispatchers.IO) {
            val my = db.shiftDao().getShift(todayStr)
                ?.let { ShiftType.fromString(it.type) }

            val partner = try {
                val friends = db.friendShiftDao()
                    .getShiftsInRange(todayStr, todayStr)
                    .firstOrNull()
                friends?.firstOrNull()?.let { ShiftType.fromString(it.type) }
            } catch (_: Exception) { null }

            my to partner
        }

        provideContent {
            TodayWidgetContent(today, myShift, partnerShift)
        }
    }
}

@Composable
private fun TodayWidgetContent(
    today: LocalDate,
    myShift: ShiftType?,
    partnerShift: ShiftType?
) {
    val darkBg = Color(0xFF0F0F1A)
    val muted = Color(0xFF6B7280)
    val purple = Color(0xFFA78BFA)

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

            Spacer(modifier = GlanceModifier.height(8.dp))

            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = myShift?.emoji ?: "📋",
                    style = TextStyle(fontSize = 20.sp)
                )
                Spacer(modifier = GlanceModifier.width(6.dp))
                Column {
                    Text(
                        text = "나",
                        style = TextStyle(
                            color = ColorProvider(muted, muted),
                            fontSize = 9.sp
                        )
                    )
                    Text(
                        text = myShift?.label ?: "미설정",
                        style = TextStyle(
                            color = ColorProvider(
                                myShift?.color ?: muted,
                                myShift?.color ?: muted
                            ),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
                if (myShift != null) {
                    Spacer(modifier = GlanceModifier.defaultWeight())
                    Text(
                        text = myShift.timeRange,
                        style = TextStyle(
                            color = ColorProvider(
                                myShift.color.copy(alpha = 0.6f),
                                myShift.color.copy(alpha = 0.6f)
                            ),
                            fontSize = 10.sp
                        )
                    )
                }
            }

            Spacer(modifier = GlanceModifier.height(6.dp))

            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = partnerShift?.emoji ?: "👤",
                    style = TextStyle(fontSize = 20.sp)
                )
                Spacer(modifier = GlanceModifier.width(6.dp))
                Column {
                    Text(
                        text = "상대",
                        style = TextStyle(
                            color = ColorProvider(muted, muted),
                            fontSize = 9.sp
                        )
                    )
                    Text(
                        text = partnerShift?.label ?: "미설정",
                        style = TextStyle(
                            color = ColorProvider(
                                partnerShift?.color ?: muted,
                                partnerShift?.color ?: muted
                            ),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
                if (partnerShift != null) {
                    Spacer(modifier = GlanceModifier.defaultWeight())
                    Text(
                        text = partnerShift.timeRange,
                        style = TextStyle(
                            color = ColorProvider(
                                partnerShift.color.copy(alpha = 0.6f),
                                partnerShift.color.copy(alpha = 0.6f)
                            ),
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
