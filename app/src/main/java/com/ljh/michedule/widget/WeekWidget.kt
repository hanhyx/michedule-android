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
    val cardBg = Color(0xFF1C1C2E)
    val accent = Color(0xFFB794F6)
    val muted = Color(0xFF4B5563)
    val textPrimary = Color(0xFFE8E8ED)
    val sundayRed = Color(0xFFF87171)
    val saturdayBlue = Color(0xFF60A5FA)
    val todayBg = Color(0xFF2D2640)
    val divider = Color(0xFF2E2E42)
    val memoColor = Color(0xFF7DD3FC)
    val todoColor = Color(0xFF6EE7B7)
    val dayNames = listOf("일", "월", "화", "수", "목", "금", "토")

    val hasPartner = partnerName.isNotBlank() || partnerShifts.isNotEmpty()

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(darkBg, darkBg))
            .cornerRadius(20.dp)
            .clickable(actionStartActivity<MainActivity>())
            .padding(horizontal = 4.dp, vertical = 6.dp)
    ) {
        // 날짜별 세로 칼럼 레이아웃
        Row(modifier = GlanceModifier.fillMaxSize()) {
            for (i in 0..6) {
                val date = weekStart.plusDays(i.toLong())
                val dateStr = date.toString()
                val isToday = date == today
                val dayColor = when {
                    isToday -> accent
                    i == 0 -> sundayRed
                    i == 6 -> saturdayBlue
                    else -> muted
                }
                val dateColor = when {
                    isToday -> accent
                    i == 0 -> sundayRed.copy(alpha = 0.8f)
                    i == 6 -> saturdayBlue.copy(alpha = 0.8f)
                    else -> textPrimary
                }

                // 내 정보
                val myInfo = myShifts[dateStr]
                val myLabel = myInfo?.config?.shortLabel ?: myInfo?.type?.shortLabel ?: ""
                val myColor = myInfo?.config?.fontColor ?: myInfo?.config?.color ?: myInfo?.type?.color ?: muted
                val myEmoji = myInfo?.config?.emoji ?: myInfo?.type?.emoji ?: ""
                val mood = weekMoods[dateStr]

                // 상대 정보
                val partnerInfo = partnerShifts[dateStr]
                val pLabel = partnerInfo?.config?.label ?: partnerInfo?.type?.label ?: ""
                val pShort = partnerInfo?.config?.shortLabel ?: partnerInfo?.type?.shortLabel ?: ""
                val pColor = (partnerInfo?.config?.color ?: partnerInfo?.type?.color) ?: muted
                val pEmoji = partnerInfo?.config?.emoji ?: partnerInfo?.type?.emoji ?: ""

                // 메모/할일
                val memos = weekMemos[dateStr]?.split("|||")?.filter { it.isNotBlank() } ?: emptyList()
                val todos = weekTodos[dateStr] ?: emptyList()

                val cellBg = if (isToday) todayBg else Color.Transparent

                // 구분선 (첫 칼럼 제외)
                if (i > 0) {
                    Box(
                        modifier = GlanceModifier
                            .fillMaxHeight()
                            .width(1.dp)
                            .background(ColorProvider(divider, divider))
                    ) {}
                }

                Column(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .fillMaxHeight()
                        .cornerRadius(4.dp)
                        .background(ColorProvider(cellBg, cellBg))
                        .padding(horizontal = 2.dp, vertical = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 요일 + 날짜
                    Text(dayNames[i], style = TextStyle(fontSize = 8.sp, color = ColorProvider(dayColor, dayColor)))
                    Text(
                        "${date.dayOfMonth}",
                        style = TextStyle(
                            fontSize = 10.sp,
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                            color = ColorProvider(dateColor, dateColor)
                        )
                    )

                    Spacer(modifier = GlanceModifier.height(2.dp))

                    // ── 내 근무 ──
                    if (myEmoji.isNotBlank()) {
                        Text(myEmoji, style = TextStyle(fontSize = 12.sp))
                    }
                    if (myLabel.isNotBlank()) {
                        Text(
                            myLabel,
                            style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ColorProvider(myColor, myColor))
                        )
                    }
                    if (mood != null) {
                        Text(mood, style = TextStyle(fontSize = 8.sp))
                    }

                    // ── 메모 ──
                    memos.forEach { m ->
                        Text(
                            m.trim().take(6),
                            style = TextStyle(color = ColorProvider(memoColor, memoColor), fontSize = 7.sp),
                            maxLines = 1
                        )
                    }

                    // ── 할일 ──
                    todos.forEach { t ->
                        val isDone = t.startsWith("✅")
                        val c = if (isDone) muted else todoColor
                        Text(
                            t.take(6),
                            style = TextStyle(color = ColorProvider(c, c), fontSize = 7.sp),
                            maxLines = 1
                        )
                    }

                    Spacer(modifier = GlanceModifier.defaultWeight())

                    // ── 상대 근무 ──
                    if (hasPartner) {
                        Box(
                            modifier = GlanceModifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(ColorProvider(divider, divider))
                        ) {}
                        Spacer(modifier = GlanceModifier.height(1.dp))
                        if (pEmoji.isNotBlank()) {
                            Text(pEmoji, style = TextStyle(fontSize = 10.sp))
                        }
                        if (pShort.isNotBlank()) {
                            Text(
                                pShort,
                                style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold, color = ColorProvider(pColor.copy(alpha = 0.8f), pColor.copy(alpha = 0.8f)))
                            )
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
