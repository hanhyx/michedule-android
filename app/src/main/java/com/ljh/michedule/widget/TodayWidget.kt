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
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class TodayWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val today = LocalDate.now()
        val db = AppDatabase.getInstance(context)
        val shift = withContext(Dispatchers.IO) {
            db.shiftDao().getShift(today.toString())
        }
        val shiftType = shift?.let { ShiftType.fromString(it.type) }

        provideContent {
            TodayWidgetContent(today, shiftType)
        }
    }
}

@Composable
private fun TodayWidgetContent(today: LocalDate, shift: ShiftType?) {
    val darkBg = Color(0xFF16161E)
    val muted = Color(0xFF9CA3AF)
    val textColor = shift?.color ?: Color(0xFFE8E8ED)

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(darkBg, darkBg))
            .cornerRadius(16.dp)
            .clickable(actionStartActivity<MainActivity>())
            .padding(12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = GlanceModifier.fillMaxWidth()
        ) {
            Text(
                text = shift?.emoji ?: "📅",
                style = TextStyle(fontSize = 24.sp)
            )
            Spacer(modifier = GlanceModifier.width(10.dp))
            Column {
                Text(
                    text = today.format(DateTimeFormatter.ofPattern("M/d (E)")),
                    style = TextStyle(
                        color = ColorProvider(muted, muted),
                        fontSize = 12.sp
                    )
                )
                Text(
                    text = shift?.label ?: "미설정",
                    style = TextStyle(
                        color = ColorProvider(textColor, textColor),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                if (shift != null) {
                    Text(
                        text = shift.timeRange,
                        style = TextStyle(
                            color = ColorProvider(
                                textColor.copy(alpha = 0.7f),
                                textColor.copy(alpha = 0.7f)
                            ),
                            fontSize = 11.sp
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
