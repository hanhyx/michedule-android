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
    val rightBg = Color(0xFF181825)
    val accent = Color(0xFFB794F6)
    val muted = Color(0xFF6B7280)
    val dimText = Color(0xFF8888AA)
    val white = Color(0xFFFFFFFF)
    val albaColor = Color(0xFFFB923C)
    val sundayRed = Color(0xFFF87171)
    val saturdayBlue = Color(0xFF60A5FA)
    val todayHighlight = Color(0xFF2D2640)
    val memoColor = Color(0xFF7DD3FC)
    val todoColor = Color(0xFF6EE7B7)
    val moodColor = Color(0xFFFBBF24)
    val dayNames = listOf("일", "월", "화", "수", "목", "금", "토")

    val todayStr = today.toString()
    val todayInfo = myShifts[todayStr]
    val myConfig = todayInfo?.config
    val myShift = todayInfo?.type
    val myColor = myConfig?.color ?: myShift?.color ?: muted
    val myLabel = myConfig?.label ?: myShift?.label ?: "미설정"
    val myEmoji = myConfig?.emoji ?: myShift?.emoji ?: "📋"
    val myTime = myConfig?.defaultTimeRange ?: myShift?.defaultTimeRange ?: ""
    val todayMemos = weekMemos[todayStr]?.split("|||")?.filter { it.isNotBlank() } ?: emptyList()
    val todayTodos = weekTodos[todayStr] ?: emptyList()
    val todayMood = weekMoods[todayStr]

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(darkBg, darkBg))
            .cornerRadius(20.dp)
            .clickable(actionStartActivity<MainActivity>())
            .padding(6.dp)
    ) {
        Row(modifier = GlanceModifier.fillMaxSize()) {

            // ══ 왼쪽: 2x2 동일 컨텐츠 ══
            Column(
                modifier = GlanceModifier
                    .fillMaxHeight()
                    .defaultWeight()
            ) {
                // 날짜 + 감정
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        today.format(java.time.format.DateTimeFormatter.ofPattern("M/d (E)")),
                        style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ColorProvider(accent, accent))
                    )
                    if (todayMood != null) {
                        Spacer(modifier = GlanceModifier.width(4.dp))
                        Text(todayMood, style = TextStyle(fontSize = 12.sp))
                    }
                }
                Spacer(modifier = GlanceModifier.height(4.dp))

                // 근무 카드
                Box(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .cornerRadius(12.dp)
                        .background(ColorProvider(cardBg, cardBg))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(myEmoji, style = TextStyle(fontSize = 18.sp))
                            Spacer(modifier = GlanceModifier.width(6.dp))
                            Column {
                                Text(myLabel, style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ColorProvider(myColor, myColor)))
                                if (myTime.isNotBlank()) {
                                    Text(myTime, style = TextStyle(fontSize = 9.sp, color = ColorProvider(muted, muted)))
                                }
                            }
                        }
                        // 추가근무
                        val myExtras = todayInfo?.getExtraShiftList() ?: emptyList()
                        myExtras.take(2).forEach { extraId ->
                            val ec = myTypeMap[extraId]
                            val ecEmoji = ec?.emoji ?: "💼"
                            val ecLabel = ec?.label ?: extraId
                            val ecColor = ec?.color ?: albaColor
                            Spacer(modifier = GlanceModifier.height(3.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(ecEmoji, style = TextStyle(fontSize = 10.sp))
                                Spacer(modifier = GlanceModifier.width(3.dp))
                                Text(ecLabel, style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold, color = ColorProvider(ecColor, ecColor)))
                            }
                        }
                    }
                }

                Spacer(modifier = GlanceModifier.height(4.dp))

                // 메모
                todayMemos.take(2).forEach { m ->
                    Text("📝 ${m.trim().take(20)}", style = TextStyle(fontSize = 9.sp, color = ColorProvider(memoColor, memoColor)), maxLines = 1)
                    Spacer(modifier = GlanceModifier.height(1.dp))
                }
                // 할일
                todayTodos.take(3).forEach { t ->
                    val isDone = t.startsWith("✅")
                    val c = if (isDone) muted else todoColor
                    Text(t.take(20), style = TextStyle(fontSize = 9.sp, color = ColorProvider(c, c)), maxLines = 1)
                }
            }

            Spacer(modifier = GlanceModifier.width(6.dp))

            // ══ 오른쪽: 주간 아이콘 그리드 ══
            Column(
                modifier = GlanceModifier
                    .fillMaxHeight()
                    .defaultWeight()
                    .cornerRadius(12.dp)
                    .background(ColorProvider(rightBg, rightBg))
                    .padding(4.dp)
            ) {
                // 요일 헤더
                Row(modifier = GlanceModifier.fillMaxWidth()) {
                    for (i in 0..6) {
                        val date = weekStart.plusDays(i.toLong())
                        val isToday = date == today
                        val color = when {
                            isToday -> accent
                            i == 0 -> sundayRed; i == 6 -> saturdayBlue
                            else -> dimText
                        }
                        Box(modifier = GlanceModifier.defaultWeight(), contentAlignment = Alignment.Center) {
                            Text(
                                "${dayNames[i]}${date.dayOfMonth}",
                                style = TextStyle(
                                    fontSize = 7.sp,
                                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                                    color = ColorProvider(color, color)
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = GlanceModifier.height(2.dp))

                // 근무 이모지 행
                Row(modifier = GlanceModifier.fillMaxWidth()) {
                    for (i in 0..6) {
                        val dateStr = weekStart.plusDays(i.toLong()).toString()
                        val isToday = weekStart.plusDays(i.toLong()) == today
                        val info = myShifts[dateStr]
                        val emoji = info?.config?.emoji ?: info?.type?.emoji ?: ""
                        val cellBg = if (isToday) todayHighlight else Color.Transparent
                        Box(
                            modifier = GlanceModifier.defaultWeight().cornerRadius(4.dp)
                                .background(ColorProvider(cellBg, cellBg)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(emoji.ifBlank { "·" }, style = TextStyle(fontSize = 14.sp))
                        }
                    }
                }

                // 근무 약자 행 (색상 포함)
                Row(modifier = GlanceModifier.fillMaxWidth()) {
                    for (i in 0..6) {
                        val dateStr = weekStart.plusDays(i.toLong()).toString()
                        val info = myShifts[dateStr]
                        val label = info?.config?.shortLabel ?: info?.type?.shortLabel ?: ""
                        val fontColor = info?.config?.fontColor ?: info?.config?.color ?: info?.type?.color ?: muted
                        Box(modifier = GlanceModifier.defaultWeight(), contentAlignment = Alignment.Center) {
                            Text(
                                label.ifBlank { "-" },
                                style = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Bold, color = ColorProvider(fontColor, fontColor)),
                                maxLines = 1
                            )
                        }
                    }
                }

                Spacer(modifier = GlanceModifier.height(2.dp))

                // 추가근무 행 (있는 날만 이모지+약자, 색상)
                Row(modifier = GlanceModifier.fillMaxWidth()) {
                    for (i in 0..6) {
                        val dateStr = weekStart.plusDays(i.toLong()).toString()
                        val info = myShifts[dateStr]
                        val extras = info?.getExtraShiftList() ?: emptyList()
                        val exConfig = if (extras.isNotEmpty()) myTypeMap[extras.first()] else null

                        Box(modifier = GlanceModifier.defaultWeight(), contentAlignment = Alignment.Center) {
                            if (exConfig != null) {
                                Text(
                                    "+${exConfig.emoji}",
                                    style = TextStyle(fontSize = 8.sp, color = ColorProvider(exConfig.color, exConfig.color)),
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = GlanceModifier.defaultWeight())

                // 감정 행
                Row(modifier = GlanceModifier.fillMaxWidth()) {
                    for (i in 0..6) {
                        val dateStr = weekStart.plusDays(i.toLong()).toString()
                        val mood = weekMoods[dateStr]
                        val hasMemo = weekMemos[dateStr]?.isNotBlank() == true
                        val hasTodo = (weekTodos[dateStr] ?: emptyList()).isNotEmpty()
                        val icons = buildString {
                            if (mood != null) append(mood)
                            if (hasMemo) append("📝")
                            if (hasTodo) append("📋")
                        }
                        Box(modifier = GlanceModifier.defaultWeight(), contentAlignment = Alignment.Center) {
                            if (icons.isNotBlank()) {
                                Text(icons, style = TextStyle(fontSize = 7.sp), maxLines = 1)
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
