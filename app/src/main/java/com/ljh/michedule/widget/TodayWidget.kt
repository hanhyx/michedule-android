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

data class WidgetDayDetail(
    val shift: WidgetShiftInfo,
    val memo: String? = null,
    val mood: String? = null,
    val todoTexts: List<String> = emptyList(),
    val name: String = ""
)

class TodayWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val today = LocalDate.now()
        val todayStr = today.toString()
        val db = AppDatabase.getInstance(context)

        val (myDetail, partnerDetail) = withContext(Dispatchers.IO) {
            val myEntity = db.shiftDao().getShift(todayStr)
            val myMood = try { db.moodDao().getMoodForDate(todayStr).firstOrNull() } catch (_: Exception) { null }
            val myTodos = try { db.todoDao().getTodosForDate(todayStr).firstOrNull() ?: emptyList() } catch (_: Exception) { emptyList() }

            val my = WidgetDayDetail(
                shift = WidgetShiftInfo(
                    type = myEntity?.let { ShiftType.fromString(it.type) },
                    hasAlba = myEntity?.hasAlba ?: false
                ),
                memo = myEntity?.memo,
                mood = myMood?.emoji,
                todoTexts = myTodos.take(2).map { (if (it.isDone) "✅" else "⬜") + it.title }
            )

            val partner = try {
                val friends = db.friendShiftDao().getShiftsInRange(todayStr, todayStr).firstOrNull()
                val fe = friends?.firstOrNull()
                WidgetDayDetail(
                    shift = WidgetShiftInfo(
                        type = fe?.let { ShiftType.fromString(it.type) },
                        hasAlba = fe?.hasAlba ?: false
                    ),
                    memo = fe?.memo,
                    mood = fe?.mood,
                    name = fe?.friendName ?: "상대"
                )
            } catch (_: Exception) { WidgetDayDetail(WidgetShiftInfo(null)) }

            my to partner
        }

        provideContent {
            TodayWidgetContent(today, myDetail, partnerDetail)
        }
    }
}

@Composable
private fun TodayWidgetContent(
    today: LocalDate,
    myDetail: WidgetDayDetail,
    partnerDetail: WidgetDayDetail
) {
    val darkBg = Color(0xFF0F0F1A)
    val muted = Color(0xFF6B7280)
    val purple = Color(0xFFA78BFA)
    val albaColor = Color(0xFFF97316)
    val divider = Color(0xFF2A2A3D)
    val memoColor = Color(0xFF60A5FA)
    val todoColor = Color(0xFF34D399)
    val myShift = myDetail.shift.type
    val partnerShift = partnerDetail.shift.type

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(darkBg, darkBg))
            .cornerRadius(16.dp)
            .clickable(actionStartActivity<MainActivity>())
            .padding(10.dp)
    ) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            // 날짜 헤더
            Text(
                text = today.format(DateTimeFormatter.ofPattern("M월 d일 (E)")),
                style = TextStyle(color = ColorProvider(purple, purple), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = GlanceModifier.height(4.dp))

            // ── 내 영역 (7) ──
            Column(modifier = GlanceModifier.defaultWeight()) {
                // 근무
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(myShift?.emoji ?: "📋", style = TextStyle(fontSize = 14.sp))
                    Spacer(modifier = GlanceModifier.width(4.dp))
                    Text(
                        text = myShift?.label ?: "미설정",
                        style = TextStyle(color = ColorProvider(myShift?.color ?: muted, myShift?.color ?: muted), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    )
                    if (myShift != null) {
                        Spacer(modifier = GlanceModifier.width(4.dp))
                        Text(myShift.timeRange, style = TextStyle(color = ColorProvider(myShift.color.copy(alpha = 0.5f), myShift.color.copy(alpha = 0.5f)), fontSize = 9.sp))
                    }
                }

                // 알바
                if (myDetail.shift.hasAlba) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("💼", style = TextStyle(fontSize = 10.sp))
                        Spacer(modifier = GlanceModifier.width(4.dp))
                        Text("알바", style = TextStyle(color = ColorProvider(albaColor, albaColor), fontSize = 11.sp, fontWeight = FontWeight.Bold))
                        Spacer(modifier = GlanceModifier.width(4.dp))
                        Text(ShiftType.ALBA.timeRange, style = TextStyle(color = ColorProvider(albaColor.copy(alpha = 0.6f), albaColor.copy(alpha = 0.6f)), fontSize = 9.sp))
                    }
                }

                // 메모
                if (!myDetail.memo.isNullOrBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("📝", style = TextStyle(fontSize = 10.sp))
                        Spacer(modifier = GlanceModifier.width(4.dp))
                        Text(myDetail.memo, style = TextStyle(color = ColorProvider(memoColor, memoColor), fontSize = 10.sp))
                    }
                }

                // 할일
                myDetail.todoTexts.forEach { todo ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(todo, style = TextStyle(color = ColorProvider(todoColor, todoColor), fontSize = 9.sp))
                    }
                }

                // 감정
                if (myDetail.mood != null) {
                    Text(myDetail.mood, style = TextStyle(fontSize = 12.sp))
                }
            }

            // 구분선
            Box(modifier = GlanceModifier.fillMaxWidth().height(1.dp).background(ColorProvider(divider, divider))) {}
            Spacer(modifier = GlanceModifier.height(3.dp))

            // ── 상대 영역 (3) ──
            val partnerName = partnerDetail.name.ifBlank { "상대" }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("👤 $partnerName", style = TextStyle(color = ColorProvider(muted, muted), fontSize = 9.sp))
            }
            Spacer(modifier = GlanceModifier.height(2.dp))
            // 근무
            if (partnerShift != null) {
                Box(
                    modifier = GlanceModifier
                        .cornerRadius(4.dp)
                        .background(ColorProvider(partnerShift.color.copy(alpha = 0.85f), partnerShift.color.copy(alpha = 0.85f)))
                        .padding(horizontal = 6.dp, vertical = 1.dp)
                ) {
                    Text(partnerShift.label, style = TextStyle(color = ColorProvider(Color.White, Color.White), fontSize = 10.sp, fontWeight = FontWeight.Bold))
                }
            }
            // 알바
            if (partnerDetail.shift.hasAlba) {
                Spacer(modifier = GlanceModifier.height(2.dp))
                Box(
                    modifier = GlanceModifier
                        .cornerRadius(4.dp)
                        .background(ColorProvider(albaColor.copy(alpha = 0.85f), albaColor.copy(alpha = 0.85f)))
                        .padding(horizontal = 6.dp, vertical = 1.dp)
                ) {
                    Text("알바", style = TextStyle(color = ColorProvider(Color.White, Color.White), fontSize = 9.sp, fontWeight = FontWeight.Bold))
                }
            }
            // 메모
            if (!partnerDetail.memo.isNullOrBlank()) {
                Spacer(modifier = GlanceModifier.height(2.dp))
                Box(
                    modifier = GlanceModifier
                        .cornerRadius(4.dp)
                        .background(ColorProvider(memoColor.copy(alpha = 0.7f), memoColor.copy(alpha = 0.7f)))
                        .padding(horizontal = 6.dp, vertical = 1.dp)
                ) {
                    Text(partnerDetail.memo, style = TextStyle(color = ColorProvider(Color.White, Color.White), fontSize = 9.sp))
                }
            }
        }
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}
