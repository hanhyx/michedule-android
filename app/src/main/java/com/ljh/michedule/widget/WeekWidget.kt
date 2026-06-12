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

        data class WeekData(
            val myShifts: Map<String, WidgetShiftInfo>,
            val partnerShifts: Map<String, WidgetShiftInfo>,
            val myTypeMap: Map<String, ShiftTypeConfig>,
            val partnerTypeMap: Map<String, ShiftTypeConfig>,
            val weekMemos: Map<String, String>,
            val weekTodos: Map<String, List<String>>,
            val weekMoods: Map<String, String>,
            val partnerName: String
        )

        val data = withContext(Dispatchers.IO) {
            val myTypeConfigs = try {
                db.shiftTypeConfigDao().getAll().associateBy { it.id }
            } catch (_: Exception) { emptyMap() }
            val partnerTypeConfigs = try {
                db.shiftTypeConfigDao().getPartner().associateBy { it.id }
            } catch (_: Exception) { emptyMap() }
            val typeConfigs = ShiftTypeConfig.DEFAULTS.associateBy { it.id } + myTypeConfigs
            val pTypeMap = ShiftTypeConfig.DEFAULTS.associateBy { it.id } + partnerTypeConfigs

            val allShifts = db.shiftDao().getAllShifts()
                .filter { it.date in weekStartStr..weekEndStr }

            val mine = allShifts.associate {
                it.date to WidgetShiftInfo(
                    ShiftType.fromString(it.type),
                    typeConfigs[it.type],
                    it.hasAlba,
                    it.extraShifts
                )
            }

            val memos = allShifts.filter { !it.memo.isNullOrBlank() }
                .associate { it.date to (it.memo ?: "") }

            val todos = mutableMapOf<String, List<String>>()
            val moods = mutableMapOf<String, String>()
            for (i in 0..6) {
                val dateStr = weekStart.plusDays(i.toLong()).toString()
                try {
                    val dayTodos = db.todoDao().getTodosForDate(dateStr).firstOrNull() ?: emptyList()
                    if (dayTodos.isNotEmpty()) {
                        todos[dateStr] = dayTodos.take(3).map { (if (it.isDone) "✅" else "⬜") + it.title }
                    }
                } catch (_: Exception) {}
                try {
                    val mood = db.moodDao().getMoodForDate(dateStr).firstOrNull()
                    if (mood != null) moods[dateStr] = mood.emoji
                } catch (_: Exception) {}
            }

            var pName = ""
            val partner = try {
                val friends = db.friendShiftDao()
                    .getShiftsInRange(weekStartStr, weekEndStr)
                    .firstOrNull() ?: emptyList()
                if (friends.isNotEmpty()) {
                    pName = friends.firstOrNull()?.friendName ?: ""
                }
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

            WeekData(mine, partner, typeConfigs, pTypeMap, memos, todos, moods, pName)
        }

        provideContent {
            WeekWidgetContent(today, weekStart, data.myShifts, data.partnerShifts,
                data.myTypeMap, data.partnerTypeMap, data.weekMemos, data.weekTodos, data.weekMoods, data.partnerName)
        }
    }
}

@Composable
private fun WeekWidgetContent(
    today: LocalDate,
    weekStart: LocalDate,
    myShifts: Map<String, WidgetShiftInfo>,
    partnerShifts: Map<String, WidgetShiftInfo>,
    myTypeMap: Map<String, ShiftTypeConfig> = emptyMap(),
    partnerTypeMap: Map<String, ShiftTypeConfig> = emptyMap(),
    weekMemos: Map<String, String> = emptyMap(),
    weekTodos: Map<String, List<String>> = emptyMap(),
    weekMoods: Map<String, String> = emptyMap(),
    partnerName: String = ""
) {
    val darkBg = Color(0xFF13131E)
    val accent = Color(0xFFB794F6)
    val muted = Color(0xFF4B5563)
    val textPrimary = Color(0xFFE8E8ED)
    val sundayRed = Color(0xFFF87171)
    val saturdayBlue = Color(0xFF60A5FA)
    val todayBg = Color(0xFF2D2640)
    val dividerColor = Color(0xFF2E2E42)
    val memoColor = Color(0xFF7DD3FC)
    val todoColor = Color(0xFF6EE7B7)
    val partnerBg = Color(0xFF1A1A2E)
    val dayNames = listOf("일", "월", "화", "수", "목", "금", "토")

    val hasPartner = partnerName.isNotBlank() || partnerShifts.isNotEmpty()

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(darkBg, darkBg))
            .cornerRadius(20.dp)
            .clickable(actionStartActivity<MainActivity>())
            .padding(2.dp)
    ) {
        Column(modifier = GlanceModifier.fillMaxSize()) {

            // ═══ 내 영역 (70%) ═══
            Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                for (i in 0..6) {
                    val date = weekStart.plusDays(i.toLong())
                    val dateStr = date.toString()
                    val isToday = date == today
                    val dayColor = when {
                        isToday -> accent
                        i == 0 -> sundayRed; i == 6 -> saturdayBlue
                        else -> muted
                    }

                    val myInfo = myShifts[dateStr]
                    val myEmoji = myInfo?.config?.emoji ?: myInfo?.type?.emoji ?: ""
                    val myLabel = myInfo?.config?.shortLabel ?: myInfo?.type?.shortLabel ?: ""
                    val myColor = myInfo?.config?.fontColor ?: myInfo?.config?.color ?: myInfo?.type?.color ?: muted
                    val mood = weekMoods[dateStr]
                    val memos = weekMemos[dateStr]?.split("|||")?.filter { it.isNotBlank() } ?: emptyList()
                    val todos = weekTodos[dateStr] ?: emptyList()

                    val cellBg = if (isToday) todayBg else Color.Transparent

                    Column(
                        modifier = GlanceModifier
                            .defaultWeight()
                            .fillMaxHeight()
                            .background(ColorProvider(cellBg, cellBg))
                            .padding(horizontal = 1.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("${dayNames[i]}${date.dayOfMonth}", style = TextStyle(
                            fontSize = 8.sp,
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                            color = ColorProvider(dayColor, dayColor)
                        ))
                        if (myEmoji.isNotBlank()) {
                            Text(myEmoji, style = TextStyle(fontSize = 11.sp))
                        }
                        if (myLabel.isNotBlank()) {
                            Text(myLabel, style = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Bold, color = ColorProvider(myColor, myColor)), maxLines = 1)
                        }
                        if (mood != null) {
                            Text(mood, style = TextStyle(fontSize = 7.sp))
                        }
                        memos.take(2).forEach { m ->
                            Text(m.trim().take(3), style = TextStyle(fontSize = 7.sp, color = ColorProvider(memoColor, memoColor)), maxLines = 1)
                        }
                        todos.take(2).forEach { t ->
                            val isDone = t.startsWith("✅")
                            val c = if (isDone) muted else todoColor
                            Text(t.drop(1).take(2), style = TextStyle(fontSize = 7.sp, color = ColorProvider(c, c)), maxLines = 1)
                        }
                    }
                }
            }

            // ═══ 구분선 + 상대 이름 ═══
            if (hasPartner) {
                Box(modifier = GlanceModifier.fillMaxWidth().height(1.dp).background(ColorProvider(dividerColor, dividerColor))) {}
                Text(
                    "💕${partnerName.ifBlank { "상대" }}",
                    style = TextStyle(fontSize = 7.sp, fontWeight = FontWeight.Bold, color = ColorProvider(accent, accent))
                )
            }

            // ═══ 상대 영역 (30%) ═══
            if (hasPartner) {
                Row(
                    modifier = GlanceModifier.fillMaxWidth()
                        .background(ColorProvider(partnerBg, partnerBg))
                        .cornerRadius(6.dp)
                ) {
                    for (i in 0..6) {
                        val dateStr = weekStart.plusDays(i.toLong()).toString()
                        val pInfo = partnerShifts[dateStr]
                        val pEmoji = pInfo?.config?.emoji ?: pInfo?.type?.emoji ?: ""
                        val pLabel = pInfo?.config?.shortLabel ?: pInfo?.type?.shortLabel ?: ""
                        val pColor = (pInfo?.config?.color ?: pInfo?.type?.color)?.copy(alpha = 0.85f) ?: muted.copy(alpha = 0.3f)

                        Column(
                            modifier = GlanceModifier.defaultWeight(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(pEmoji.ifBlank { "·" }, style = TextStyle(fontSize = 9.sp))
                            Text(pLabel.ifBlank { "-" }, style = TextStyle(fontSize = 8.sp, fontWeight = FontWeight.Bold, color = ColorProvider(pColor, pColor)), maxLines = 1)
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
